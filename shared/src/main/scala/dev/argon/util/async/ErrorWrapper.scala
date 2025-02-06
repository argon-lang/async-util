package dev.argon.util.async

import zio.{FiberId, *}
import zio.stream.ZStream

import scala.reflect.TypeTest

trait ErrorWrapper[E] {
  type EX <: Throwable

  given exceptionTypeTest: TypeTest[Throwable, EX]

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

  def forWrappedError[E, EXImpl <: WrappedErrorBase[E]](create: Cause[E] => EXImpl)(using TypeTest[Throwable, EXImpl]): ErrorWrapper[E] =
    new ErrorWrapper[E] {
      override type EX = EXImpl | InterruptedException
      override def exceptionTypeTest: TypeTest[Throwable, EX] = new TypeTest[Throwable, EX] {
        override def unapply(x: Throwable): Option[x.type & EX] =
          x match {
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

  def forWrappedErrorPassthrough[E, JEX <: E & Throwable, EXImpl <: WrappedErrorBase[E]](create: Cause[E] => EXImpl)(using TypeTest[Throwable, EXImpl], TypeTest[E, JEX], TypeTest[Throwable, JEX]): ErrorWrapper[E] =
    new ErrorWrapper[E] {
      override type EX = EXImpl | InterruptedException | JEX
      override def exceptionTypeTest: TypeTest[Throwable, EX] = new TypeTest[Throwable, EX] {
        override def unapply(x: Throwable): Option[x.type & EX] =
          x match {
            case ex: EXImpl => Some(ex.asInstanceOf[x.type & EXImpl])
            case ex: (x.type & InterruptedException) => Some(ex)
            case ex: JEX => Some(ex.asInstanceOf[x.type & JEX])
            case _ => None
          }
      }

      override def wrap(error: Cause[E]): EX =
        if error.isInterruptedOnly then
          new InterruptedException()
        else if error.isFailure && error.stripFailures.isEmpty then
          error.failures match {
            case List(e: JEX) => e
            case _ => create(error)
          }
        else
          create(error)

      override def unwrap(ex: EX): Cause[E] =
        ex match {
          case _: InterruptedException => Cause.interrupt(FiberId.None)
          case ex: JEX => Cause.fail(ex)
          case ex: EXImpl => ex.cause
        }
    }

  private final class WrappedCauseNothing(cause: Cause[Nothing]) extends WrappedErrorBase(cause)

  given ErrorWrapper[Nothing] = forWrappedError(WrappedCauseNothing(_))



  final class Context[E] {
    final class ContextException(cause: Cause[E]) extends WrappedErrorBase[E](cause)
    given ErrorWrapper[E] = forWrappedError(ContextException(_))
  }


}

