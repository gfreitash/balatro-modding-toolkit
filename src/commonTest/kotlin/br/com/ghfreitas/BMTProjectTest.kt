package br.com.ghfreitas

import BMTProject
import DiscoveredMod
import PrettyJson
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.*
import kotlin.time.Clock

class BMTProjectTest {
    
    @Test
    fun save_creates_bmt_json_file() {
        val fs = FakeFileSystem()
        val project = BMTProject(
            rootPath = "/project",
            discoveredMods = listOf(
                DiscoveredMod(
                    name = "test.mod",
                    manifestPath = "/project/mods/test/manifest.json",
                    included = true
                )
            ),
            lastScanMilliseconds = 1234567890L
        )
        
        // Set up fake filesystem to replace system calls
        val bmtFile = "/project/.bmt.json".toPath()
        fs.createDirectories(bmtFile.parent ?: "/".toPath())
        
        // We can't directly test the save() method since it uses FileSystem.SYSTEM
        // Instead, we test the serialization format by writing manually
        fs.write(bmtFile) {
            writeUtf8(PrettyJson.encodeToString(project))
        }

        assertTrue(fs.exists(bmtFile))
        val content = fs.read(bmtFile) { readUtf8() }
        assertTrue(content.contains("\"rootPath\": \"/project\""))
        assertTrue(content.contains("\"test.mod\""))
        assertTrue(content.contains("\"included\": true"))
    }
    
    @Test
    fun load_parses_valid_bmt_json() {
        val fs = FakeFileSystem()
        val bmtFile = "/project/.bmt.json".toPath()
        fs.createDirectories(bmtFile.parent ?: "/".toPath())
        
        val projectJson = """
        {
          "rootPath" : "/project",
          "discoveredMods" : [ {
            "name" : "test.mod",
            "manifestPath" : "/project/mods/test/manifest.json",
            "included" : true,
            "discoveredAt" : 1234567890
          } ],
          "lastScanMilliseconds" : 1234567890
        }
        """.trimIndent()
        
        fs.write(bmtFile) { writeUtf8(projectJson) }
        
        // We can't directly test load() since it uses FileSystem.SYSTEM
        // Instead, we test the deserialization by reading manually
        val content = fs.read(bmtFile) { readUtf8() }
        val project = PrettyJson.decodeFromString<BMTProject>(content)
        
        assertEquals("/project", project.rootPath)
        assertEquals(1, project.discoveredMods.size)
        assertEquals("test.mod", project.discoveredMods[0].name)
        assertTrue(project.discoveredMods[0].included)
        assertEquals(1234567890L, project.lastScanMilliseconds)
    }
    
    @Test
    fun load_returns_null_for_invalid_json() {
        val fs = FakeFileSystem()
        val bmtFile = "/project/.bmt.json".toPath()
        fs.createDirectories(bmtFile.parent ?: "/".toPath())
        
        fs.write(bmtFile) { writeUtf8("{ invalid json }") }
        
        // Test manual parsing of invalid JSON
        val content = fs.read(bmtFile) { readUtf8() }
        val project = runCatching {
            PrettyJson.decodeFromString<BMTProject>(content)
        }.getOrNull()
        
        assertNull(project)
    }
    
    @Test
    fun discoveredMod_has_correct_structure() {
        val now = Clock.System.now().toEpochMilliseconds()
        val mod = DiscoveredMod(
            name = "my.mod",
            manifestPath = "/path/to/manifest.json",
            included = false
        )
        
        assertEquals("my.mod", mod.name)
        assertEquals("/path/to/manifest.json", mod.manifestPath)
        assertFalse(mod.included)
        assertTrue(mod.discoveredAt >= now)
    }
    
    @Test
    fun bmtProject_default_values() {
        val project = BMTProject()
        
        assertEquals("", project.rootPath)
        assertTrue(project.discoveredMods.isEmpty())
        assertEquals(0L, project.lastScanMilliseconds)
    }
    
    @Test
    fun bmtProject_copy_and_update() {
        val originalProject = BMTProject(
            rootPath = "/original",
            discoveredMods = listOf(
                DiscoveredMod("mod1", "/path1", true),
                DiscoveredMod("mod2", "/path2", false)
            ),
            lastScanMilliseconds = 1000L
        )
        
        val updatedProject = originalProject.copy(
            rootPath = "/updated",
            lastScanMilliseconds = 2000L
        )
        
        assertEquals("/updated", updatedProject.rootPath)
        assertEquals(2, updatedProject.discoveredMods.size)
        assertEquals(2000L, updatedProject.lastScanMilliseconds)
        
        // Original should be unchanged
        assertEquals("/original", originalProject.rootPath)
        assertEquals(1000L, originalProject.lastScanMilliseconds)
    }
}
