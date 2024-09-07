package dev.argon.util.async

import scala.scalajs.js.JavaScriptException

object JSErrorHandler {

  def handleJSError(error: Any): Throwable =
    error.asInstanceOf[Matchable] match {
      case ex: Throwable => ex
      case ex => JavaScriptException(ex)
    }

}
