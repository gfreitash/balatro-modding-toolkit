package br.com.ghfreitas.bmt.projectmanagement.application.service

import arrow.core.raise.either
import br.com.ghfreitas.bmt.common.domain.valueobjects.Success
import br.com.ghfreitas.bmt.projectmanagement.application.repository.BMTProjectRepository
import br.com.ghfreitas.bmt.projectmanagement.domain.model.bmtproject.BMTProject
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProjectServiceTest {

    private lateinit var fs: FakeFileSystem
    private lateinit var repository: BMTProjectRepository
    private lateinit var service: ProjectService

    @BeforeTest
    fun setup() {
        fs = FakeFileSystem()
        // Simulate a working directory
        fs.createDirectories("/work".toPath())
        fs.workingDirectory = "/work".toPath()

        repository = BMTProjectRepository(fs)
        val modDiscoveryService = ModDiscoveryService(fs)
        service = ProjectService(fs, repository, modDiscoveryService)
    }

    @Test
    fun initialize_creates_new_project_with_cwd() {
        val result = either { service.initialize() }

        assertTrue(result is Success)
        val status = result.value
        assertEquals("/work", status.rootPath)
        assertEquals(0, status.modCount)

        val loadResult = either { repository.load() }
        assertTrue(loadResult is Success)
        assertEquals("/work", loadResult.value.rootPath)
    }

    @Test
    fun ensureRootPathSet_updates_empty_root_path() {
        // First initialize a project
        either { service.initialize() }

        val result = either { service.ensureRootPathSet() }

        assertTrue(result is Success)
        assertEquals("/work", result.value.rootPath)
    }

    @Test
    fun discoverNewMods_finds_mod_at_root() = runTest {
        // Setup: project root is a mod (has manifest.json)
        val project = BMTProject(rootPath = "/work")
        either { repository.save(project) }

        // Create manifest in root
        val manifestPath = "/work/manifest.json".toPath()
        fs.createDirectories(manifestPath.parent!!)
        fs.write(manifestPath) {
            writeUtf8("""
                {
                    "id": "root_mod",
                    "name": "Root Mod",
                    "author": ["Alice"],
                    "version": "1.0.0",
                    "description": "A mod at root",
                    "prefix": "RM",
                    "main_file": "main.lua"
                }
            """.trimIndent())
        }

        val foundMods = mutableListOf<Pair<String, String>>()
        val result = either {
            service.discoverNewMods { modName, path ->
                foundMods.add(modName to path)
                true // Accept all mods
            }
        }

        assertTrue(result is Success)

        // Verify via callback
        assertEquals(1, foundMods.size)
        assertEquals("root_mod", foundMods[0].first)

        // Verify mods are persisted in repository
        val loadResult = either { repository.load() }
        assertTrue(loadResult is Success)
        val loadedProject = loadResult.value
        assertEquals(1, loadedProject.discoveredMods.size)
        assertEquals("root_mod", loadedProject.discoveredMods.first().name)
    }
}
