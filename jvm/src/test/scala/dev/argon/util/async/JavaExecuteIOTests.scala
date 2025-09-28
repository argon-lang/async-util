package dev.argon.util.async

import zio.*
import zio.test.*
import zio.test.Assertion.*

import scala.reflect.TypeTest

object JavaExecuteIOTests extends ZIOSpecDefault {

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
      ZIO.attemptBlockingInterrupt {
        JavaExecuteIO.runInterruptable(task)
      }
        .catchAll {
          case ex: WrappedStringCause => ZIO.refailCause(ex.cause)
          case ex => ZIO.die(ex)
        }
    }


  def runHelperJava(f: => Int): IO[String, Int] =
    ZIO.runtime[Any].flatMap { runtime =>
      given runtime2: Runtime[Any] = runtime
      JavaExecuteIO.runJava(f)
    }

  override def spec: Spec[Environment & Scope, Any] =
    suite("JavaExecuteIO")(
      suite("runInterruptably")(
        test("Success")(
          assertZIO(runHelper(ZIO.succeed(4)))(equalTo(4))
        ),
        test("Error")(
          assertZIO(runHelper(ZIO.fail("A")).flip)(equalTo("A"))
        ),
        test("Interrupt")(
          assertZIO(
            for {
              didRelease <- Ref.make(false)
              fiber <- runHelper(
                ZIO.acquireReleaseWith(acquire = ZIO.unit)(release = _ => didRelease.set(true))(use = _ => ZIO.never)
              ).fork
              _ <- live(ZIO.sleep(1.second))
              _ <- fiber.interrupt
              res <- didRelease.get
            } yield res
          )(equalTo(true))
        ),
        test("Die")(
          assertZIO(runHelper(ZIO.die(DummyDefectException())).cause.map(_.defects.map(_.getClass)))(equalTo(List(classOf[DummyDefectException])))
        ),
      ),

      suite("runJava")(
        test("Success")(
          assertZIO(runHelperJava(4))(equalTo(4))
        ),
        test("Error")(
          assertZIO(runHelperJava { throw WrappedStringCause(Cause.fail("A")) }.flip)(equalTo("A"))
        ),
        test("Interrupt")(
          assertZIO(
            runHelperJava { throw InterruptedException() }
              .catchAllCause { cause => ZIO.succeed(cause.isInterruptedOnly) }
          )(equalTo(true))
        ),
        test("Die")(
          assertZIO(
            runHelperJava { throw DummyDefectException() }
              .cause.map(_.defects.map(_.getClass))
          )(equalTo(List(classOf[DummyDefectException])))
        ),
      ),
    )

}
