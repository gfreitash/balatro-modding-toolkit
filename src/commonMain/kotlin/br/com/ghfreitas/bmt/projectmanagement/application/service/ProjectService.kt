package br.com.ghfreitas.bmt.projectmanagement.application.service

import arrow.core.raise.context.Raise
import arrow.core.raise.either
import br.com.ghfreitas.bmt.common.domain.valueobjects.Failure
import br.com.ghfreitas.bmt.common.domain.valueobjects.Success
import br.com.ghfreitas.bmt.common.infrastructure.cwd
import br.com.ghfreitas.bmt.projectmanagement.application.error.ProjectError
import br.com.ghfreitas.bmt.projectmanagement.application.model.DiscoveredModDTO
import br.com.ghfreitas.bmt.projectmanagement.application.model.ModRegistrationDecision
import br.com.ghfreitas.bmt.projectmanagement.application.model.ProjectStatusDTO
import br.com.ghfreitas.bmt.projectmanagement.application.repository.BMTProjectRepository
import br.com.ghfreitas.bmt.projectmanagement.domain.model.bmtproject.BMTProject
import br.com.ghfreitas.bmt.projectmanagement.domain.model.bmtproject.DiscoveredMod
import br.com.ghfreitas.bmt.projectmanagement.domain.service.ModIdentityService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.map
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.time.Clock

/**
 * Application service for project-level operations.
 *
 * Orchestrates business processes related to project management,
 * coordinating between discovery, identity, and persistence layers.
 */
class ProjectService(
    private val fileSystem: FileSystem,
    private val repository: BMTProjectRepository,
    private val modDiscoveryService: ModDiscoveryService,
    private val modIdentityService: ModIdentityService
) {

    /**
     * Checks if the project is initialized.
     * @return true if initialized, false if not, null if corrupted.
     */
    fun checkProjectStatus(): Boolean? {
        return when (val result = either { repository.load() }) {
            is Success -> true
            is Failure -> when (result.value) {
                is ProjectError.NotFound -> false
                is ProjectError.Corrupted -> null
                else -> null
            }
        }
    }

    /**
     * Initializes a new BMT project in the current working directory.
     *
     * @return The status of the newly created project
     * @raises ProjectError.IoError if the project file cannot be written
     */
    context(_: Raise<ProjectError>)
    fun initialize(): ProjectStatusDTO = with(fileSystem) {
        val project = BMTProject(rootPath = FileSystem.cwd().toString())
        repository.save(project)
        project.toStatusDTO()
    }

    /**
     * Ensures the project has a valid root path set and returns an updated status.
     *
     * @raises ProjectError.NotFound if the project file doesn't exist
     * @raises ProjectError.Corrupted if the project file is corrupted
     * @raises ProjectError.IoError if the project file cannot be saved
     */
    context(_: Raise<ProjectError>)
    fun ensureRootPathSet(): ProjectStatusDTO = with(fileSystem) {
        val project = repository.load()
        if (project.rootPath.isEmpty()) {
            val updated = project.copy(rootPath = FileSystem.cwd().toString())
            repository.save(updated)
            updated.toStatusDTO()
        } else {
            project.toStatusDTO()
        }
    }

    /**
     * Discovers mods that are not yet registered in the project.
     *
     * @raises ProjectError.NotFound if the project file doesn't exist
     * @raises ProjectError.Corrupted if the project file is corrupted
     */
    context(_: Raise<ProjectError>)
    suspend fun discoverNewMods(
        respectGitignore: Boolean = true,
        additionalIgnores: List<String> = emptyList()
    ): Flow<DiscoveredModDTO> {
        val project = repository.load()

        val discoveredMods = modDiscoveryService.discoverMods(
            rootPath = project.rootPath.toPath(),
            respectGitignore = respectGitignore,
            additionalIgnores = additionalIgnores
        )

        return discoveredMods
            .filterNot { manifest -> project.hasModAt(manifest.path.toString()) }
            .map { manifest ->
                val modName = modIdentityService.resolveModName(
                    parentFolderName = manifest.path.name,
                    metadata = manifest.metadata
                )
                DiscoveredModDTO(
                    name = modName,
                    path = manifest.path.toString(),
                    hasManifest = manifest.metadata != null,
                    hasLovelyPatches = manifest.hasLovelyPatches
                )
            }
    }

    /**
     * Registers the chosen mods into the project.
     *
     * @raises ProjectError.NotFound if the project file doesn't exist
     * @raises ProjectError.Corrupted if the project file is corrupted
     * @raises ProjectError.ModAlreadyExists if a mod already exists at a given path
     * @raises ProjectError.IoError if the project file cannot be saved
     */
    context(_: Raise<ProjectError>)
    fun registerDiscoveredMods(decisions: List<ModRegistrationDecision>) {
        var project = repository.load()

        decisions.forEach { decision ->
            val discoveredMod = DiscoveredMod(
                name = decision.name,
                manifestPath = decision.path,
                included = decision.included
            )
            project = project.addDiscoveredMod(discoveredMod)
        }

        repository.save(project.markScanned(Clock.System.now().toEpochMilliseconds()))
    }

    private fun BMTProject.toStatusDTO() = ProjectStatusDTO(
        rootPath = rootPath,
        modCount = discoveredMods.size,
        lastScanTimestamp = lastScanMilliseconds
    )
}
