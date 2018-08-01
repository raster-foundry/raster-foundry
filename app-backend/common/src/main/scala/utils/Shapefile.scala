package com.azavea.rf.common.utils

object Shapefile {
  def accumulateFeatures[T1, T2](f: T1 => Option[T2])
                        (accum: List[T2], errorIndices: List[Int], accumulateFrom: List[T1], fieldsO: Option[Map[String, String]] = None):
      Either[List[Int], List[T2]] =
    accumulateFrom match {
      case Nil => {
        errorIndices.length match {
          case 0 => Right(accum)
          case _ => Left(errorIndices)
        }
      }
      case h +: t => {
        f(h, fieldsO) match {
          case Some(t2) => accumulateFeatures(f)(
            accum :+ t2, errorIndices, t
          )
          case None => accumulateFeatures(f)(
            accum, errorIndices :+ (accum.length + errorIndices.length + 1), t
          )
        }
      }
  }
}
