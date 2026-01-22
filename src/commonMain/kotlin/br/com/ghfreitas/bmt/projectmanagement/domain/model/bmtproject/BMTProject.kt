package br.com.ghfreitas.bmt.projectmanagement.domain.model.bmtproject

import arrow.core.NonEmptyList
import arrow.core.raise.Raise
import arrow.core.raise.RaiseAccumulate
import arrow.core.raise.context.ensureOrAccumulate
import arrow.core.raise.context.raise
import br.com.ghfreitas.bmt.common.domain.valueobjects.Validatable
import br.com.ghfreitas.bmt.common.domain.valueobjects.ValidationError
import br.com.ghfreitas.bmt.common.domain.valueobjects.resolve
import br.com.ghfreitas.bmt.common.domain.valueobjects.validating
import br.com.ghfreitas.bmt.projectmanagement.domain.model.FolderName
import br.com.ghfreitas.bmt.projectmanagement.domain.model.steamodded.SteamoddedManifest
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.time.Clock

// Other things that are part of project
// - Dependencies libs such as love2d source, balatro source, smods source, smods wiki, the lib folder name itself
// - A common/shared code folder. Is it embedded? Is it deployed separately?
// - Changelog structure if any. Is the changelog the source of truth? Is git releases the source of truth?

/**
 * Domain aggregate representing a BMT (Balatro Modding Toolkit) project.
 *
 * This aggregate encapsulates the project configuration and discovered mods,
 * providing business rules for mod management.
 */
@Serializable
data class BMTProject(
    val rootPath: String,
    val discoveredMods: MutableSet<DiscoveredMod> = mutableSetOf(),
    var lastScannedAt: LastScannedAt? = null
) {
    companion object {
        const val FILE_NAME = ".bmt.json"
    }
}

/**
 * Value object representing a discovered mod within a BMT project.
 *
 * Part of the BMTProject aggregate, this captures the essential information
 * about a mod discovered during project scanning.
 */
@Serializable
class DiscoveredMod private constructor(
    val name: String,
    val path: List<String>,
    val metadata: SteamoddedManifest?,
    val hasLovelyPatches: Boolean,
) : Validatable {

    companion object {
        context(_: Raise<NonEmptyList<ValidationError>>)
        fun resolveModName(
            folderName: FolderName,
            metadata: SteamoddedManifest?
        ): String = validating { folderName.constraints() }.resolve(
            ifFailure = { raise(it)},
            ifSuccess = { metadata?.id?.value ?: folderName.value }
        )

        context(_: RaiseAccumulate<ValidationError>)
        fun create(
            name: String,
            path: List<String>,
            metadata: SteamoddedManifest?,
            hasLovelyPatches: Boolean
        ): DiscoveredMod = DiscoveredMod(
            name = name,
            path = path,
            metadata = metadata,
            hasLovelyPatches = hasLovelyPatches
        ).also { it.constraints() }
    }

    context(_: RaiseAccumulate<ValidationError>)
    override fun constraints() {
        ensureOrAccumulate(name.isNotBlank()) { ValidationError("Mod name cannot be blank") }
        ensureOrAccumulate(path.isNotEmpty()) { ValidationError("Mod folder path cannot be blank") }
        // First segment can be empty for absolute paths (e.g., ["", "project", "mod"] represents "/project/mod")
        ensureOrAccumulate(path.drop(1).all { it.isNotBlank() }) { ValidationError("Mod folder path segments cannot be blank") }

        ensureOrAccumulate(metadata != null || hasLovelyPatches) { ValidationError("Mod must be a Steamodded or Lovely mod") }
        ensureOrAccumulate(metadata != null || path.last() == name) { ValidationError("Lovely only mod name must match folder name") }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as DiscoveredMod

        return name == other.name
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }


}

@JvmInline
@Serializable
value class LastScannedAt(val value: Long) {
    companion object {
        fun now(): LastScannedAt = LastScannedAt(Clock.System.now().toEpochMilliseconds())
    }
}
