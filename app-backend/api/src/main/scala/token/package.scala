package com.azavea.rf.api

package object token extends RfJsonProtocols {
  implicit val refreshTokenJson = jsonFormat1(RefreshToken)
  implicit val deviceCredentialJson = jsonFormat2(DeviceCredential)
  implicit val authorizedTokenJson = jsonFormat3(AuthorizedToken)
}
