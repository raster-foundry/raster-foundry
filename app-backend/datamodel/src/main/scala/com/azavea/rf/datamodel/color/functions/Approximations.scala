package com.azavea.rf.datamodel.color.functions

/**
  * Not very good pow and exponent approximations
  * consider org.apache.commons.math{3|4}.util.FastMath usage
  * in cases where these functions can't be applied.
  *
  * ApproximationsSpec demonstrates max deviation on SaturationAdjust and SigmoidalContrast domains
  */
object Approximations {

  /**
    * For clarifications
    * see https://www.reddit.com/r/gamedev/comments/n7na0/fast_approximation_to_mathpow/
    * and https://martin.ankerl.com/2007/10/04/optimized-pow-approximation-for-java-and-c-c/
    *
    */
  def pow(a: Double, b: Double): Double = { // exponentiation by squaring
    if (a < 1 && java.lang.Double.isInfinite(b)) return 0d
    if (a >= 1 && java.lang.Double.isInfinite(b)) return Double.NaN
    if (java.lang.Double.isNaN(a) || java.lang.Double.isNaN(b))
      return Double.NaN

    var r = 1d
    var exp = b.toInt
    var base = a
    while (exp != 0) {
      if ((exp & 1) != 0) r *= base
      base *= base
      exp >>= 1
    }
    // use the IEEE 754 trick for the fraction of the exponent
    val b_faction = b - b.toInt
    val tmp = java.lang.Double.doubleToLongBits(a)
    val tmp2 = (b_faction * (tmp - 4606921280493453312L)).toLong + 4606921280493453312L
    r * java.lang.Double.longBitsToDouble(tmp2)
  }

  /**
    * For clarifications
    * see http://martin.ankerl.com/2007/02/11/optimized-exponential-functions-for-java/
    *
    */
  def exp(value: Double): Double = {
    val tmp = (1512775 * value + (1072693248 - 60801)).toLong
    java.lang.Double.longBitsToDouble(tmp << 32)
  }
}
