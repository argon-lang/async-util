package dev.argon.util.async

import zio.*
import scala.compiletime.uninitialized
import java.util.concurrent.locks.ReentrantLock

private[util] final class JavaExecuteIO[R, E, A](using rt: Runtime[R], errorWrapper: ErrorWrapper[E]) {
  private var isComplete: Boolean = false
  private var isError: Boolean = false
  private var result: A = uninitialized
  private var error: Throwable = uninitialized
  private val lock = new ReentrantLock()
  private val condition = lock.newCondition().nn

  def execute(task: ZIO[R, E, A]): A =
    Unsafe.unsafely {
      val fiber = rt.unsafe.fork(task.onExit(onComplete))

      var exitResult: Option[A] = None

      try {
        lock.lock()
        try while !isComplete do condition.await()
        finally lock.unlock()
      }
      catch {
        case _: InterruptedException =>
          val exit = summon[Runtime[R]].unsafe
            .run(fiber.interrupt)
            .flattenExit

          exit match {
            case Exit.Success(a) => exitResult = Some(a)
            case Exit.Failure(cause) =>
              throw errorWrapper.wrap(cause)
          }
      }

      if isError then
        throw error
      else
        exitResult.getOrElse(result)
    }

  private def onComplete(exit: Exit[E, A]): UIO[Unit] =
    ZIO.succeed {
      exit match {
        case Exit.Success(a) =>
          lock.lock()
          try
            isComplete = true
            result = a
            condition.signalAll()
          finally lock.unlock()

        case Exit.Failure(cause) =>
          lock.lock()
          try
            isComplete = true
            isError = true
            error =
              if cause.isInterruptedOnly then new InterruptedException()
              else errorWrapper.wrap(cause)
            condition.signalAll()
          finally lock.unlock()
      }
    }
}

object JavaExecuteIO {
  def runInterruptable[R, E, A](task: ZIO[R, E, A])(using Runtime[R], ErrorWrapper[E]): A =
    val exec = new JavaExecuteIO[R, E, A]
    exec.execute(task)
  end runInterruptable

  def runJava[E, A](f: => A)(using ew: ErrorWrapper[E]): IO[E, A] =
    ZIO.attemptBlockingInterrupt {
      f
    }
      .catchAll {
        case ex: ew.EX => ZIO.failCause(ew.unwrap(ex))
        case ex => ZIO.die(ex)
      }

}
