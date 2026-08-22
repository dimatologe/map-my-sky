package net.astrometry;

/**
 * JNI-Bruecke zur nativen astrometry.net-Bibliothek (libastrometry.so, arm64-v8a).
 *
 * WICHTIG: Paket ("net.astrometry"), Klassenname ("JNI") und Methodenname ("solveField")
 * MUESSEN exakt so bleiben. Der native Symbolname in der .so ist
 * Java_net_astrometry_JNI_solveField und wird genau aus diesen Namen abgeleitet — jede
 * Aenderung fuehrt zu UnsatisfiedLinkError. Vorgegeben vom DiDacTex/astrometry-android-Fork.
 */
public final class JNI {

    private JNI() {
    }

    /**
     * Fuehrt einen Solve wie das solve-field-CLI aus.
     *
     * @param args    Kommandozeilen-Argumente (z. B. "--fits-image", "--wcs", Pfad, "--backend-config", ...).
     * @param results double[2]; erhaelt bei Erfolg [RA, Dec] in Grad (Bildmitte).
     * @return 0 bei Erfolg, sonst != 0. Die volle WCS-Loesung schreibt solve-field zusaetzlich
     *         in die per "--wcs <pfad>" angegebene Datei (die wir dann selbst parsen).
     */
    public static native int solveField(String[] args, double[] results);
}
