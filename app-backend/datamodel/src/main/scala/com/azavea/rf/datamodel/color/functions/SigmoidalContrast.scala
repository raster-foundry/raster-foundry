package com.azavea.rf.datamodel.color.functions

import geotrellis.raster._
import org.apache.commons.math3.util.FastMath

/**
  * Usage of Approximations.{pow | exp} functions can allow to speed up this function on 10 - 15ms.
  * We can consider these functions usages in case of real performance issues caused by a long sigmoidal contrast.
  */
object SigmoidalContrast {

  /**
    * @param  cellType   The cell type on which the transform is to act
    * @param  alpha      The center around-which the stretch is performed (given as a fraction)
    * @param  beta       The standard deviation in the computation, used to avoid saturating the upper and lower parts of the gamut
    * @param  intensity  The raw intensity value to be mapped-from
    * @return            The intensity value produced by the sigmoidal contrast transformation
    */
  def transform(cellType: CellType, alpha: Double, beta: Double)(
      intensity: Double): Double = {
    val bits = cellType.bits

    val u = cellType match {
      case _: FloatCells =>
        (intensity - Float.MinValue) / (Float.MaxValue - Float.MinValue)
      case _: DoubleCells =>
        (intensity / 2 - Double.MinValue / 2) / (Double.MaxValue / 2 - Double.MinValue / 2)
      case _: BitCells | _: UByteCells | _: UShortCells =>
        intensity / ((1 << bits) - 1)
      case _: ByteCells | _: ShortCells | _: IntCells =>
        (intensity + (1 << (bits - 1))) / ((1 << bits) - 1)
    }

    val numer = 1 / (1 + FastMath.exp(beta * (alpha - u))) - 1 / (1 + FastMath
      .exp(beta))
    val denom = 1 / (1 + FastMath.exp(beta * (alpha - 1))) - 1 / (1 + FastMath
      .exp(beta * alpha))
    val gu = math.max(0.0, math.min(1.0, numer / denom))

    cellType match {
      case _: FloatCells =>
        Float.MaxValue * (2 * gu - 1d)
      case _: DoubleCells =>
        Double.MaxValue * (2 * gu - 1d)
      case _: BitCells | _: UByteCells | _: UShortCells =>
        ((1 << bits) - 1) * gu
      case _: ByteCells | _: ShortCells | _: IntCells =>
        (((1 << bits) - 1) * gu) - (1 << (bits - 1))
    }
  }

  /**
    * Given a singleband [[Tile]] object and the parameters alpha and
    * beta, perform the sigmoidal contrast computation and return the
    * result as a tile.
    *
    * The approach used is described here:
    * https://www.imagemagick.org/Usage/color_mods/#sigmoidal
    *
    * @param  tile   The input tile
    * @param  alpha  The center around-which the stretch is performed (given as a fraction)
    * @param  beta   The standard deviation in the computation, used to avoid saturating the upper and lower parts of the gamut
    * @return        The output tile
    */
  def apply(tile: Tile, alpha: Double, beta: Double): Tile = {
    val localTransform = transform(tile.cellType, alpha, beta) _
    tile.mapDouble(localTransform)
  }

  def localTransform(cellType: CellType, alpha: Double, beta: Double) =
    transform(cellType, alpha, beta) _

  /**
    * Given a [[MultibandTile]] object and the parameters alpha and
    * beta, perform the sigmoidal contrast computation on each band
    * and return the result as a multiband tile.
    *
    * The approach used is described here:
    * https://www.imagemagick.org/Usage/color_mods/#sigmoidal
    *
    * @param  tile   The input multibandtile
    * @param  alpha  The center around-which the stretch is performed (given as a fraction)
    * @param  beta   The standard deviation in the computation, used to avoid saturating the upper and lower parts of the gamut
    * @return        The output tile
    */
  def apply(tile: MultibandTile, alpha: Double, beta: Double): MultibandTile = {
    val localTransform = transform(tile.cellType, alpha, beta) _
    MultibandTile(tile.bands.map(_.mapDouble(localTransform)))
  }
}
