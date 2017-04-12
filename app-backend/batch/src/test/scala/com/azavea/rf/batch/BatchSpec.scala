package com.azavea.rf.batch

trait BatchSpec {
  def getJson(resource: String): String = {
    val stream = getClass.getResourceAsStream(resource)
    val lines = scala.io.Source.fromInputStream(stream).getLines
    val json = lines.mkString(" ")
    stream.close()
    json
  }
}
