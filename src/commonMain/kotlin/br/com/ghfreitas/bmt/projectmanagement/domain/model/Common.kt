package br.com.ghfreitas.bmt.projectmanagement.domain.model

import arrow.core.raise.context.RaiseAccumulate
import arrow.core.raise.context.ensureOrAccumulate
import br.com.ghfreitas.bmt.common.domain.valueobjects.Validatable
import br.com.ghfreitas.bmt.common.domain.valueobjects.ValidationError
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
value class ModAuthor(val value: String) : Validatable {
    context(_: RaiseAccumulate<ValidationError>)
    override fun constraints() {
        ensureOrAccumulate(value.isNotBlank()) { ValidationError("ModAuthor cannot be blank") }
    }
}


@JvmInline
value class FolderName(val value: String) : Validatable {
    companion object {
        private val FORBIDDEN_CHARS = setOf('<', '>', ':', '"', '/', '\\', '|', '?', '*')
    }

    context(_: RaiseAccumulate<ValidationError>)
    override fun constraints() {
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
