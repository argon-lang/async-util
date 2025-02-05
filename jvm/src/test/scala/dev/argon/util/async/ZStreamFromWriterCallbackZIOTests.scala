package dev.argon.util.async

import dev.argon.util.async.ZStreamFromWriterCallbackZIO
import zio.*
import zio.stream.*
import zio.test.*
import zio.test.Assertion.*

object ZStreamFromWriterCallbackZIOTests extends ZIOSpecDefault {

  override def spec: Spec[Environment & Scope, Any] =
    suite("ZStreamFromWriterCallback")(
      test("Transfer strings")(
        assertZIO(ZStreamFromWriterCallbackZIO { w =>
          ZIO.succeed {
            w.write("A")
            w.write("B")
            w.write("CD")
          }
        }.run(ZSink.mkString))(equalTo("ABCD"))
      ),
      test("Error")(
        assertZIO(ZStreamFromWriterCallbackZIO { _ =>
          ZIO.attempt {
            throw new RuntimeException("stop")
          }
        }.runCollect.exit)(fails(anything))
      ),
      test("Interrupt")(
        assertZIO(
          for
            startQueue <- Queue.unbounded[Unit]
            gotInterrupt <- Ref.make(false)
            task <- ZStreamFromWriterCallbackZIO { w =>
              startQueue.offer((())) *>
              ZIO.succeed { w.write("A") }
                .forever
                .onExit {
                  case Exit.Failure(cause) if cause.isInterruptedOnly =>
                    gotInterrupt.set(true)

                  case _ => ZIO.unit
                }
            }.runDrain.fork
            _ <- startQueue.take
            _ <- task.interrupt
            gotInterrupt <- gotInterrupt.get
          yield gotInterrupt
        )(isTrue)
      ),
    )

}
