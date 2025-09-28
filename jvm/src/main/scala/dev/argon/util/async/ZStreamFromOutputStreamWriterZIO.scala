package dev.argon.util.async

import java.io.{OutputStream, BufferedOutputStream}
import zio.*
import zio.stream.ZStream

import java.util.Objects

object ZStreamFromOutputStreamWriterZIO {

  def apply[R, E](write: OutputStream => ZIO[R, E, Unit]): ZStream[R, E, Byte] =
    ZStream.unwrapScoped(
      for
        queue <- Queue.bounded[Exit[Option[E], Chunk[Byte]]](2)
        rt <- ZIO.runtime[Any]
        os <- ZIO.succeed { BufferedOutputStream(EnqueueOutputStream(queue, rt)) }
        task <- write(os)
          .zipRight { ZIO.succeed(os.close()) }
          .onExit {
            case Exit.Success(_) =>
              queue.offer(Exit.fail(None))
            case Exit.Failure(cause) =>
              queue.offer(Exit.failCause(cause.map(Some.apply)))
          }
          .fork
      yield ZStream.fromQueue(queue).flattenExitOption.flattenChunks
    )

  private class EnqueueOutputStream(queue: Enqueue[Exit[Nothing, Chunk[Byte]]], rt: Runtime[Any]) extends OutputStream {
    override def write(b: Int): Unit =
      Unsafe.unsafely {
        rt.unsafe.run(queue.offer(Exit.Success(Chunk(b.toByte)))).getOrThrow()
      }

    override def write(b: Array[Byte], off: Int, len: Int): Unit =
      Objects.checkFromIndexSize(off, len, b.length)

      if len > 0 then
        val buff = new Array[Byte](len)
        java.lang.System.arraycopy(b, off, buff, 0, len)
        Unsafe.unsafely {
          rt.unsafe.run(queue.offer(Exit.Success(Chunk.fromArray(buff)))).getOrThrow()
        }
      end if
    end write
  }

}
