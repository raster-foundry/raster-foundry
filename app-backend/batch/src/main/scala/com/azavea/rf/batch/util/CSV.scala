package com.azavea.rf.batch.util

import com.opencsv._

import java.io._
import java.net._

object CSV {
  def getBiFunctions(header: Array[String]): (Map[String, Int], Map[Int, String]) = {
    val idToIndex: Map[String, Int] = header.zipWithIndex.toMap
    val indexToId = idToIndex map { case (v, i) => i -> v }

    idToIndex -> indexToId
  }

  def parse(uri: URI): CSVReader = new CSVReader(new InputStreamReader(getStream(uri)))
  def parse(uri: String): CSVReader = parse(new URI(uri))
}
