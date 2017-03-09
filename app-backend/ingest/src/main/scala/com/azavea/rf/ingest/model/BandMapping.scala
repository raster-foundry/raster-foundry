package com.azavea.rf.ingest.model

import spray.json._
import DefaultJsonProtocol._

/** An object defining the mapping of source bands to target bands when multiband output is expected
  *
  * @param source The band number of a source tile to read
  * @param target The band number in the multibandtile on write
  */
case class BandMapping(source: Int, target: Int)

object BandMapping {
  implicit val jsonFormat = jsonFormat2(BandMapping.apply _)
}

