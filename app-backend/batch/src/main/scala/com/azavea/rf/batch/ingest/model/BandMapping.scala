package com.azavea.rf.batch.ingest.model

import io.circe.generic.JsonCodec

/** An object defining the mapping of source bands to target bands when multiband output is expected
  *
  * @param source The band number of a source tile to read
  * @param target The band number in the multibandtile on write
  */
@JsonCodec
case class BandMapping(source: Int, target: Int)
