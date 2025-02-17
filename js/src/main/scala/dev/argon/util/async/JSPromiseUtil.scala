package dev.argon.util.async

import zio.*

import scala.reflect.TypeTest
import scala.scalajs.js
import scala.util.{Success, Failure}

import scala.scalajs.concurrent.JSExecutionContext.Implicits.given

object JSPromiseUtil {

  def runEffectToPromiseRaw[R, A](a: RIO[R, A])(using runtime: Runtime[R]): js.Promise[A] =
    Unsafe.unsafely {
      new js.Promise[A]((resolve, reject) => {
        runtime.unsafe.runToFuture(a)
          .onComplete {
            case Success(value) => resolve(value)
            case Failure(exception) => reject(exception)
          }
      })
    }

  def runEffectToPromise[R, E, A](a: ZIO[R, E, A])(using runtime: Runtime[R], errorWrapper: ErrorWrapper[E]): js.Promise[A] =
    runEffectToPromiseRaw(ErrorWrapper.wrapEffect(a))

  def promiseToEffect[E, A](a: => js.Promise[A])(using ErrorWrapper[E]): IO[E, A] =
    ErrorWrapper.unwrapEffect(ZIO.fromPromiseJS(a))

}
