package com.azavea.rf.utils

object EitherToOption {
  def apply[A](either: Either[_, A]): Option[A] = either match {
    case Right(a) => Some(a)
    case _ => None
  }
}
