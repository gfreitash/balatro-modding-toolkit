@file:OptIn(ExperimentalContracts::class)

package br.com.ghfreitas.bmt.common.domain.valueobjects

import arrow.core.Either
import arrow.core.EitherNel
import arrow.core.raise.RaiseAccumulate
import arrow.core.raise.accumulate
import arrow.core.raise.either
import kotlin.contracts.ExperimentalContracts
import kotlin.jvm.JvmInline

/**
 * Shared Kernel - Validation Framework
 *
 * This module provides the foundation for domain validation using Arrow's functional error handling.
 * All value objects and entities in the domain layer use this framework to validate their business rules.
 */

@JvmInline
value class ValidationError(val message: String)

/**
 * Represents a contract for objects capable of validating their internal state.
 *
 * Implementations should provide a mechanism to ensure their integrity and correct configuration.
 * The validation logic should return instances of `ValidationError` wrapped in an `EitherNel`
 * if any issues are detected, or `Unit` if valid.
 */
interface Validatable {
    context(_: RaiseAccumulate<ValidationError>)
    fun constraints()
}


/**
 * This wraps `either { accumulate {} }` into a single lambda.
 */
inline fun <ValidationError, A> validating(
    block: RaiseAccumulate<ValidationError>.() -> A
): EitherNel<ValidationError, Unit> = either { accumulate(block) }

typealias Valid<B> = Either.Right<B>
typealias Invalid<A> = Either.Left<A>
