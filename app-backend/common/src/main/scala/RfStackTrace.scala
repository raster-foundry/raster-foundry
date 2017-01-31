package com.azavea.rf.common

import java.io.{PrintWriter, StringWriter}

object RfStackTrace {
  // From http://alvinalexander.com/scala/how-convert-stack-trace-exception-string-print-logger-logging-log4j-slf4j
  def apply(t: Throwable): String = {
    val sw = new StringWriter
    t.printStackTrace(new PrintWriter(sw))
    sw.toString
  }
}
