package com.rasterfoundry.datamodel

import io.circe.generic.JsonCodec

@JsonCodec
final case class License(shortName: String,
                         name: String,
                         url: String,
                         osiApproved: Boolean,
                         id: Long)
