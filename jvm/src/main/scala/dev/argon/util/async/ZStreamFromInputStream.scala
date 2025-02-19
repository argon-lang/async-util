package dev.argon.util.async

import zio.*
import zio.stream.*

import java.io.{IOException, InputStream}

object ZStreamFromInputStream {
  
  def apply[R](stream: ZIO[R & Scope, IOException, InputStream]): ZStream[R, IOException, Byte] =
    ZStream.scoped(stream)
      .flatMap { is =>
        ZStream.repeatZIOChunkOption(
          for
            arr <- ZIO.succeed(new Array[Byte](ZStream.DefaultChunkSize))
            bytesRead <- JavaExecuteIO.runJavaRaw { is.read(arr) }
              .refineToOrDie[IOException]
              .asSomeError
            chunk <-
              if bytesRead < 0 then
                ZIO.fail(None)
              else if bytesRead == 0 then
                ZIO.succeed(Chunk.empty)
              else if bytesRead < arr.length then
                ZIO.succeed(Chunk.fromArray(arr).take(bytesRead))
              else
                ZIO.succeed(Chunk.fromArray(arr))
          yield chunk
        )
      }
    

}
