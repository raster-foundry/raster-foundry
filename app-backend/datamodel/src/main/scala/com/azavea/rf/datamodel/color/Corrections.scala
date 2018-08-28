package com.azavea.rf.datamodel.color

import io.circe.generic.JsonCodec

sealed trait ColorCorrection {
  val enabled: Boolean
}

@JsonCodec
final case class BandGamma(enabled: Boolean,
                           redGamma: Option[Double],
                           greenGamma: Option[Double],
                           blueGamma: Option[Double])
    extends ColorCorrection

@JsonCodec
final case class PerBandClipping(enabled: Boolean,
                                 redMax: Option[Int],
                                 greenMax: Option[Int],
                                 blueMax: Option[Int],
                                 redMin: Option[Int],
                                 greenMin: Option[Int],
                                 blueMin: Option[Int])
    extends ColorCorrection

@JsonCodec
final case class MultiBandClipping(enabled: Boolean,
                                   min: Option[Int],
                                   max: Option[Int])
    extends ColorCorrection

@JsonCodec
final case class SigmoidalContrast(enabled: Boolean,
                                   alpha: Option[Double],
                                   beta: Option[Double])
    extends ColorCorrection

@JsonCodec
final case class Saturation(enabled: Boolean, saturation: Option[Double])
    extends ColorCorrection

@JsonCodec
final case class Equalization(enabled: Boolean) extends ColorCorrection

@JsonCodec
final case class AutoWhiteBalance(enabled: Boolean) extends ColorCorrection
