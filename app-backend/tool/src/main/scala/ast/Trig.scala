package com.azavea.rf.tool.ast

sealed trait Trig

object Trig {
  sealed trait Circular extends Trig
  case object Sin extends Circular
  case object Cos extends Circular
  case object Tan extends Circular

  sealed trait Inverse extends Trig
  case object Asin extends Inverse
  case object Atan extends Inverse
  case object Acos extends Inverse

  sealed trait Hyperbolic extends Trig
  case object Sinh extends Hyperbolic
  case object Tanh extends Hyperbolic
  case object Cosh extends Hyperbolic
}
