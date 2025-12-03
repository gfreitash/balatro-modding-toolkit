package br.com.ghfreitas.dto

import arrow.core.EitherNel
import arrow.core.right
import br.com.ghfreitas.dto.validation.Validatable
import br.com.ghfreitas.dto.validation.ValidationError
import br.com.ghfreitas.dto.validation.gather
import br.com.ghfreitas.dto.validation.validation
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
value class ModId(val value: String) : Validatable {
    companion object {
        private val DISALLOWED_IDS = setOf("Steamodded", "Lovely", "Balatro")
    }

    override fun validate(): EitherNel<ValidationError, Unit> = validation {
        ensureOrAccumulate(value.isNotBlank()) { ValidationError("ModId cannot be blank") }
        ensureOrAccumulate(value !in DISALLOWED_IDS) {
            ValidationError("ModId '$value' is not allowed. Reserved IDs: ${DISALLOWED_IDS.joinToString(", ")}")
        }
        ensureOrAccumulate(value.matches("""^[a-zA-Z0-9_-]+$""".toRegex())) {
            ValidationError("ModId must contain only letters, numbers, _ or -")
        }
    }
}

@Serializable
@JvmInline
value class ModName(val value: String) : Validatable {
    override fun validate(): EitherNel<ValidationError, Unit> = validation {
        ensureOrAccumulate(value.isNotBlank()) { ValidationError("ModName cannot be blank") }
    }
}

@Serializable
@JvmInline
value class ModAuthor(val value: String) : Validatable {
    override fun validate(): EitherNel<ValidationError, Unit> = validation {
        ensureOrAccumulate(value.isNotBlank()) { ValidationError("ModAuthor cannot be blank") }
    }
}

@Serializable
@JvmInline
value class ModDescription(val value: String) : Validatable {
    override fun validate(): EitherNel<ValidationError, Unit> = validation {
        ensureOrAccumulate(value.isNotBlank()) { ValidationError("ModDescription cannot be blank") }
    }
}

@Serializable
@JvmInline
value class ModPrefix(val value: String) : Validatable {
    override fun validate(): EitherNel<ValidationError, Unit> = validation {
        ensureOrAccumulate(value.isNotBlank()) { ValidationError("ModPrefix cannot be blank") }
        ensureOrAccumulate(value.matches("""^[a-zA-Z0-9_]+$""".toRegex())) {
            ValidationError("ModPrefix must contain only letters, numbers or _")
        }
    }
}

@Serializable
@SerialName("main_file")
@JvmInline
value class MainFile(
    val value: String
) : Validatable {
    override fun validate(): EitherNel<ValidationError, Unit> = validation {
        ensureOrAccumulate(value.isNotBlank()) { ValidationError("MainFile cannot be blank") }
        ensureOrAccumulate(value.endsWith(".lua")) { ValidationError("MainFile must have .lua extension") }
    }
}

@Serializable
@SerialName("config_file")
@JvmInline
value class ConfigFile(
    val value: String
) : Validatable {
    override fun validate(): EitherNel<ValidationError, Unit> = validation {
        ensureOrAccumulate(value.isNotBlank()) { ValidationError("ConfigFile cannot be blank") }
        ensureOrAccumulate(value.endsWith(".lua")) { ValidationError("ConfigFile must have .lua extension") }
    }
}

@Serializable
@JvmInline
value class Priority(val value: Int) : Validatable {
    override fun validate(): EitherNel<ValidationError, Unit> = Unit.right()
}

@Serializable
@JvmInline
value class HexColor(val value: String) : Validatable {
    override fun validate(): EitherNel<ValidationError, Unit> = validation {
        ensureOrAccumulate(value.matches("""^[0-9A-Fa-f]{6}$|^[0-9A-Fa-f]{8}$""".toRegex())) {
            ValidationError("HexColor must be a valid hexadecimal color code (6 or 8 digits)")
        }
    }
}

@Serializable
@SerialName("display_name")
@JvmInline
value class DisplayName(val value: String) : Validatable {
    override fun validate(): EitherNel<ValidationError, Unit> = validation {
        ensureOrAccumulate(value.isNotBlank()) { ValidationError("DisplayName cannot be blank") }
        ensureOrAccumulate(value.length <= 10) { ValidationError("DisplayName must be at most 10 characters") }
    }
}

@Serializable
@JvmInline
value class ModVersion(val value: String) : Validatable {
    override fun validate(): EitherNel<ValidationError, Unit> = validation {
        ensureOrAccumulate(value.isNotBlank()) { ValidationError("ModVersion cannot be blank") }
        // Based on the Lua is_valid function, the version format is more flexible
        // Supports: major.minor.patch with optional rev (including ~ for beta)
        // Supports wildcards (*) and various revision formats
        ensureOrAccumulate(
            value.matches("""^(\d+)(\.?([*\d]*))?(\.?([*\d]*))?(.*)$""".toRegex())
        ) { ValidationError("ModVersion must follow the format major[.minor][.patch][rev] (e.g., '1.0.0', '1.2.*', '1.0.0~beta')") }

        // Additional check for trailing dots (as warned in Lua code)
        ensureOrAccumulate(
            !value.matches(""".*\.$""".toRegex())
        ) { ValidationError("ModVersion cannot end with a trailing dot") }
    }
}

@Serializable
@JvmInline
value class DependencySpec(val value: String) : Validatable {
    override fun validate(): EitherNel<ValidationError, Unit> = validation {
        ensureOrAccumulate(value.isNotBlank()) { ValidationError("DependencySpec cannot be blank") }
        // More permissive validation since Lua does complex parsing
        // Supports: "ModName", "ModName (operator version)", "ModA | ModB | ModC"
        // Operators: <<, >>, <=, >=, ==
        ensureOrAccumulate(
            value.matches("""^[a-zA-Z0-9_-]+(\s*\([^)]*\))?\s*(\|\s*[a-zA-Z0-9_-]+(\s*\([^)]*\))?)*$""".toRegex())
        ) { ValidationError("DependencySpec must follow valid format: 'ModName [(operator version)]' with optional alternatives separated by |") }
    }
}

@Serializable
@JvmInline
value class ConflictSpec(val value: String) : Validatable {
    override fun validate(): EitherNel<ValidationError, Unit> = validation {
        ensureOrAccumulate(value.isNotBlank()) { ValidationError("ConflictSpec cannot be blank") }
        ensureOrAccumulate(!value.contains("|")) { ValidationError("ConflictSpec cannot contain alternatives (|)") }
        // More permissive to match Lua parsing
        ensureOrAccumulate(
            value.matches("""^[a-zA-Z0-9_-]+(\s*\([^)]*\))?$""".toRegex())
        ) { ValidationError("ConflictSpec must follow format: 'ModName [(operator version)]'") }
    }
}

@Serializable
@JvmInline
value class ProvideSpec(val value: String) : Validatable {
    override fun validate(): EitherNel<ValidationError, Unit> = validation {
        ensureOrAccumulate(value.isNotBlank()) { ValidationError("ProvideSpec cannot be blank") }
        // More permissive to match Lua parsing
        ensureOrAccumulate(
            value.matches("""^[a-zA-Z0-9_-]+(\s*\([^)]*\))?$""".toRegex())
        ) { ValidationError("ProvideSpec must follow format: 'ModName [(version)]'") }
    }
}

@Serializable
@JsonIgnoreUnknownKeys
data class BalatroModMetadata(
    val id: ModId,
    val name: ModName,
    val author: List<ModAuthor>,
    val description: ModDescription,
    val prefix: ModPrefix,
    @SerialName("main_file") val mainFile: MainFile,
    val version: ModVersion,
    val priority: Priority = Priority(0),
    @SerialName("badge_colour") val badgeColour: HexColor = HexColor("666665FF"),
    @SerialName("badge_text_colour") val badgeTextColour: HexColor = HexColor("FFFFFFFF"),
    @SerialName("display_name") val displayName: DisplayName? = null,
    @SerialName("config_file") val configFile: ConfigFile = ConfigFile("config.lua"),
    val dependencies: List<DependencySpec> = emptyList(),
    val conflicts: List<ConflictSpec> = emptyList(),
    val provides: List<ProvideSpec> = emptyList(),
    @SerialName("dump_loc") val dumpLoc: Boolean = false
) : Validatable {

    override fun validate(): EitherNel<ValidationError, Unit> = validation {
        // Validate all required fields
        id.validate().gather()
        name.validate().gather()
        description.validate().gather()
        prefix.validate().gather()
        mainFile.validate().gather()
        configFile.validate().gather()
        priority.validate().gather()
        badgeColour.validate().gather()
        badgeTextColour.validate().gather()

        // Validate author list
        ensureOrAccumulate(author.isNotEmpty()) { ValidationError("Must have at least one author") }
        author.forEach { it.validate().gather() }

        // Validate optional fields
        displayName?.validate()?.gather()
        version.validate().gather()

        // Validate lists
        dependencies.forEach { it.validate().gather() }
        conflicts.forEach { it.validate().gather() }
        provides.forEach { it.validate().gather() }
    }
}
