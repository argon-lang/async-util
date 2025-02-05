package dev.argon.util.async

import zio.*
import zio.stream.*
import zio.test.*
import zio.test.Assertion.*

import scala.reflect.TypeTest

object AsyncIterableToolsTests extends ZIOSpecDefault {

  final case class WrappedStringCause(cause: Cause[String]) extends Exception
  final case class DummyDefectException() extends Exception

  given ErrorWrapper[String] with
    type EX = WrappedStringCause
    override def exceptionTypeTest: TypeTest[Throwable, WrappedStringCause] = summon

    override def wrap(cause: Cause[String]): WrappedStringCause = WrappedStringCause(cause)
    override def unwrap(ex: WrappedStringCause): Cause[String] = ex.cause
  end given

  override def spec: Spec[Environment & Scope, Any] =
    suite("AsyncIterableTools")(
      test("inverse")(
        for
          runtime <- ZIO.runtime[Any]
          given Runtime[Any] = runtime
          items <- AsyncIterableTools.asyncIterableToZStreamRaw(AsyncIterableTools.zstreamToAsyncIterable(ZStream("a", "b", "c"))).runCollect
        yield assertTrue(items.toList == List("a", "b", "c"))
      ),
    )

}
