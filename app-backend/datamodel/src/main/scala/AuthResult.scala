package com.rasterfoundry.datamodel

sealed trait AuthResult[T] {
  def toBoolean: Boolean
}

case class AuthSuccess[T](
    value: T
) extends AuthResult[T] {
  def toBoolean: Boolean = true
}

case class AuthFailure[T]() extends AuthResult[T] {
  def toBoolean: Boolean = false
}

object AuthResult {
  def fromOption[T](src: Option[T]): AuthResult[T] = src match {
    case Some(v) => AuthSuccess(v)
    case _       => AuthFailure()
  }
}
