package br.com.ghfreitas.bmt.projectmanagement.application.repository

import br.com.ghfreitas.bmt.common.infrastructure.PrettyJson
import br.com.ghfreitas.bmt.common.infrastructure.readAsString
import br.com.ghfreitas.bmt.common.infrastructure.writeToFile
import br.com.ghfreitas.bmt.projectmanagement.domain.model.BMTProject
import okio.FileSystem
import okio.Path.Companion.toPath

/**
 * Repository for persisting and loading BMTProject aggregates.
 *
 * This is an application layer output adapter that handles the serialization
 * and file I/O for BMTProject instances.
 */
class BMTProjectRepository(private val fileSystem: FileSystem) {

    /**
     * Loads the BMTProject from the project file.
     *
     * @return The loaded BMTProject, or null if the file doesn't exist or is invalid
     */
    fun load(): BMTProject? = with(fileSystem) {
        runCatching {
            PrettyJson.decodeFromString<BMTProject>(
                BMTProject.FILE_NAME.toPath().readAsString()
            )
        }.getOrNull()
    }

    /**
     * Saves the BMTProject to the project file.
     *
     * @param project The project to save
     */
    fun save(project: BMTProject) = with(fileSystem) {
        BMTProject.FILE_NAME.toPath().writeToFile(
            PrettyJson.encodeToString(project)
        )
    }

    /**
     * Checks if a valid BMTProject file exists.
     *
     * @return true if file exists and is valid, false if file doesn't exist, null if file exists but is corrupted
     */
    fun exists(): Boolean? {
        val path = BMTProject.FILE_NAME.toPath()
        if (!fileSystem.exists(path)) return false
        load() ?: return null  // file exists but corrupted
        return true
    }
}
