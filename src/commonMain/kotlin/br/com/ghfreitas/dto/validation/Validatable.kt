@file:OptIn(ExperimentalContracts::class)

package br.com.ghfreitas.dto.validation

import arrow.core.Either
import arrow.core.EitherNel
import arrow.core.raise.RaiseAccumulate
import arrow.core.raise.RaiseAccumulate.Value
import arrow.core.raise.accumulate
import arrow.core.raise.either
import arrow.core.recover
import kotlin.contracts.ExperimentalContracts
import kotlin.jvm.JvmInline

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
    fun validate(): EitherNel<ValidationError, Unit>
}

/**
 * Aggregates multiple `ValidationError` objects into a single `ValidationError` containing
 * a concatenated message of their error descriptions. If there are no errors, the original
 * successful unit result is returned.
 *
 * @return An `Either` containing a single `ValidationError` with aggregated messages in
 * case of errors, or the original success value of type `Unit`.
 */
fun EitherNel<ValidationError, Unit>.aggregateErrors(): Either<ValidationError, Unit> =
    this.recover { errors ->
        raise(ValidationError(errors.map { it.message }.joinToString("\n")))
    }


/**
 * This wraps `either { accumulate {} }` into a single lambda.
 */
inline fun <ValidationError, A> validation(
    block: RaiseAccumulate<ValidationError>.() -> A
): EitherNel<ValidationError, Unit> = either { accumulate(block) }


/**
 * This is equivalent to `EitherNel.bindNelOrAccumulate()`
 * but with less verbosity
 */
context(raiseAcc: RaiseAccumulate<Error>)
fun <Error, A> EitherNel<Error, A>.gather(): Value<A> =
    raiseAcc.accumulating { this@gather.bindNel() }
