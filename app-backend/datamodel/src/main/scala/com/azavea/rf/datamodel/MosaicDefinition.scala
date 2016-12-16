package com.azavea.rf.datamodel

import spray.json._
import spray.json.DefaultJsonProtocol._

case class MosaicDefinition(
  val i: Int = 1,
  val d: Double = 1.0
)

object MosaicDefinition {
  def tupled = (MosaicDefinition.apply _).tupled

  implicit val defaultMosaicDefinitionFormat = jsonFormat2(MosaicDefinition.apply _)
}
