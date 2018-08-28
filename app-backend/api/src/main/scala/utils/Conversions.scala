package com.azavea.rf.api.utils

object EitherToOption {
  def apply[A](either: Either[_, A]): Option[A] = either match {
    case Right(a) => Some(a)
    case _        => None
  }
}
