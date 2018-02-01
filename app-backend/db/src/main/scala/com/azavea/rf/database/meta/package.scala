package com.azavea.rf.database


package object meta {
  trait RFMeta extends GtWktMeta
      with CirceJsonbMeta
      with SingleBandOptionsMeta
      with EnumMeta

  object RFMeta extends RFMeta
}
