package com.codex.starmapper.processing

import com.codex.starmapper.domain.DeepSkyObject
import com.codex.starmapper.domain.SkyPoint
import org.junit.Assert.assertEquals
import org.junit.Test

class DeepSkyCatalogsTest {

    private fun dso(id: String, name: String = id) = DeepSkyObject(
        id = id,
        name = name,
        type = "bn",
        point = SkyPoint(0f, 0f),
        magnitude = null,
        dimensions = "",
    )

    @Test
    fun designationPrefixesMapToCatalogs() {
        assertEquals(DeepSkyCatalogGroup.Ngc, DeepSkyCatalogGroup.fromDesignation("NGC 1977"))
        assertEquals(DeepSkyCatalogGroup.Ic, DeepSkyCatalogGroup.fromDesignation("IC 1805"))
        assertEquals(DeepSkyCatalogGroup.Messier, DeepSkyCatalogGroup.fromDesignation("M 31"))
        assertEquals(DeepSkyCatalogGroup.Messier, DeepSkyCatalogGroup.fromDesignation("M45"))
        assertEquals(DeepSkyCatalogGroup.Sharpless, DeepSkyCatalogGroup.fromDesignation("Sh2-155"))
        assertEquals(DeepSkyCatalogGroup.VanDenBergh, DeepSkyCatalogGroup.fromDesignation("VdB 118"))
        assertEquals(DeepSkyCatalogGroup.VanDenBergh, DeepSkyCatalogGroup.fromDesignation("VdBH 24"))
        assertEquals(DeepSkyCatalogGroup.Lbn, DeepSkyCatalogGroup.fromDesignation("LBN 800"))
        assertEquals(DeepSkyCatalogGroup.Ldn, DeepSkyCatalogGroup.fromDesignation("LDN 1607"))
        assertEquals(DeepSkyCatalogGroup.Pgc, DeepSkyCatalogGroup.fromDesignation("PGC 17223"))
        assertEquals(DeepSkyCatalogGroup.Collinder, DeepSkyCatalogGroup.fromDesignation("Cr 39"))
        assertEquals(DeepSkyCatalogGroup.Cederblad, DeepSkyCatalogGroup.fromDesignation("Ced 214"))
        assertEquals(DeepSkyCatalogGroup.Caldwell, DeepSkyCatalogGroup.fromDesignation("C 9"))
        assertEquals(DeepSkyCatalogGroup.Barnard, DeepSkyCatalogGroup.fromDesignation("B 33"))
        assertEquals(DeepSkyCatalogGroup.Other, DeepSkyCatalogGroup.fromDesignation("Arp 220"))
    }

    @Test
    fun caldwellAndCollinderAndCederbladDoNotCollide() {
        // C/Cr/Ced teilen den Anfangsbuchstaben - dürfen sich nicht überschneiden.
        assertEquals(DeepSkyCatalogGroup.Cederblad, DeepSkyCatalogGroup.fromDesignation("CED 99"))
        assertEquals(DeepSkyCatalogGroup.Collinder, DeepSkyCatalogGroup.fromDesignation("CR 70"))
        assertEquals(DeepSkyCatalogGroup.Caldwell, DeepSkyCatalogGroup.fromDesignation("C 41"))
    }

    @Test
    fun fallsBackToIdWhenNameIsProperName() {
        // "LMC" als Name, aber PGC-id -> PGC.
        assertEquals(DeepSkyCatalogGroup.Pgc, DeepSkyCatalogGroup.of(dso(id = "PGC 17223", name = "LMC")))
    }
}
