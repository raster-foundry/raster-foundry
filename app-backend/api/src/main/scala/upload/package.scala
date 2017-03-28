package com.azavea.rf.api

import com.azavea.rf.common.STS.Credentials

package object upload extends RfJsonProtocols {
  implicit val getFederationTokenResultFormat = jsonFormat4(Credentials)
}
