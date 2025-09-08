package br.com.ghfreitas.dto

import arrow.core.EitherNel
import br.com.ghfreitas.dto.validation.Validatable
import br.com.ghfreitas.dto.validation.ValidationError
import br.com.ghfreitas.dto.validation.gather
import br.com.ghfreitas.dto.validation.validation

value class ModTitle(val value: String) : Validatable {
    override fun validate(): EitherNel<ValidationError, Unit> = validation {
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

value class ModCategories(val values: Set<ModCategory>) : Validatable {
    override fun validate(): EitherNel<ValidationError, Unit> = validation {
        ensureOrAccumulate(values.isNotEmpty()) {
            ValidationError("Must have at least one category")
        }
    }
}

value class RepoUrl(val value: String) : Validatable {
    override fun validate(): EitherNel<ValidationError, Unit> = validation {
        ensureOrAccumulate(value.isNotBlank()) { ValidationError("Repository URL cannot be blank") }
        ensureOrAccumulate(
            value.matches("""^https?://[\w.-]+(/.*)?$""".toRegex())
        ) { ValidationError("Repository URL must be a valid HTTP/HTTPS URL") }
    }
}

value class DownloadUrl(val value: String) : Validatable {
    override fun validate(): EitherNel<ValidationError, Unit> = validation {
        ensureOrAccumulate(value.isNotBlank()) { ValidationError("Download URL cannot be blank") }
        ensureOrAccumulate(
            value.matches("""^https?://[\w.-]+(/.*)?$""".toRegex())
        ) { ValidationError("Download URL must be a valid HTTP/HTTPS URL") }
    }
}

value class FolderName(val value: String) : Validatable {
    companion object {
        private val FORBIDDEN_CHARS = setOf('<', '>', ':', '"', '/', '\\', '|', '?', '*')
    }

    override fun validate(): EitherNel<ValidationError, Unit> = validation {
        ensureOrAccumulate(value.isNotBlank()) { ValidationError("FolderName cannot be blank") }
        ensureOrAccumulate(value.length <= 100) { ValidationError("FolderName must be at most 100 characters") }
        ensureOrAccumulate(
            !value.any { it in FORBIDDEN_CHARS }
        ) {
            ValidationError("FolderName cannot contain characters: ${FORBIDDEN_CHARS.joinToString(" ")}")
        }
        ensureOrAccumulate(value.trim() == value) {
            ValidationError("FolderName cannot start or end with whitespace")
        }
    }
}

value class IndexModVersion(val value: String) : Validatable {
    override fun validate(): EitherNel<ValidationError, Unit> = validation {
        ensureOrAccumulate(value.isNotBlank()) { ValidationError("Version cannot be blank") }
    }
}

value class LastUpdated(val value: ULong) : Validatable {
    override fun validate(): EitherNel<ValidationError, Unit> = validation {}
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

    override fun validate(): EitherNel<ValidationError, Unit> = validation {
        // Validate all required fields
        title.validate().gather()
        categories.validate().gather()
        author.validate().gather()
        repo.validate().gather()
        downloadURL.validate().gather()
        version.validate().gather()

        // Validate optional fields
        folderName?.validate()?.gather()
        lastUpdated?.validate()?.gather()

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
