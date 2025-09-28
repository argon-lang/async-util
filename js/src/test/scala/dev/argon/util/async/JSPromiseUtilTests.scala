package dev.argon.util.async

import zio.*
import zio.test.*
import zio.test.Assertion.*

import scala.reflect.TypeTest

object JSPromiseUtilTests extends ZIOSpecDefault {

  final case class WrappedStringCause(cause: Cause[String]) extends Exception
  final case class DummyDefectException() extends Exception
  
  private val wrappedStringCauseTypeTest = summon[TypeTest[Any, WrappedStringCause]]

  given ErrorWrapper[String]:
    type EX = WrappedStringCause
    override def exceptionTypeTest: TypeTest[Any, WrappedStringCause] = wrappedStringCauseTypeTest

    override def wrap(cause: Cause[String]): WrappedStringCause = WrappedStringCause(cause)
    override def unwrap(ex: WrappedStringCause): Cause[String] = ex.cause
  end given

  def runHelper(task: IO[String, Int]): IO[String, Int] =
    ZIO.runtime[Any].flatMap { runtime =>
      given runtime2: Runtime[Any] = runtime
      ZIO.fromPromiseJS {
        JSPromiseUtil.runEffectToPromise(task)
      }
        .catchAll {
          case ex: WrappedStringCause => ZIO.refailCause(ex.cause)
          case ex => ZIO.die(ex)
        }
    }

  override def spec: Spec[Environment & Scope, Any] =
    suite("JSPromiseUtil")(
      suite("runEffectToPromise")(
        test("Success")(
          assertZIO(runHelper(ZIO.succeed(4)))(equalTo(4))
        ),
        test("Error")(
          assertZIO(runHelper(ZIO.fail("A")).flip)(equalTo("A"))
        ),
        test("Die")(
          assertZIO(runHelper(ZIO.die(DummyDefectException())).cause.map(_.defects.map(_.getClass)))(equalTo(List(classOf[DummyDefectException])))
        ),
      ),
    )

}
