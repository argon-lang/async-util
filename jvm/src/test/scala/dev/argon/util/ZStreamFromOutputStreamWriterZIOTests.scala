package dev.argon.util

import dev.argon.util.async.ZStreamFromOutputStreamWriterZIO
import zio.*
import zio.test.*
import zio.test.Assertion.*

object ZStreamFromOutputStreamWriterZIOTests extends ZIOSpecDefault {

  override def spec: Spec[Environment & Scope, Any] =
    suite("ZStreamFromOutputStreamWriter")(
      test("Transfer bytes")(
        assertZIO(ZStreamFromOutputStreamWriterZIO { os =>
          ZIO.succeed {
            os.write(1)
            os.write(2)
            os.write(Array[Byte](3))
          }
        }.runCollect)(equalTo(Chunk[Byte](1, 2, 3)))
      ),
      test("Error")(
        assertZIO(ZStreamFromOutputStreamWriterZIO { _ =>
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
            task <- ZStreamFromOutputStreamWriterZIO { os =>
              startQueue.offer((())) *>
              ZIO.succeed { os.write(1) }
                .forever
                .tapDefect { cause =>
                  ZIO.foreachDiscard(cause.defects) {
                    case ex: InterruptedException => gotInterrupt.set(true)
                    case _ => ZIO.unit
                  }
                }
                .onExit { _ => gotInterrupt.set(true) }
            }.runDrain.fork
            _ <- startQueue.take
            _ <- task.interrupt
            gotInterrupt <- gotInterrupt.get
          yield gotInterrupt
        )(isTrue)
      ),
    )

}
