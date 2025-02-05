package dev.argon.util.async

import zio.*

import scala.reflect.TypeTest
import scala.scalajs.js
import scala.util.{Success, Failure}

import scala.scalajs.concurrent.JSExecutionContext.Implicits.given

object JSPromiseUtil {

  def runEffectToPromise[R, E, A](a: ZIO[R, E, A])(using runtime: Runtime[R], errorWrapper: ErrorWrapper[E]): js.Promise[A] =
    Unsafe.unsafely {
      new js.Promise[A]((resolve, reject) => {
        runtime.unsafe.runToFuture(ErrorWrapper.wrapEffect(a).toPromiseJS)
          .onComplete {
            case Success(value) => resolve(value)
            case Failure(exception) => reject(exception)
          }
      })

    }

  def promiseToEffect[E, A](a: => js.Promise[A])(using ErrorWrapper[E]): IO[E, A] =
    ErrorWrapper.unwrapEffect(ZIO.fromPromiseJS(a))

}
