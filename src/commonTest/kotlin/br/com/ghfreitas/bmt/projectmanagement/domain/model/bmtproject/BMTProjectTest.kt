package br.com.ghfreitas.bmt.projectmanagement.domain.model.bmtproject

import arrow.core.raise.either
import br.com.ghfreitas.bmt.common.domain.valueobjects.Failure
import br.com.ghfreitas.bmt.common.domain.valueobjects.Success
import br.com.ghfreitas.bmt.common.domain.valueobjects.validating
import br.com.ghfreitas.bmt.common.infrastructure.writeToFile
import br.com.ghfreitas.bmt.projectmanagement.application.error.ProjectError
import br.com.ghfreitas.bmt.projectmanagement.application.repository.BMTProjectRepository
import br.com.ghfreitas.bmt.projectmanagement.domain.model.ModAuthor
import br.com.ghfreitas.bmt.projectmanagement.domain.model.steamodded.*
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
        val metadata = SteamoddedManifest(
            id = ModId("test_mod"),
            name = ModName("Test Mod"),
            author = listOf(ModAuthor("Author")),
            description = ModDescription("A test mod"),
            prefix = ModPrefix("TM"),
            mainFile = MainFile("main.lua"),
            version = ModVersion("1.0.0")
        )

        val discoveredMod = validating {
            DiscoveredMod.create(
                name = "test_mod",
                path = listOf("mods", "test_folder"),
                metadata = metadata,
                hasLovelyPatches = false
            )
        }
        assertTrue(discoveredMod is Success)

        val project = BMTProject(
            rootPath = rootPath.toString(),
            discoveredMods = mutableSetOf(discoveredMod.value),
            lastScannedAt = LastScannedAt(1234567890L)
        )

        val result = either { repository.save(project) }

        assertTrue(result is Success)
        assertTrue(fs.exists(bmtFile))
        val content = fs.read(bmtFile) { readUtf8() }
        assertTrue(content.contains("\"rootPath\": \"$rootPath\""))
        assertTrue(content.contains("\"test_mod\""))
    }

    @Test
    fun load_parses_valid_bmt_json() {
        val projectJson = """
        {
          "rootPath" : "$rootPath",
          "discoveredMods" : [ {
            "name" : "test_mod",
            "path" : [ "mods", "test_folder" ],
            "metadata" : {
              "id" : "test_mod",
              "name" : "Test Mod",
              "author" : [ "Author" ],
              "description" : "A test mod",
              "prefix" : "TM",
              "main_file" : "main.lua",
              "version" : "1.0.0"
            },
            "hasLovelyPatches" : false
          } ],
          "lastScannedAt" : 1234567890
        }
        """.trimIndent()
        with(fs) { bmtFile.writeToFile(projectJson) }

        val result = either { repository.load() }

        assertTrue(result is Success)
        val project = result.value
        assertEquals("/project", project.rootPath)
        assertEquals(1, project.discoveredMods.size)
        assertEquals("test_mod", project.discoveredMods.first().name)
        assertEquals(listOf("mods", "test_folder"), project.discoveredMods.first().path)
        assertEquals(1234567890L, project.lastScannedAt?.value)
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
}
