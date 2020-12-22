package com.rasterfoundry.datamodel

import cats.{Applicative, Functor}
import cats.syntax.applicative._
import cats.syntax.functor._

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
  def fromOption[T](src: Option[T]): AuthResult[T] =
    src match {
      case Some(v) => AuthSuccess(v)
      case _       => AuthFailure()
    }

  def combine[T](x: AuthResult[T], y: AuthResult[T]): AuthResult[T] =
    (x, y) match {
      case (succ @ AuthSuccess(_), _) => succ
      case (_, succ @ AuthSuccess(_)) => succ
      case _                          => AuthFailure()
    }

  def success[F[_]: Applicative, T](v: T): F[AuthResult[T]] =
    AuthSuccess(v).pure[F].widen

  def failed[F[_]: Applicative, T]: F[AuthResult[T]] =
    AuthFailure[T]().pure[F].widen

  implicit val functorAuthResult: Functor[AuthResult] =
    new Functor[AuthResult] {
      def map[A, B](fa: AuthResult[A])(f: A => B): AuthResult[B] =
        fa match {
          case AuthSuccess(v) => AuthSuccess(f(v))
          case AuthFailure()  => AuthFailure()
        }
    }
}
