package br.com.ghfreitas.bmt.projectmanagement.application.service

import br.com.ghfreitas.bmt.common.infrastructure.cwd
import br.com.ghfreitas.bmt.projectmanagement.application.model.DiscoveredModDTO
import br.com.ghfreitas.bmt.projectmanagement.application.model.ModRegistrationDecision
import br.com.ghfreitas.bmt.projectmanagement.application.model.ProjectStatusDTO
import br.com.ghfreitas.bmt.projectmanagement.application.repository.BMTProjectRepository
import br.com.ghfreitas.bmt.projectmanagement.domain.model.BMTProject
import br.com.ghfreitas.bmt.projectmanagement.domain.model.DiscoveredMod
import br.com.ghfreitas.bmt.projectmanagement.domain.service.ModIdentityService
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
        return repository.exists()
    }

    /**
     * Initializes a new BMT project in the current working directory.
     *
     * @return The status of the newly created project
     */
    fun initialize(): ProjectStatusDTO = with(fileSystem) {
        val project = BMTProject(rootPath = FileSystem.cwd().toString())
        repository.save(project)
        project.toStatusDTO()
    }

    /**
     * Checks if the project is already initialized and returns its status.
     */
    fun getProjectStatus(): ProjectStatusDTO? {
        return repository.load()?.toStatusDTO()
    }

    /**
     * Ensures the project has a valid root path set and returns an updated status.
     */
    fun ensureRootPathSet(): ProjectStatusDTO? = with(fileSystem) {
        val project = repository.load() ?: return null
        if (project.rootPath.isEmpty()) {
            val updated = project.copy(rootPath = FileSystem.cwd().toString())
            repository.save(updated)
            updated.toStatusDTO()
        } else project.toStatusDTO()
    }

    /**
     * Discovers mods that are not yet registered in the project.
     */
    fun discoverNewMods(
        respectGitignore: Boolean = true,
        additionalIgnores: List<String> = emptyList()
    ): List<DiscoveredModDTO> {
        val project = repository.load() ?: return emptyList()
        
        val discoveredManifests = modDiscoveryService.discoverMods(
            rootPath = project.rootPath.toPath(),
            respectGitignore = respectGitignore,
            additionalIgnores = additionalIgnores
        )

        return discoveredManifests
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
     */
    fun registerDiscoveredMods(decisions: List<ModRegistrationDecision>) {
        var project = repository.load() ?: return
        
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