package com.azavea.rf.common.utils

object Shapefile {
  def accumulateFeatures[T1, T2, T3, T4](f: (T1, T3, T4) => Option[T2])
                        (accum: List[T2], errorIndices: List[Int], accumulateFrom: List[T1], props: T3, user: T4):
      Either[List[Int], List[T2]] =
    accumulateFrom match {
      case Nil => {
        errorIndices.length match {
          case 0 => Right(accum)
          case _ => Left(errorIndices)
        }
      }
      case h +: t => {
        f(h, props, user) match {
          case Some(t2) => accumulateFeatures(f)(
            accum :+ t2, errorIndices, t, props, user
          )
          case None => accumulateFeatures(f)(
            accum, errorIndices :+ (accum.length + errorIndices.length + 1), t, props, user
          )
        }
      }
  }
}
