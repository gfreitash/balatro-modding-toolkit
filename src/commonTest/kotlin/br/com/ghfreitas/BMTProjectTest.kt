package br.com.ghfreitas

import br.com.ghfreitas.bmt.common.infrastructure.writeToFile
import br.com.ghfreitas.bmt.projectmanagement.application.repository.BMTProjectRepository
import br.com.ghfreitas.bmt.projectmanagement.domain.model.BMTProject
import br.com.ghfreitas.bmt.projectmanagement.domain.model.DiscoveredMod
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.*

class BMTProjectTest {

    private lateinit var fs: FakeFileSystem
    private lateinit var rootPath: okio.Path
    private lateinit var bmtFile: okio.Path
    private lateinit var repository: BMTProjectRepository

    @BeforeTest
    fun setup() {
        fs = FakeFileSystem()
        rootPath = "/project".toPath()
        fs.createDirectories(rootPath)
        fs.workingDirectory = rootPath
        bmtFile = rootPath / BMTProject.FILE_NAME
        repository = BMTProjectRepository(fs)
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

        repository.save(project)

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
        with(fs) { bmtFile.writeToFile(projectJson) }

        val project = repository.load()

        assertNotNull(project)
        assertEquals("/project", project.rootPath)
        assertEquals(1, project.discoveredMods.size)
        assertEquals("test.mod", project.discoveredMods[0].name)
        assertTrue(project.discoveredMods[0].included)
        assertEquals(1234567890L, project.lastScanMilliseconds)
    }

    @Test
    fun load_returns_null_for_invalid_json() {
        with(fs) { bmtFile.writeToFile("{ invalid json }") }
        val project = repository.load()

        assertNull(project)
    }

    @Test
    fun hasModAt_returns_true_when_mod_exists() {
        val project = BMTProject(
            rootPath = rootPath.toString(),
            discoveredMods = listOf(
                DiscoveredMod(
                    name = "test.mod",
                    manifestPath = "$rootPath/mods/test/manifest.json",
                    included = true
                )
            )
        )

        assertTrue(project.hasModAt("$rootPath/mods/test/manifest.json"))
    }

    @Test
    fun hasModAt_returns_false_when_mod_does_not_exist() {
        val project = BMTProject(
            rootPath = rootPath.toString(),
            discoveredMods = emptyList()
        )

        assertFalse(project.hasModAt("$rootPath/mods/test/manifest.json"))
    }

    @Test
    fun addDiscoveredMod_adds_new_mod() {
        val project = BMTProject(rootPath = rootPath.toString())
        val mod = DiscoveredMod(
            name = "test.mod",
            manifestPath = "$rootPath/mods/test/manifest.json",
            included = true
        )

        val updated = project.addDiscoveredMod(mod)

        assertEquals(1, updated.discoveredMods.size)
        assertEquals("test.mod", updated.discoveredMods[0].name)
    }

    @Test
    fun addDiscoveredMod_throws_on_duplicate() {
        val mod = DiscoveredMod(
            name = "test.mod",
            manifestPath = "$rootPath/mods/test/manifest.json",
            included = true
        )
        val project = BMTProject(
            rootPath = rootPath.toString(),
            discoveredMods = listOf(mod)
        )

        assertFailsWith<IllegalArgumentException> {
            project.addDiscoveredMod(mod)
        }
    }

    @Test
    fun markScanned_updates_timestamp() {
        val project = BMTProject(rootPath = rootPath.toString())
        val timestamp = 9876543210L

        val updated = project.markScanned(timestamp)

        assertEquals(timestamp, updated.lastScanMilliseconds)
    }

    @Test
    fun exists_returns_false_when_file_does_not_exist() {
        assertFalse(repository.exists()!!)
    }

    @Test
    fun exists_returns_true_when_valid_file_exists() {
        val project = BMTProject(rootPath = rootPath.toString())
        repository.save(project)

        assertTrue(repository.exists()!!)
    }

    @Test
    fun exists_returns_null_when_file_is_corrupted() {
        with(fs) { bmtFile.writeToFile("{ invalid json }") }

        assertNull(repository.exists())
    }
}
