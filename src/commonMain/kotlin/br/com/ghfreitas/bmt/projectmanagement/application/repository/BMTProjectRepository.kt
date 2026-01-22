package br.com.ghfreitas.bmt.projectmanagement.application.repository

import arrow.core.raise.catch
import arrow.core.raise.context.Raise
import arrow.core.raise.context.ensure
import arrow.core.raise.context.raise
import br.com.ghfreitas.bmt.common.infrastructure.PrettyJson
import br.com.ghfreitas.bmt.common.infrastructure.atomicWrite
import br.com.ghfreitas.bmt.common.infrastructure.readAsString
import br.com.ghfreitas.bmt.projectmanagement.application.error.ProjectError
import br.com.ghfreitas.bmt.projectmanagement.domain.model.bmtproject.BMTProject
import kotlinx.serialization.SerializationException
import okio.FileSystem
import okio.IOException
import okio.Path.Companion.toPath

/**
 * Repository for persisting and loading BMTProject aggregates.
 *
 * This is an application layer output adapter that handles the serialization
 * and file I/O for BMTProject instances.
 *
 * Uses Arrow's Raise DSL for typed error handling, distinguishing between
 * "not found" and "corrupted" states rather than returning nullable types.
 */
class BMTProjectRepository(private val fileSystem: FileSystem) {

    /**
     * Loads the BMTProject from the project file.
     *
     * @return The loaded BMTProject
     * @raises ProjectError.NotFound if the file doesn't exist
     * @raises ProjectError.Corrupted if the file exists but cannot be parsed
     */
    context(_: Raise<ProjectError>)
    fun load(): BMTProject = with(fileSystem) {
        val path = BMTProject.FILE_NAME.toPath()
        ensure(exists(path)) { ProjectError.NotFound }

        catch({
            PrettyJson.decodeFromString<BMTProject>(path.readAsString())
        }) { e: Throwable ->
            when (e) {
                is SerializationException, is IllegalArgumentException ->
                    raise(ProjectError.Corrupted)

                is IOException ->
                    raise(ProjectError.IoError(e.message ?: "Unknown IO error"))

                else -> throw e
            }
        }
    }


    /**
     * Saves the BMTProject to the project file.
     *
     * @param project The project to save
     * @raises ProjectError.IoError if the file cannot be written
     */
    context(_: Raise<ProjectError>)
    fun save(project: BMTProject) = with(fileSystem) {
        catch({
            val content = PrettyJson.encodeToString(project)
            BMTProject.FILE_NAME.toPath().atomicWrite(content)
        }) { e ->
            when (e) {
                is IOException ->
                    raise(ProjectError.IoError(e.message ?: "Unknown IO error"))
                else -> throw e
            }
        }
    }
}
