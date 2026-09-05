package com.codex.starmapper.processing

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * EGL/FBO-"Smoke Test" für den geplanten GPU-Sphere-Space-Renderpfad (Nutzer-Vorgabe 2026-08-30,
 * Priorität 2 der Umsetzungsreihenfolge: "Erst wenn das auf dem Gerät stabil funktioniert, Glyph-Atlas/
 * Text ergänzen"). Hat NICHTS mit dem eigentlichen Spherical360-Rendering zu tun -- rein die Frage
 * "funktioniert Offscreen-EGL/FBO-Rendering + Shader-Compile/Link + Pixel-Rücklesen auf DIESEM Gerät
 * überhaupt zuverlässig?", BEVOR irgendein Sphere-/Glyph-Geometrie-Code entsteht. Bewusst als
 * eigenständige, komplett unabhängige Datei gehalten -- wird NIRGENDS vom bestehenden
 * `SphericalOverlayRenderer`/`ExportRenderer` aufgerufen, beeinflusst also den funktionierenden
 * Canvas-Pfad in keiner Weise.
 *
 * GLES-Version wird zur LAUFZEIT ermittelt (Nutzer-Korrektur 2026-08-30: "minSdk=26 garantiert nicht
 * GLES 3.0") -- versucht zuerst GLES 3, fällt bei Fehlschlag auf GLES 2 zurück, meldet in jedem Fall,
 * welche Version tatsächlich zustande kam (oder ob beide scheiterten). Rendert eine DETERMINISTISCHE
 * Testszene (4 farbige Ecken + Shader-Interpolation dazwischen -- prüft den vollen Compile/Link/Draw/
 * Readback-Pfad, nicht nur einen einfarbigen Clear) in einen kleinen Offscreen-Framebuffer und liest
 * bekannte Pixel zurück. Ein Fehlschlag an JEDER Stelle (Display/Config/Context/Surface/FBO/Shader) wird
 * abgefangen und als [Result] zurückgegeben -- NIE als Absturz, s. [run].
 */
object GpuSmokeTest {

    data class Result(
        val success: Boolean,
        val glesVersionRequested: Int = 0,
        val glVersion: String? = null,
        val glVendor: String? = null,
        val glRenderer: String? = null,
        val extensionCount: Int = 0,
        val seamlessCubemapExtensionPresent: Boolean = false,
        // Name -> ARGB (Int, wie android.graphics.Color) -- s. Klassenkommentar "deterministische Szene".
        val samplePixels: Map<String, Int> = emptyMap(),
        val failureReason: String? = null,
    )

    private const val TEST_SIZE = 64

    /** Rein synchron (kein `suspend`) -- der Aufrufer ist dafür verantwortlich, dies auf einem
     *  Hintergrund-Dispatcher laufen zu lassen (dasselbe Muster wie die bestehenden Export-Aufrufe,
     *  s. StarMapperApp.kt `withContext(Dispatchers.Default) { ... }`), da EGL/GLES-Aufrufe blockieren
     *  können. Der komplette EGL-Lebenszyklus (Display->Config->Context->Surface->...->Terminate) läuft
     *  in EINEM synchronen Durchlauf auf EINEM Thread -- kein Hin- und Herspringen zwischen Coroutinen. */
    fun run(): Result = runCatching { runInternal() }.getOrElse {
        Result(success = false, failureReason = "${it.javaClass.simpleName}: ${it.message}")
    }

    private fun runInternal(): Result {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == EGL14.EGL_NO_DISPLAY) return Result(false, failureReason = "eglGetDisplay: no display")
        val versionBuf = IntArray(2)
        if (!EGL14.eglInitialize(display, versionBuf, 0, versionBuf, 1)) {
            return Result(false, failureReason = "eglInitialize failed")
        }
        try {
            var lastFailure: String? = null
            for (requestedVersion in intArrayOf(3, 2)) {
                val outcome = tryVersion(display, requestedVersion)
                if (outcome.success) return outcome
                lastFailure = outcome.failureReason
            }
            return Result(false, failureReason = "GLES3 and GLES2 both failed; last reason: $lastFailure")
        } finally {
            EGL14.eglTerminate(display)
        }
    }

    private fun tryVersion(display: EGLDisplay, glesVersion: Int): Result {
        val renderableType = if (glesVersion == 3) EGLExt.EGL_OPENGL_ES3_BIT_KHR else EGL14.EGL_OPENGL_ES2_BIT
        val configAttribs = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, renderableType,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        val chose = EGL14.eglChooseConfig(display, configAttribs, 0, configs, 0, configs.size, numConfigs, 0)
        if (!chose || numConfigs[0] <= 0) {
            return Result(false, glesVersion, failureReason = "eglChooseConfig: no matching config for GLES$glesVersion")
        }
        val config = configs[0] ?: return Result(false, glesVersion, failureReason = "eglChooseConfig: null config")

        val contextAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, glesVersion, EGL14.EGL_NONE)
        val context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
        if (context == EGL14.EGL_NO_CONTEXT) {
            return Result(false, glesVersion, failureReason = "eglCreateContext failed for GLES$glesVersion")
        }

        val pbufferAttribs = intArrayOf(EGL14.EGL_WIDTH, TEST_SIZE, EGL14.EGL_HEIGHT, TEST_SIZE, EGL14.EGL_NONE)
        val surface = EGL14.eglCreatePbufferSurface(display, config, pbufferAttribs, 0)
        if (surface == EGL14.EGL_NO_SURFACE) {
            EGL14.eglDestroyContext(display, context)
            return Result(false, glesVersion, failureReason = "eglCreatePbufferSurface failed for GLES$glesVersion")
        }

        try {
            if (!EGL14.eglMakeCurrent(display, surface, surface, context)) {
                return Result(false, glesVersion, failureReason = "eglMakeCurrent failed for GLES$glesVersion")
            }
            return renderAndReadback(glesVersion)
        } finally {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(display, surface)
            EGL14.eglDestroyContext(display, context)
        }
    }

    private fun renderAndReadback(glesVersion: Int): Result {
        val glVersion = GLES20.glGetString(GLES20.GL_VERSION)
        val vendor = GLES20.glGetString(GLES20.GL_VENDOR)
        val renderer = GLES20.glGetString(GLES20.GL_RENDERER)
        val extensions = GLES20.glGetString(GLES20.GL_EXTENSIONS) ?: ""
        val extensionList = extensions.split(" ").filter { it.isNotBlank() }
        // GLES 3.0 macht nahtloses Cubemap-Filtern PFLICHT (kein Extension-Name nötig); auf GLES 2 kann
        // es nur über eine Erweiterung existieren, falls überhaupt (selten) -- rein informativ fürs
        // spätere Sphere-Design, beeinflusst dieses Smoke-Test-Ergebnis selbst nicht.
        val seamless = glesVersion >= 3 || extensionList.any { it.contains("seamless_cube", ignoreCase = true) }

        val fboIds = IntArray(1)
        GLES20.glGenFramebuffers(1, fboIds, 0)
        val texIds = IntArray(1)
        GLES20.glGenTextures(1, texIds, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texIds[0])
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, TEST_SIZE, TEST_SIZE, 0,
            GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null,
        )
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboIds[0])
        GLES20.glFramebufferTexture2D(
            GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, texIds[0], 0,
        )
        val fboStatus = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
        if (fboStatus != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            GLES20.glDeleteTextures(1, texIds, 0)
            GLES20.glDeleteFramebuffers(1, fboIds, 0)
            return Result(
                false, glesVersion, glVersion, vendor, renderer, extensionList.size, seamless,
                failureReason = "FBO incomplete: 0x${fboStatus.toString(16)}",
            )
        }

        GLES20.glViewport(0, 0, TEST_SIZE, TEST_SIZE)

        val program = buildProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        if (program == 0) {
            GLES20.glDeleteTextures(1, texIds, 0)
            GLES20.glDeleteFramebuffers(1, fboIds, 0)
            return Result(
                false, glesVersion, glVersion, vendor, renderer, extensionList.size, seamless,
                failureReason = "shader program compile/link failed",
            )
        }
        GLES20.glUseProgram(program)

        // Vollflächiges Quad (2 Dreiecke als Strip), 4 farbige Ecken -- die Fragment-Interpolation
        // dazwischen prüft die Rasterizer-Pipeline, nicht nur einen einfarbigen Clear. GL-Ursprung ist
        // unten-links: (-1,-1)=Rot, (1,-1)=Grün, (-1,1)=Blau, (1,1)=Gelb.
        val verts = floatArrayOf(
            -1f, -1f, 1f, 0f, 0f,
            1f, -1f, 0f, 1f, 0f,
            -1f, 1f, 0f, 0f, 1f,
            1f, 1f, 1f, 1f, 0f,
        )
        val vertBuffer = ByteBuffer.allocateDirect(verts.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(verts)
            position(0)
        }
        val posLoc = GLES20.glGetAttribLocation(program, "aPos")
        val colorLoc = GLES20.glGetAttribLocation(program, "aColor")
        val strideBytes = 5 * 4
        vertBuffer.position(0)
        GLES20.glVertexAttribPointer(posLoc, 2, GLES20.GL_FLOAT, false, strideBytes, vertBuffer)
        GLES20.glEnableVertexAttribArray(posLoc)
        vertBuffer.position(2)
        GLES20.glVertexAttribPointer(colorLoc, 3, GLES20.GL_FLOAT, false, strideBytes, vertBuffer)
        GLES20.glEnableVertexAttribArray(colorLoc)

        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glFinish()

        val pixelBuffer = ByteBuffer.allocateDirect(TEST_SIZE * TEST_SIZE * 4).order(ByteOrder.nativeOrder())
        GLES20.glReadPixels(0, 0, TEST_SIZE, TEST_SIZE, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixelBuffer)

        fun sampleArgb(px: Int, py: Int): Int {
            val idx = (py * TEST_SIZE + px) * 4
            val r = pixelBuffer.get(idx).toInt() and 0xFF
            val g = pixelBuffer.get(idx + 1).toInt() and 0xFF
            val b = pixelBuffer.get(idx + 2).toInt() and 0xFF
            val a = pixelBuffer.get(idx + 3).toInt() and 0xFF
            return (a shl 24) or (r shl 16) or (g shl 8) or b
        }
        val margin = 4
        val samples = linkedMapOf(
            "bottomLeft" to sampleArgb(margin, margin),
            "bottomRight" to sampleArgb(TEST_SIZE - 1 - margin, margin),
            "topLeft" to sampleArgb(margin, TEST_SIZE - 1 - margin),
            "topRight" to sampleArgb(TEST_SIZE - 1 - margin, TEST_SIZE - 1 - margin),
            "center" to sampleArgb(TEST_SIZE / 2, TEST_SIZE / 2),
        )

        GLES20.glDeleteProgram(program)
        GLES20.glDeleteTextures(1, texIds, 0)
        GLES20.glDeleteFramebuffers(1, fboIds, 0)

        return Result(true, glesVersion, glVersion, vendor, renderer, extensionList.size, seamless, samples)
    }

    private fun buildProgram(vertexSrc: String, fragmentSrc: String): Int {
        val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexSrc)
        if (vertexShader == 0) return 0
        val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSrc)
        if (fragmentShader == 0) {
            GLES20.glDeleteShader(vertexShader)
            return 0
        }
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(fragmentShader)
        if (linkStatus[0] == 0) {
            GLES20.glDeleteProgram(program)
            return 0
        }
        return program
    }

    private fun compileShader(type: Int, src: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, src)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            GLES20.glDeleteShader(shader)
            return 0
        }
        return shader
    }

    private const val VERTEX_SHADER = """
        attribute vec2 aPos;
        attribute vec3 aColor;
        varying vec3 vColor;
        void main() {
            vColor = aColor;
            gl_Position = vec4(aPos, 0.0, 1.0);
        }
    """

    private const val FRAGMENT_SHADER = """
        precision mediump float;
        varying vec3 vColor;
        void main() {
            gl_FragColor = vec4(vColor, 1.0);
        }
    """
}
