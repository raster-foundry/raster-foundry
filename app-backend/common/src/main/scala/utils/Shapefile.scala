package com.azavea.rf.common.utils

object Shapefile {

  @SuppressWarnings(Array("ListAppend"))
  def accumulateFeatures[T1, T2](
      f: (T1, Map[String, String], String, String) => Option[T2])(
      accum: List[T2],
      errorIndices: List[Int],
      accumulateFrom: List[T1],
      props: Map[String, String],
      userId: String,
      prj: String): Either[List[Int], List[T2]] =
    accumulateFrom match {
      case Nil =>
        errorIndices.length match {
          case 0 => Right(accum)
          case _ => Left(errorIndices)
        }
      case h +: t =>
        f(h, props, userId, prj) match {
          case Some(t2) =>
            accumulateFeatures(f)(
              accum :+ t2,
              errorIndices,
              t,
              props,
              userId,
              prj
            )
          case None =>
            accumulateFeatures(f)(
              accum,
              errorIndices :+ (accum.length + errorIndices.length + 1),
              t,
              props,
              userId,
              prj
            )
        }
    }
}
