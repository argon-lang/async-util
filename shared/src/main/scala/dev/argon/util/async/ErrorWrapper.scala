package dev.argon.util.async

import zio.{FiberId, *}
import zio.stream.ZStream

import scala.compiletime.deferred
import scala.reflect.TypeTest

trait ErrorWrapper[E] {
  type EX <: Throwable

  def exceptionTypeTest: TypeTest[Any, EX]
  given TypeTest[Any, EX] = exceptionTypeTest

  def wrap(error: Cause[E]): EX
  def unwrap(ex: EX): Cause[E]
  
  private[ErrorWrapper] final def unwrapThrowable(ex: Throwable): Cause[E] =
    ex match {
      case ex: InterruptedException => Cause.interrupt(FiberId.None)
      case ex: EX => unwrap(ex)
      case _ => Cause.die(ex)
    }
}

object ErrorWrapper {
  type Aux[E, WEX <: Throwable] = ErrorWrapper[E] { type EX = WEX }
  
  def apply[E](using errorWrapper: ErrorWrapper[E]): ErrorWrapper[E] =
    errorWrapper

  private def wrappedCause[E](cause: Cause[E])(using errorWrapper: ErrorWrapper[E]): Cause[errorWrapper.EX] =
    if !cause.isFailure then
      cause.stripFailures
    else
      Cause.fail(errorWrapper.wrap(cause))

  def wrapEffect[R, E, A](a: ZIO[R, E, A])(using errorWrapper: ErrorWrapper[E]): ZIO[R, errorWrapper.EX, A] =
    a.mapErrorCause(wrappedCause)

  def wrapStream[R, E, A](a: ZStream[R, E, A])(using errorWrapper: ErrorWrapper[E]): ZStream[R, errorWrapper.EX, A] =
    a.mapErrorCause(wrappedCause)

  def unwrapEffect[R, E, A](a: ZIO[R, Throwable, A])(using errorWrapper: ErrorWrapper[E]): ZIO[R, E, A] =
    a.catchAll(ex => ZIO.failCause(errorWrapper.unwrapThrowable(ex)))

  def unwrapStream[R, E, A](a: ZStream[R, Throwable, A])(using errorWrapper: ErrorWrapper[E]): ZStream[R, E, A] =
    a.catchAll(ex => ZStream.failCause(errorWrapper.unwrapThrowable(ex)))

  abstract class WrappedErrorBase[E](val cause: Cause[E]) extends Exception

  def forWrappedError[E, EXImpl <: WrappedErrorBase[E]](create: Cause[E] => EXImpl)(using TypeTest[Any, EXImpl]): ErrorWrapper[E] =
    new ErrorWrapper[E] {
      override type EX = EXImpl | InterruptedException
      override lazy val exceptionTypeTest: TypeTest[Any, EX] = new TypeTest[Any, EX] {
        override def unapply(x: Any): Option[x.type & EX] =
          x.asInstanceOf[x.type & Matchable] match {
            case ex: EXImpl => Some(ex.asInstanceOf[x.type & EXImpl])
            case ex: (x.type & InterruptedException) => Some(ex)
            case _ => None
          }
      }

      override def wrap(error: Cause[E]): EX =
        if error.isInterruptedOnly then
          new InterruptedException()
        else
          create(error)

      override def unwrap(ex: EX): Cause[E] =
        ex match {
          case _: InterruptedException => Cause.interrupt(FiberId.None)
          case ex: EXImpl => ex.cause
        }
    }

  private final class WrappedCauseNothing(cause: Cause[Nothing]) extends WrappedErrorBase(cause)

  given ErrorWrapper[Nothing] = forWrappedError(WrappedCauseNothing(_))



  final class Context[E] {
    private final class ContextException(cause: Cause[E]) extends WrappedErrorBase[E](cause)
    given errorWrapper: ErrorWrapper[E] = forWrappedError(ContextException(_))
  }


}

