package dev.argon.util.async

import zio.*
import zio.stream.*

import java.io.{IOException, InputStream, InterruptedIOException}

object ZStreamFromInputStream {
  
  def apply[R](stream: ZIO[R & Scope, IOException, InputStream]): ZStream[R, IOException, Byte] =
    ZStream.scoped(stream)
      .flatMap { is =>
        ZStream.repeatZIOChunkOption(
          for
            arr <- ZIO.succeed(new Array[Byte](ZStream.DefaultChunkSize))
            bytesRead <- JavaExecuteIO.runJavaRaw {
              try is.read(arr)
              catch {
                case ex: InterruptedIOException =>
                  val ex2 = new InterruptedException(ex.getMessage)
                  ex2.setStackTrace(ex.getStackTrace)
                  throw ex2
              }
            }
              .refineToOrDie[IOException]
              .asSomeError
            chunk <-
              if bytesRead < 0 then
                ZIO.fail(None)
              else
                ZIO.succeed(Chunk.fromArray(arr).take(bytesRead))
          yield chunk
        )
      }
    

}
