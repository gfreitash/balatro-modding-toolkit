package br.com.ghfreitas.bmt.common.domain.valueobjects

import arrow.core.Either
import arrow.core.Either.Left
import arrow.core.Either.Right
import arrow.core.EitherNel
import arrow.core.raise.Raise
import arrow.core.raise.RaiseAccumulate
import arrow.core.raise.accumulate
import arrow.core.raise.either
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
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
): EitherNel<ValidationError, A> = either { accumulate(block) }

/**
 * Type alias for Either.Right.
 * It's a semantic alternative to represent a successful validation result
 */
typealias Valid<B> = Right<B>
/**
 * Type alias for Either.Left.
 * It's a semantic alternative to represent a failed validation result
 */
typealias Invalid<A> = Left<A>

/**
 * Type alias for Either.Right representing a success case.
 * Mirrors the [Valid] pattern but for general results.
 */
typealias Success<B> = Right<B>
/**
 * Type alias for Either.Left representing a failure case.
 * Mirrors the [Invalid] pattern but for general results.
 */
typealias Failure<A> = Left<A>

/**
 * A typealias for the Either type, representing a computation that may result in a value of type B
 * (typically indicating success) or a value of type A (typically indicating failure).
 *
 * This alias is useful for improving the readability of code where Either is used to describe
 * operations that can succeed or fail with distinct types for success and error cases.
 *
 * @param A The type representing the failure or error case.
 * @param B The type representing the success case.
 */
typealias Outcome<A, B> = Either<A, B>

/**
 * Runs a computation [block] using [Raise], and returns its outcome as [Outcome].
 * - [Success] represents success,
 * - [Failure] represents logical failure.
 *
 * This function is a wrapper around [either]
 */
inline fun <Error, A> attempt(
    @BuilderInference block: Raise<Error>.() -> A
): Outcome<Error, A> = either(block)

/**
 * Determines whether the current instance represents a failure case.
 * Smart-casts to the [Outcome] to the appropriate subtype.
 *
 * @return true if the instance is of type [Failure], indicating a failure state; false if the instance is of type [Success], indicating a success state.
 */
fun <A, B> Outcome<A, B>.isFailure(): Boolean {
    contract {
        returns(true) implies (this@isFailure is Left)
        returns(false) implies (this@isFailure is Right)
    }
    return this@isFailure is Left<A>
}

/**
 * Determines whether the current instance represents a success case.
 * Smart-casts to the [Outcome] to the appropriate subtype.
 *
 * @return true if the instance is of type [Success], indicating a success state; false if the instance is of type [Failure], indicating a failure state.
 */
@Suppress("UNUSED_PARAMETER")
fun <A, B> Outcome<A, B>.isSuccess(): Boolean {
    contract {
        returns(true) implies (this@isSuccess is Right)
        returns(false) implies (this@isSuccess is Left)
    }
    return this@isSuccess is Right<B>
}

inline fun <A, B, C> Outcome<A, B>.resolve(ifFailure: (failure: A) -> C, ifSuccess: (success: B) -> C): C {
    contract {
        callsInPlace(ifFailure, InvocationKind.AT_MOST_ONCE)
        callsInPlace(ifSuccess, InvocationKind.AT_MOST_ONCE)
    }
    return when (this) {
        is Success -> ifSuccess(value)
        is Failure -> ifFailure(value)
    }
}
