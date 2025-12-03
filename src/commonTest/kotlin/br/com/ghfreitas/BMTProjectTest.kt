package br.com.ghfreitas

import BMTProject
import DiscoveredMod
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.*

class BMTProjectTest {

    private lateinit var fs: FakeFileSystem
    private lateinit var rootPath: okio.Path
    private lateinit var bmtFile: okio.Path

    @BeforeTest
    fun setup() {
        fs = FakeFileSystem()
        rootPath = "/project".toPath()
        fs.createDirectories(rootPath)
        fs.workingDirectory = rootPath
        bmtFile = rootPath / BMTProject.FILE
    }

    @Test
    fun save_creates_bmt_json_file() {
        val project = BMTProject(
            rootPath = rootPath.toString(),
            discoveredMods = listOf(
                DiscoveredMod(
                    name = "test.mod",
                    manifestPath = "$rootPath/mods/test/manifest.json",
                    included = true
                )
            ),
            lastScanMilliseconds = 1234567890L
        )

        with(fs) { project.save() }

        assertTrue(fs.exists(bmtFile))
        val content = fs.read(bmtFile) { readUtf8() }
        assertTrue(content.contains("\"rootPath\": \"$rootPath\""))
        assertTrue(content.contains("\"test.mod\""))
        assertTrue(content.contains("\"included\": true"))
    }

    @Test
    fun load_parses_valid_bmt_json() {
        val projectJson = """
        {
          "rootPath" : "$rootPath",
          "discoveredMods" : [ {
            "name" : "test.mod",
            "manifestPath" : "$rootPath/mods/test/manifest.json",
            "included" : true,
            "discoveredAt" : 1234567890
          } ],
          "lastScanMilliseconds" : 1234567890
        }
        """.trimIndent()
        fs.write(bmtFile) { writeUtf8(projectJson) }

        val project = with(fs) { BMTProject.load() }

        assertNotNull(project)
        assertEquals("/project", project.rootPath)
        assertEquals(1, project.discoveredMods.size)
        assertEquals("test.mod", project.discoveredMods[0].name)
        assertTrue(project.discoveredMods[0].included)
        assertEquals(1234567890L, project.lastScanMilliseconds)
    }

    @Test
    fun load_returns_null_for_invalid_json() {
        fs.write(bmtFile) { writeUtf8("{ invalid json }") }
        val project = with(fs) { BMTProject.load() }

        assertNull(project)
    }
}
