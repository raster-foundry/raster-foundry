package com.azavea.rf.api

package object config extends RfJsonProtocols {
  implicit val featureFlagFormat = jsonFormat4(FeatureFlag)
  implicit val configFormat = jsonFormat3(AngularConfig)
}
