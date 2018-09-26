package com.azavea.rf.datamodel

import io.circe.Json
import io.circe.generic.JsonCodec

object SingleBandOptions {

  @JsonCodec
  final case class Params(band: Int,
                          dataType: BandDataType,
                          colorBins: Int,
                          colorScheme: Json,
                          legendOrientation: String,
                          extraNoData: Seq[Int] = Seq.empty[Int])
}
