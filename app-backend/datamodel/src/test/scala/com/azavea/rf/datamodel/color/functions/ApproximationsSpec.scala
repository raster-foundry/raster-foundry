package com.azavea.rf.datamodel.color.functions

import org.apache.commons.math3.util.FastMath
import org.scalatest._

class ApproximationsSpec extends FunSpec with Matchers {
  it("Approximations.pow in SaturationAdjust should be +- 0.1 of the FastMath.pow") {
    // Chroma is a Double in the range [0.0, 1.0]. Scale factor is the same as our other gamma corrections:
    // a Double in the range [0.0, 2.0].
    val chromas = 0d to 1d by 0.2
    val scaleFactors = 0d to 2d by 0.2

    for {
      chroma <- chromas
      scaleFactor <- scaleFactors
    } yield {
      val fst = Approximations.pow(chroma, 1d / scaleFactor)
      val snd = FastMath.pow(chroma, 1d / scaleFactor)
      val thrd = math.pow(chroma, 1d / scaleFactor)

      if(!java.lang.Double.isNaN(fst) && !java.lang.Double.isNaN(snd) && !java.lang.Double.isNaN(thrd)) {
        fst shouldBe snd +- 0.1
      } else {
        java.lang.Double.isNaN(fst) shouldBe java.lang.Double.isNaN(snd)
        java.lang.Double.isNaN(snd) shouldBe java.lang.Double.isNaN(thrd)
      }
    }
  }

  it("Approximations.exp in SigmoidalContrast should be +- 403 of the FastMath.exp") {
    val alphas = 0d to 1d by 0.2
    val betas = 0d to 10d by 0.2

    for {
      alpha <- alphas
      beta <- betas
    } yield {
      val fst = Approximations.exp(beta * alpha)
      val snd = FastMath.exp(beta * alpha)
      val thrd = math.exp(beta * alpha)

      if(!java.lang.Double.isNaN(fst) && !java.lang.Double.isNaN(snd) && !java.lang.Double.isNaN(thrd)) {
        fst shouldBe snd +- 403
      } else {
        java.lang.Double.isNaN(fst) shouldBe java.lang.Double.isNaN(snd)
        java.lang.Double.isNaN(snd) shouldBe java.lang.Double.isNaN(thrd)
      }
    }
  }
}
