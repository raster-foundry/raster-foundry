package com.azavea.rf.backsplash.auth

import com.azavea.rf.datamodel.User

import cats.data.OptionT
import org.http4s.server._

object Authenticators {
  val queryParamAuthenticator = Kleisli[OptionT[IO, User], Request[IO], User] {
    ???
  }
}
