package com.azavea.rf.common.utils

import geotrellis.util._

final case class CacheRangeReader(rr: RangeReader, cachedBytes: Array[Byte])
    extends RangeReader {
  def totalLength: Long = rr.totalLength

  override def readRange(start: Long, length: Int): Array[Byte] = {
    val end = length + start
    if (end <= cachedBytes.length)
      java.util.Arrays.copyOfRange(cachedBytes, start.toInt, end.toInt)
    else
      rr.readRange(start, length)
  }

  protected def readClippedRange(start: Long, length: Int): Array[Byte] = ???

  override def readAll(): Array[Byte] = {
    rr.readAll()
  }
}
