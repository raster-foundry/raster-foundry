package com.azavea.rf.datamodel

import java.io.{PrintWriter, StringWriter}

import geotrellis.raster.{MultibandTile, UByteConstantNoDataCellType, isData}
import spire.syntax.cfor.cfor

import scala.annotation.tailrec
import scala.math.{abs, signum}

final case class Memo[I, K, O](f: I => O)(implicit ev: I => K)
    extends (I => O) {
  import collection.mutable.{Map => Dict}
  type Input = I
  type Key = K
  type Output = O

  val cache: Dict[K, O] = Dict.empty[K, O]
  override def apply(x: I): O = cache.getOrElseUpdate(x, f(x))
}

object WhiteBalance {

  @inline def clamp8Bit(z: Int): Int = {
    if (z < 0) 0
    else if (z > 255) 255
    else z
  }

  def apply(rgbTiles: List[MultibandTile]): List[MultibandTile] = {
    val tileAdjs = rgbTiles.par
      .map(t => tileRgbAdjustments(t))
      .toList
      .foldLeft((0.0, 0.0, 0.0))((acc, x) => {
        (
          acc._1 + (x._1 / rgbTiles.length),
          acc._2 + (x._2 / rgbTiles.length),
          acc._3 + (x._3 / rgbTiles.length)
        )
      })

    val newTiles =
      rgbTiles
        .map(
          t =>
            MultibandTile(
              t.band(0).mapIfSet(c => clamp8Bit((c * tileAdjs._1).toInt)),
              t.band(1).mapIfSet(c => clamp8Bit((c * tileAdjs._2).toInt)),
              t.band(2).mapIfSet(c => clamp8Bit((c * tileAdjs._3).toInt))
          )
        )
        .map(_.convert(UByteConstantNoDataCellType))

    newTiles
  }

  @SuppressWarnings(Array("CatchException"))
  def tileRgbAdjustments(rgbTile: MultibandTile): (Double, Double, Double) = {
    try {
      val resampledTile = rgbTile.resample(128, 128)
      val newTileAdjs =
        adjustGrey(resampledTile, (1.0, 1.0, 1.0), 0, converged = false)
      newTileAdjs
    } catch {
      case ex: Exception =>
        val sw = new StringWriter
        ex.printStackTrace(new PrintWriter(sw))
        println(sw.toString)
        throw ex
    }
  }

  @SuppressWarnings(Array("MethodNames"))
  def RgbToYuv(rByte: Int, gByte: Int, bByte: Int): YUV = {
    type I = (Int, Int, Int)
    type K = (Int, Int, Int)
    type O = YUV

    type MemoizedFn = Memo[I, K, O]

    implicit def encode(input: MemoizedFn#Input): MemoizedFn#Key =
      (input._1, input._2, input._3)

    def rgbToYuv(rb: Int, gb: Int, bb: Int): YUV = {
      val (r, g, b) = (rb, gb, bb)
      val W_r = 0.299
      val W_b = 0.114
      val W_g = 1 - W_r - W_b

      val Y = W_r * r + W_g * g + W_b * b
      val U = -0.168736 * r + -0.331264 * g + 0.5 * b
      val V = 0.5 * r + -0.418688 * g + -0.081312 * b

      YUV(Y, U, V)
    }

    lazy val f: MemoizedFn = Memo {
      case (r, g, b) => rgbToYuv(r, g, b)
      case _         => throw new IllegalArgumentException("Wrong number of arguments")
    }

    f((rByte, gByte, bByte))
  }

  final case class AutoBalanceParams(maxIter: Int,
                                     gainIncr: Double,
                                     doubleStepThreshold: Double,
                                     convergenceThreshold: Double,
                                     greyThreshold: Double)

  final case class YUV(y: Double, u: Double, v: Double)

  val balanceParams = AutoBalanceParams(
    maxIter = 1000,
    gainIncr = 0.01,
    doubleStepThreshold = 0.8,
    convergenceThreshold = 0.001,
    greyThreshold = 0.3
  )

  def mapBands(mbTile: MultibandTile,
               f: Array[Int] => Option[YUV]): List[List[Option[YUV]]] = {
    val newTile = MultibandTile(mbTile.bands)
    val array = Array.ofDim[Option[YUV]](newTile.cols, newTile.rows)

    cfor(0)(_ < newTile.rows, _ + 1) { row =>
      cfor(0)(_ < newTile.cols, _ + 1) { col =>
        val bandValues = Array.ofDim[Int](newTile.bandCount)
        cfor(0)(_ < newTile.bandCount, _ + 1) { band =>
          bandValues(band) = newTile.band(band).get(col, row)
        }
        array(col)(row) = f(bandValues)
      }
    }

    array.map(_.toList).toList
  }

  @tailrec
  def adjustGrey(rgbTile: MultibandTile,
                 adjustments: (Double, Double, Double),
                 iter: Int,
                 converged: Boolean): (Double, Double, Double) = {
    if (iter >= balanceParams.maxIter || converged) {
      adjustments
    } else {
      // convert tile to YUV
      val tileYuv = mapBands(
        rgbTile,
        rgb => {
          val bands =
            rgb.toList.map((i: Int) => if (isData(i)) Some(i) else None)
          val r :: g :: b :: xs = bands
          val rgbs = (r, g, b)
          val yuv = rgbs match {
            case (Some(rd), Some(gr), Some(bl)) => Some(RgbToYuv(rd, gr, bl))
            case _                              => None
          }
          yuv
        }
      )

      // find grey chromaticity
      val offGrey = (yuv: YUV) => (abs(yuv.u) + abs(yuv.v)) / yuv.y
      val greys = tileYuv.flatMap(
        lst =>
          lst.map(yuv => {
            for {
              y <- yuv
            } yield {
              if (offGrey(y) < balanceParams.greyThreshold) Some(y) else None
            }
          })
      )

      // calculate average "off-grey"-ness of U & V
      val uBar = greys
        .flatMap(
          yuvs =>
            yuvs.map {
              case Some(yuv) => yuv.u
              case _         => 0.0
          }
        )
        .sum / greys.length

      val vBar = greys
        .flatMap(
          yuvs =>
            yuvs.map {
              case Some(yuv) => yuv.v
              case _         => 0.0
          }
        )
        .sum / greys.length

      // adjust red & blue channels if convergence hasn't been reached
      val err = if (abs(uBar) > abs(vBar)) uBar else vBar
      val gainVal = err match {
        case x if x < balanceParams.convergenceThreshold => 0
        case x if x > (balanceParams.doubleStepThreshold * 1) =>
          2 * balanceParams.gainIncr * signum(err)
        case _ => balanceParams.gainIncr * err
      }

      val channelGain =
        if (abs(vBar) > abs(uBar)) List(1 - gainVal, 1, 1)
        else List(1, 1, 1 - gainVal)
      val newAdjustments = (
        adjustments._1 * channelGain(0),
        adjustments._2 * channelGain(1),
        adjustments._3 * channelGain(2)
      )

      val balancedTile = MultibandTile(
        rgbTile.band(0).mapIfSet(c => clamp8Bit((c * channelGain(0)).toInt)),
        rgbTile.band(1).mapIfSet(c => clamp8Bit((c * channelGain(1)).toInt)),
        rgbTile.band(2).mapIfSet(c => clamp8Bit((c * channelGain(2)).toInt))
      )

      adjustGrey(
        balancedTile,
        newAdjustments,
        iter + 1,
        gainVal == 0
      )
    }
  }

}
