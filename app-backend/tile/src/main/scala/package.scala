package com.azavea.rf

import spray.json._
import spray.json.DefaultJsonProtocol._

package object tile {
  implicit val defaultMosaicDefinitionFormat = jsonFormat2(MosaicDefinition.apply)
}
