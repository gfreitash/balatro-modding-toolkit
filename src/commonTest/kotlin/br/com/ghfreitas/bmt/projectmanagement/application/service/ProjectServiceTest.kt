package br.com.ghfreitas.bmt.projectmanagement.application.service

import br.com.ghfreitas.bmt.projectmanagement.application.repository.BMTProjectRepository
import br.com.ghfreitas.bmt.projectmanagement.domain.model.BMTProject
import br.com.ghfreitas.bmt.projectmanagement.domain.service.ModIdentityService
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

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
        val modIdentityService = ModIdentityService()
        service = ProjectService(fs, repository, modDiscoveryService, modIdentityService)
    }

    @Test
    fun initialize_creates_new_project_with_cwd() {
        val status = service.initialize()

        assertEquals("/work", status.rootPath)
        assertEquals(0, status.modCount)
        
        val loaded = repository.load()
        assertNotNull(loaded)
        assertEquals("/work", loaded.rootPath)
    }

        @Test

        fun ensureRootPathSet_updates_empty_root_path() {

            // ... (previous test code)

        }

    

        @Test

        fun discoverNewMods_finds_mod_at_root() {

            // Setup: project root is a mod (has manifest.json)

            val project = BMTProject(rootPath = "/work")

            repository.save(project)

            

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

    

            val newMods = service.discoverNewMods()

    

            assertEquals(1, newMods.size)

            assertEquals("root_mod", newMods[0].name)

            assertEquals("/work", newMods[0].path)

        }

    }

    