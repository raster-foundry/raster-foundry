package com.azavea.rf

package object config extends RfJsonProtocols {
  implicit val configFormat = jsonFormat1(AngularConfig)
}
