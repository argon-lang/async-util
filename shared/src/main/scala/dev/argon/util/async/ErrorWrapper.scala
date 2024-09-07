package dev.argon.util.async

import zio.*
import zio.stream.ZStream

import scala.reflect.TypeTest

trait ErrorWrapper[E] {
  type EX <: Throwable

  given exceptionTypeTest: TypeTest[Throwable, EX]

  def wrap(error: Cause[E]): EX
  def unwrap(ex: EX): Cause[E]
}

object ErrorWrapper {
  def apply[E](using errorWrapper: ErrorWrapper[E]): ErrorWrapper[E] =
    errorWrapper

  def wrapEffect[R, E, A](a: ZIO[R, E, A])(using errorWrapper: ErrorWrapper[E]): ZIO[R, errorWrapper.EX, A] =
    a.mapErrorCause { cause => Cause.fail(errorWrapper.wrap(cause)) }

  def wrapStream[R, E, A](a: ZStream[R, E, A])(using errorWrapper: ErrorWrapper[E]): ZStream[R, errorWrapper.EX, A] =
    a.mapErrorCause { cause => Cause.fail(errorWrapper.wrap(cause)) }

  def unwrapEffect[R, E, A](a: ZIO[R, Throwable, A])(using errorWrapper: ErrorWrapper[E]): ZIO[R, E, A] =
    a.catchAll {
      case ex: errorWrapper.EX => ZIO.failCause(errorWrapper.unwrap(ex))
      case ex => ZIO.die(ex)
    }

  def unwrapStream[R, E, A](a: ZStream[R, Throwable, A])(using errorWrapper: ErrorWrapper[E]): ZStream[R, E, A] =
    a.catchAll {
      case ex: errorWrapper.EX => ZStream.failCause(errorWrapper.unwrap(ex))
      case ex => ZStream.die(ex)
    }

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



}

