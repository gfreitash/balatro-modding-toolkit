package br.com.ghfreitas.bmt.projectmanagement.domain.model.bmtproject

import arrow.core.raise.either
import br.com.ghfreitas.bmt.common.domain.valueobjects.Failure
import br.com.ghfreitas.bmt.common.domain.valueobjects.Success
import br.com.ghfreitas.bmt.common.infrastructure.writeToFile
import br.com.ghfreitas.bmt.projectmanagement.application.error.ProjectError
import br.com.ghfreitas.bmt.projectmanagement.application.repository.BMTProjectRepository
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

        val result = either { repository.save(project) }

        assertTrue(result is Success)
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

        val result = either { repository.load() }

        assertTrue(result is Success)
        val project = result.value
        assertEquals("/project", project.rootPath)
        assertEquals(1, project.discoveredMods.size)
        assertEquals("test.mod", project.discoveredMods[0].name)
        assertTrue(project.discoveredMods[0].included)
        assertEquals(1234567890L, project.lastScanMilliseconds)
    }

    @Test
    fun load_returns_corrupted_error_for_invalid_json() {
        with(fs) { bmtFile.writeToFile("{ invalid json }") }

        val result = either { repository.load() }

        assertTrue(result is Failure)
        assertEquals(ProjectError.Corrupted, result.value)
    }

    @Test
    fun load_returns_not_found_error_when_file_missing() {
        val result = either { repository.load() }

        assertTrue(result is Failure)
        assertEquals(ProjectError.NotFound, result.value)
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

        val result = either { project.addDiscoveredMod(mod) }

        assertTrue(result is Success)
        val updated = result.value
        assertEquals(1, updated.discoveredMods.size)
        assertEquals("test.mod", updated.discoveredMods[0].name)
    }

    @Test
    fun addDiscoveredMod_returns_error_on_duplicate() {
        val mod = DiscoveredMod(
            name = "test.mod",
            manifestPath = "$rootPath/mods/test/manifest.json",
            included = true
        )
        val project = BMTProject(
            rootPath = rootPath.toString(),
            discoveredMods = listOf(mod)
        )

        val result = either { project.addDiscoveredMod(mod) }

        assertTrue(result is Failure)
        assertTrue(result.value is ProjectError.ModAlreadyExists)
        assertEquals("$rootPath/mods/test/manifest.json", (result.value as ProjectError.ModAlreadyExists).path)
    }

    @Test
    fun markScanned_updates_timestamp() {
        val project = BMTProject(rootPath = rootPath.toString())
        val timestamp = 9876543210L

        val updated = project.markScanned(timestamp)

        assertEquals(timestamp, updated.lastScanMilliseconds)
    }
}
