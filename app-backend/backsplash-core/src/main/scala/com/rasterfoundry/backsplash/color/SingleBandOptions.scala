package com.rasterfoundry.backsplash.color

import io.circe.Json
import io.circe.generic.JsonCodec
import com.rasterfoundry.datamodel.BandDataType

object SingleBandOptions {

  @JsonCodec
  final case class Params(band: Int,
                          dataType: BandDataType,
                          colorBins: Int,
                          colorScheme: Json,
                          legendOrientation: String,
                          extraNoData: Seq[Int] = Seq.empty[Int])
}
