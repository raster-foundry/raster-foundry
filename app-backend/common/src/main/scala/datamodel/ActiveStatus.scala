package com.rasterfoundry.common.datamodel

import io.circe.generic.JsonCodec

@JsonCodec
final case class ActiveStatus(isActive: Boolean)
