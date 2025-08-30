package dev.argon.util.async

import zio.{Chunk, Exit, Fiber, Scope, Unsafe, ZIO}
import zio.stream.ZStream

import java.io.{IOException, InputStream, InterruptedIOException}
import java.util.Objects

object ZStreamToInputStream {

  def apply[R](stream: ZStream[R, IOException, Byte]): ZIO[R & Scope, IOException, InputStream] =
    for
      runtime <- ZIO.runtime[R]
      _ <- ZIO.serviceWith[Scope](_.addFinalizer(ZIO.succeed(println("Scope closed"))))
      pull <- stream.tapErrorCause(cause => ZIO.succeed(println("stream cause " + cause))).toPull
    yield new InputStream {
      private var currentData: Chunk[Byte] = Chunk.empty
      private var isEnd = false

      override def read(): Int =
        val b = new Array[Byte](1)
        while
          val bytesRead = read(b)
          if bytesRead < 0 then return -1

          bytesRead == 0
        do ()

        java.lang.Byte.toUnsignedInt(b(0))
      end read

      override def read(b: Array[Byte], off: Int, len: Int): Int =
        Objects.checkFromIndexSize(off, len, b.length)

        def getChunk: Chunk[Byte] =
          if currentData.nonEmpty then
            currentData
          else if isEnd then
            Chunk.empty
          else
            Unsafe.unsafely(runtime.unsafe.run(pull)) match {
              case Exit.Success(data) if data.isEmpty =>
                getChunk
              case Exit.Success(data) =>
                currentData = data
                data

              case Exit.Failure(cause) =>
                println("getChunk cause " + cause)
                cause.failureOrCause match {
                  case Left(Some(ex)) => throw ex
                  case Left(None) =>
                    isEnd = true
                    Chunk.empty

                  case Right(cause) =>
                    cause.squash match {
                      case ex: InterruptedException =>
                        val ex2 = new InterruptedIOException(ex.getMessage)
                        ex2.setStackTrace(ex.getStackTrace)
                        throw ex2

                      case ex => throw ex
                    }
                }
            }
          end if

        val chunk = getChunk

        if chunk.isEmpty then
          return -1

        val bytesRead = Math.min(len, chunk.size)
        chunk.copyToArray(b, off, bytesRead)
        currentData = chunk.drop(bytesRead)
        bytesRead
      end read
    }
}
