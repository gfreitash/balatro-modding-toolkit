package br.com.ghfreitas.bmt.projectmanagement.domain.model.balatromodindex

import arrow.core.raise.context.RaiseAccumulate
import arrow.core.raise.context.ensureOrAccumulate
import br.com.ghfreitas.bmt.common.domain.valueobjects.Validatable
import br.com.ghfreitas.bmt.common.domain.valueobjects.ValidationError
import br.com.ghfreitas.bmt.projectmanagement.domain.model.FolderName
import br.com.ghfreitas.bmt.projectmanagement.domain.model.ModAuthor
import kotlin.jvm.JvmInline


@JvmInline
value class ModTitle(val value: String) : Validatable {

    context(_: RaiseAccumulate<ValidationError>)
    override fun constraints() {
        ensureOrAccumulate(value.isNotBlank()) { ValidationError("ModTitle cannot be blank") }
    }
}

enum class ModCategory(val displayName: String) {
    CONTENT("Content"),
    JOKER("Joker"),
    QUALITY_OF_LIFE("Quality of Life"),
    TECHNICAL("Technical"),
    MISCELLANEOUS("Miscellaneous"),
    RESOURCE_PACKS("Resource Packs"),
    API("API");

    companion object {
        fun fromString(value: String): ModCategory? = entries.find {
            it.displayName.equals(value, ignoreCase = true)
        }
    }
}

@JvmInline
value class ModCategories(val values: Set<ModCategory>) : Validatable {
    context(_: RaiseAccumulate<ValidationError>)
    override fun constraints() {
        ensureOrAccumulate(values.isNotEmpty()) {
            ValidationError("Must have at least one category")
        }
    }
}

@JvmInline
value class RepoUrl(val value: String) : Validatable {
    context(_: RaiseAccumulate<ValidationError>)
    override fun constraints() {
        ensureOrAccumulate(value.isNotBlank()) { ValidationError("Repository URL cannot be blank") }
        ensureOrAccumulate(
            value.matches("""^https?://[\w.-]+(/.*)?$""".toRegex())
        ) { ValidationError("Repository URL must be a valid HTTP/HTTPS URL") }
    }
}

@JvmInline
value class DownloadUrl(val value: String) : Validatable {
    context(_: RaiseAccumulate<ValidationError>)
    override fun constraints() {
        ensureOrAccumulate(value.isNotBlank()) { ValidationError("Download URL cannot be blank") }
        ensureOrAccumulate(
            value.matches("""^https?://[\w.-]+(/.*)?$""".toRegex())
        ) { ValidationError("Download URL must be a valid HTTP/HTTPS URL") }
    }
}

@JvmInline
value class IndexModVersion(val value: String) : Validatable {
    context(_: RaiseAccumulate<ValidationError>)
    override fun constraints() {
        ensureOrAccumulate(value.isNotBlank()) { ValidationError("Version cannot be blank") }
    }
}

@JvmInline
value class LastUpdated(val value: ULong) : Validatable {
    context(_: RaiseAccumulate<ValidationError>)
    override fun constraints() {
    }
}

data class ModIndexMetadata(
    val title: ModTitle,
    val requiresSteamodded: Boolean,
    val requiresTalisman: Boolean,
    val categories: ModCategories,
    val author: ModAuthor,
    val repo: RepoUrl,
    val downloadURL: DownloadUrl,
    val version: IndexModVersion,
    val folderName: FolderName? = null,
    val automaticVersionCheck: Boolean? = null,
    val fixedReleaseTagUpdates: Boolean? = null,
    val lastUpdated: LastUpdated? = null
) : Validatable {

    context(_: RaiseAccumulate<ValidationError>)
    override fun constraints() {
        // Validate all required fields
        title.constraints()
        categories.constraints()
        author.constraints()
        repo.constraints()
        downloadURL.constraints()
        version.constraints()

        // Validate optional fields
        folderName?.constraints()
        lastUpdated?.constraints()

        // Schema rule: if fixedReleaseTagUpdates is true, then automaticVersionCheck must be true
        // and downloadURL must point to specific GitHub release asset
        if (fixedReleaseTagUpdates == true) {
            ensureOrAccumulate(automaticVersionCheck == true) {
                ValidationError("automaticVersionCheck must be true when fixedReleaseTagUpdates is true")
            }
            ensureOrAccumulate(
                downloadURL.value.matches("""^https?://github\.com/[^/]+/[^/]+/releases/download/[^/]+/.+$""".toRegex())
            ) {
                ValidationError("When fixedReleaseTagUpdates is true, downloadURL must point to a specific GitHub release asset")
            }
        }

        // Schema rule: prevent accidental freezing of updates
        // If automaticVersionCheck is true AND downloadURL points to a specific release asset,
        // then fixedReleaseTagUpdates must be true
        if (automaticVersionCheck == true &&
            downloadURL.value.matches("""^https?://github\.com/[^/]+/[^/]+/releases/download/[^/]+/.+$""".toRegex())
        ) {
            ensureOrAccumulate(fixedReleaseTagUpdates == true) {
                ValidationError(
                    "When downloadURL points to a specific GitHub release asset AND automaticVersionCheck is true, " +
                            "fixedReleaseTagUpdates must also be true to prevent accidental update freezing"
                )
            }
        }
    }
}
