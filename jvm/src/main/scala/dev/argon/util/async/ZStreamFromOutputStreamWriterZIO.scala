package dev.argon.util.async

import java.io.{OutputStream, BufferedOutputStream}
import zio.*
import zio.stream.ZStream

import java.io.PipedOutputStream
import java.io.PipedInputStream
import java.util.Objects

object ZStreamFromOutputStreamWriterZIO {

  def apply[R, E](write: OutputStream => ZIO[R, E, Unit]): ZStream[R, E, Byte] =
    ZStream.unwrapScoped(
      for
        queue <- Queue.bounded[Chunk[Byte]](2).withFinalizer { queue => queue.shutdown }
        rt <- ZIO.runtime[Any]
        os <- ZIO.succeed { BufferedOutputStream(EnqueueOutputStream(queue, rt)) }
        task <- write(os).onExit { _ => ZIO.succeed(os.close()) *> queue.offer(Chunk.empty) }.fork
      yield ZStream.fromQueue(queue).takeWhile(_.nonEmpty).flattenChunks ++
        ZStream.fromZIO(task.join).drain
    )

  private class EnqueueOutputStream(queue: Enqueue[Chunk[Byte]], rt: Runtime[Any]) extends OutputStream {
    override def write(b: Int): Unit =
      Unsafe.unsafely {
        rt.unsafe.run(queue.offer(Chunk(b.toByte)))
      }

    override def write(b: Array[Byte], off: Int, len: Int): Unit =
      Objects.checkFromIndexSize(off, len, b.length)

      if len > 0 then
        val buff = new Array[Byte](len)
        java.lang.System.arraycopy(b, off, buff, 0, len)
        Unsafe.unsafely {
          rt.unsafe.run(queue.offer(Chunk.fromArray(buff)))
        }
      end if
    end write

    override def close(): Unit = super.close()
  }

}
