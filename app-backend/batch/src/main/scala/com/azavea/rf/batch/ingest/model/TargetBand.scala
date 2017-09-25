package com.azavea.rf.batch.ingest.model

import io.circe.generic.JsonCodec

/** An object defining the name and number of target bands in band mappings
  *
  * @param name: the name of the target band
  * @param index: The band number in the multibandtile on write. Starts at 1
  *               to line up with source data like Landsat and Sentinel
  */
@JsonCodec
case class TargetBand(name: String, index: Int)
