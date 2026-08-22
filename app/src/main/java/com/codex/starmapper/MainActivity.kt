package com.codex.starmapper

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.app.NotificationManagerCompat
import com.codex.starmapper.diagnostics.AppDiagnostics
import com.codex.starmapper.solve.SolveForegroundService
import com.codex.starmapper.ui.AppLocale
import com.codex.starmapper.ui.StarMapperApp
import com.codex.starmapper.ui.theme.StarMapperTheme

// AppCompatActivity (nicht ComponentActivity): noetig, damit AppCompatDelegate.setApplicationLocales
// (AppLocale.kt) greift -- Voraussetzung fuer die kommende strings.xml-Migration. Theme.SternbildMapper
// erbt seit der AppCompat-Migration von Theme.MaterialComponents.DayNight.NoActionBar (styles.xml),
// nicht mehr von der reinen Plattform-Theme -- beide Aenderungen gehoeren zusammen (ein fruehrer
// Versuch, nur die Activity umzustellen, stuerzte mit "You need to use a Theme.AppCompat theme" ab).
// StarMapperTheme (Compose) ist reiner MaterialTheme-Wrapper ohne eigene Fensterbehandlung, komplett
// unabhaengig von dieser Basisklasse.
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppDiagnostics.install(applicationContext)
        // Warum sind zuletzt Prozesse gestorben? (nativer Crash / Speicher / Signal) -> in den Export,
        // damit die native Absturz-Ursache ohne Logcat sichtbar wird.
        AppDiagnostics.recordExitReasons(applicationContext)
        AppDiagnostics.record("main_activity_created")
        // Gespeicherte Sprachwahl (System/Deutsch/English) vor dem ersten Compose laden.
        AppLocale.load(applicationContext)
        enableEdgeToEdge()
        setContent {
            // Arabisch (RTL) spiegelt seit der AppCompat-Migration automatisch das gesamte Layout
            // (Android/Compose folgen der App-Sprache) -- ohne arabische UI-Texte (noch ausstehend,
            // s. i18n-Plan) wuerde das nur Menues/Symbolleisten verwirrend spiegeln, waehrend der Text
            // selbst weiter englisch bliebe. Bewusst auf LTR erzwungen, bis die richtige RTL-Runde
            // zusammen mit den arabischen Texten kommt. Arabischer TEXT selbst (z.B. Sternbildnamen)
            // wird davon nicht beeinflusst -- das regelt Compose ueber die Zeichenfolge selbst (Bidi).
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                StarMapperTheme {
                    StarMapperApp()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        AppDiagnostics.record("main_activity_started")
        // Faellt die "Solve fertig"-Notification noch aus einem vorherigen Lauf herum (Nutzer hat die
        // App ueber das App-Icon statt ueber die Notification selbst geoeffnet, dann greift deren
        // eigenes setAutoCancel nicht): hier statt dort raeumen, bei JEDEM Sichtbarwerden der App.
        NotificationManagerCompat.from(this).cancel(SolveForegroundService.COMPLETION_NOTIFICATION_ID)
    }

    override fun onStop() {
        AppDiagnostics.record("main_activity_stopped")
        super.onStop()
    }
}
