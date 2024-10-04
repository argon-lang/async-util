package dev.argon.util.async

import zio.*
import zio.stream.ZStream

import java.io.{BufferedWriter, Writer}
import java.util.Objects

object ZStreamFromWriterCallbackZIO {

  def apply[R, E](write: Writer => ZIO[R, E, Unit]): ZStream[R, E, String] =
    ZStream.unwrapScoped(
      for
        queue <- Queue.bounded[String](2).withFinalizer { queue => queue.shutdown }
        rt <- ZIO.runtime[Any]
        os <- ZIO.succeed { BufferedWriter(EnqueueWriter(queue, rt)) }
        task <- write(os).onExit { _ => ZIO.succeed(os.close()) *> queue.offer("") }.fork
      yield ZStream.fromQueue(queue).takeWhile(_.nonEmpty) ++
        ZStream.fromZIO(task.join).drain
    )

  private class EnqueueWriter(queue: Enqueue[String], rt: Runtime[Any]) extends Writer {
    override def write(cbuf: Array[Char], off: Int, len: Int): Unit =
      if len > 0 then
        write(new String(cbuf, off, len))

    override def write(str: String): Unit =
      Objects.requireNonNull(str)
      if str.nonEmpty then
        Unsafe.unsafely {
          rt.unsafe.run(queue.offer(str))
        }
    end write

    override def write(str: String, off: Int, len: Int): Unit =
      Objects.checkFromIndexSize(off, len, str.length)
      if len > 0 then
        write(str.substring(off, off + len))
    end write


    override def flush(): Unit = ()

    override def close(): Unit = ()
  }

}
