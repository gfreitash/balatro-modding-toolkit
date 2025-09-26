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

value class ValidationError(val message: String)

interface Validatable {
    fun validate(): EitherNel<ValidationError, Unit>
}

fun EitherNel<ValidationError, Unit>.aggregateErrors(): Either<ValidationError, Unit> =
    this.recover { errors ->
        raise(ValidationError(errors.map { it.message }.joinToString("\n")))
    }


/**
 * This wraps either { accumulate {} } into a single lambda.
 */
inline fun <ValidationError, A> validation(
    block: RaiseAccumulate<ValidationError>.() -> A
): EitherNel<ValidationError, Unit> = either { accumulate(block) }


/**
 * This is equivalent to EitherNel.bindNelOrAccumulate()
 */
context(raiseAcc: RaiseAccumulate<Error>)
fun <Error, A> EitherNel<Error, A>.gather(): Value<A> =
    raiseAcc.accumulating { this@gather.bindNel() }
