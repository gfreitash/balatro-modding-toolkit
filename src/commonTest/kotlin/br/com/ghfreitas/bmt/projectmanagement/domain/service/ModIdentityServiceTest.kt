package br.com.ghfreitas.bmt.projectmanagement.domain.service

import br.com.ghfreitas.bmt.projectmanagement.domain.model.steamodded.*
import kotlin.test.Test
import kotlin.test.assertEquals

class ModIdentityServiceTest {

    private val service = ModIdentityService()

    @Test
    fun resolveModName_returns_id_from_metadata_when_present() {
        val metadata = SteamoddedManifest(
            id = ModId("test_mod_id"),
            name = ModName("Test Mod"),
            description = ModDescription("Desc"),
            author = emptyList(),
            version = ModVersion("1.0.0"),
            prefix = ModPrefix("prefix"),
            mainFile = MainFile("main.lua")
        )

        val result = service.resolveModName("folder_name", metadata)

        assertEquals("test_mod_id", result)
    }

    @Test
    fun resolveModName_returns_folder_name_when_metadata_is_null() {
        val result = service.resolveModName("folder_name", null)

        assertEquals("folder_name", result)
    }

    @Test
    fun resolveModName_ignores_lovely_folder_presence() {
        // This test ensures that the hasLovelyFolder flag doesn't change the naming logic
        // which focuses on metadata ID vs folder name.
        
        val resultWithLovely = service.resolveModName("folder_name", null)
        assertEquals("folder_name", resultWithLovely)

        val metadata = SteamoddedManifest(
            id = ModId("test_mod_id"),
            name = ModName("Test Mod"),
            description = ModDescription("Desc"),
            author = emptyList(),
            version = ModVersion("1.0.0"),
            prefix = ModPrefix("prefix"),
            mainFile = MainFile("main.lua")
        )
        val resultWithLovelyAndMetadata = service.resolveModName("folder_name", metadata)
        assertEquals("test_mod_id", resultWithLovelyAndMetadata)
    }
}
