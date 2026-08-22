package com.codex.starmapper.processing

import com.codex.starmapper.R
import com.codex.starmapper.domain.DeepSkyObject

/**
 * Bekannte Deep-Sky-Kataloge. Der Schlüssel wird aus dem Präfix der Bezeichnung
 * (`desig`/`id`, z.B. "NGC 1977", "Sh2-155") abgeleitet. So lassen sich Objekte in
 * der Beschriftung katalogweise an-/abwählen.
 */
enum class DeepSkyCatalogGroup(val key: String, val labelResId: Int) {
    Messier("M", R.string.dso_catalog_messier),
    Ngc("NGC", R.string.dso_catalog_ngc),
    Ic("IC", R.string.dso_catalog_ic),
    Caldwell("C", R.string.dso_catalog_caldwell),
    Sharpless("SH2", R.string.dso_catalog_sharpless),
    Barnard("B", R.string.dso_catalog_barnard),
    Collinder("CR", R.string.dso_catalog_collinder),
    Melotte("MEL", R.string.dso_catalog_melotte),
    VanDenBergh("VDB", R.string.dso_catalog_vandenbergh),
    Lbn("LBN", R.string.dso_catalog_lbn),
    Ldn("LDN", R.string.dso_catalog_ldn),
    Cederblad("CED", R.string.dso_catalog_cederblad),
    Planetary("PN", R.string.dso_catalog_planetary),
    Abell("ACO", R.string.dso_catalog_abell),
    Pgc("PGC", R.string.dso_catalog_pgc),
    Other("OTHER", R.string.dso_catalog_other),
    ;

    companion object {
        /**
         * Leitet die Katalogzugehörigkeit aus einer Bezeichnung ab. Vergleich case-insensitiv,
         * längere/spezifischere Präfixe zuerst (VdBH vor B, Sh2 vor S).
         */
        fun fromDesignation(designation: String): DeepSkyCatalogGroup {
            val normalized = designation.trim().uppercase()
            return when {
                normalized.startsWith("M ") || normalized.matches(Regex("^M\\d.*")) -> Messier
                normalized.startsWith("NGC") -> Ngc
                normalized.startsWith("IC") -> Ic
                normalized.startsWith("SH") -> Sharpless
                normalized.startsWith("VDBH") -> VanDenBergh
                normalized.startsWith("VDB") -> VanDenBergh
                normalized.startsWith("LBN") -> Lbn
                normalized.startsWith("LDN") -> Ldn
                normalized.startsWith("CED") -> Cederblad
                normalized.startsWith("CR") -> Collinder
                normalized.startsWith("MEL") -> Melotte
                normalized.startsWith("ACO") -> Abell
                normalized.startsWith("PGC") -> Pgc
                normalized.startsWith("PN") -> Planetary
                normalized.startsWith("PK") -> Planetary
                // Caldwell "C 9" – nur als eigenständiges Token, nicht "CR"/"CED" (oben abgefangen)
                normalized.startsWith("C ") || normalized.matches(Regex("^C\\d.*")) -> Caldwell
                normalized.startsWith("B ") || normalized.matches(Regex("^B\\d.*")) -> Barnard
                else -> Other
            }
        }

        fun of(deepSky: DeepSkyObject): DeepSkyCatalogGroup {
            val fromName = fromDesignation(deepSky.name)
            // `name` kann ein Eigenname sein (z.B. "LMC"); dann auf die id zurückfallen.
            return if (fromName != Other) fromName else fromDesignation(deepSky.id)
        }
    }
}
