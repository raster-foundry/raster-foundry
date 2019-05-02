package com.rasterfoundry.datamodel.auth
import io.circe.generic.JsonCodec

@JsonCodec
final case class DeviceCredential(id: String, device_name: String)
