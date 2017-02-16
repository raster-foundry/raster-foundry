package com.azavea.rf

package object config extends RfJsonProtocols {
  implicit val featureFlagFormat = jsonFormat4(FeatureFlag)
  implicit val configFormat = jsonFormat3(AngularConfig)
}
