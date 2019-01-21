package com.rasterfoundry.http4s

import com.rasterfoundry.database.UserDao
import com.rasterfoundry.datamodel.User

import cats.data.OptionT
import cats.effect.IO
import com.guizmaii.scalajwt.{ConfigurableJwtValidator, JwtToken}
import com.nimbusds.jose.jwk.source.{JWKSource, RemoteJWKSet}
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.proc.BadJWTException
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import doobie.Transactor
import doobie.implicits._
import scalacache.CatsEffect.modes._
import scalacache.Flags
import scalacache.memoization._

import scala.concurrent.duration._

import java.net.URL

trait Authenticators extends LazyLogging {

  val xa: Transactor[IO]

  implicit val cache = Cache.caffeineAuthenticationCache
  implicit val flags = Cache.authenticationCacheFlags

  private val configAuth = ConfigFactory.load()
  private val auth0Config = configAuth.getConfig("auth0")

  val jwksURL = auth0Config.getString("jwksURL")
  val jwkSet: JWKSource[SecurityContext] = new RemoteJWKSet(new URL(jwksURL))

  private def verifyJWT(tokenString: String)
    : Either[BadJWTException, (JwtToken, JWTClaimsSet)] = {
    val token: JwtToken = JwtToken(content = tokenString)
    ConfigurableJwtValidator(jwkSet).validate(token)
  }

  def getUserFromJWTwithCache(userIdFromJWT: String)(
      implicit flags: Flags): IO[Option[User]] =
    memoizeF[IO, Option[User]](Some(30.seconds)) {
      logger.debug(s"Authentication - Getting User ${userIdFromJWT} from DB")
      UserDao
        .getUserById(userIdFromJWT)
        .transact(xa)
    }

  def userFromToken(token: String): OptionT[IO, User] = {
    val userFromTokenIO = verifyJWT(token) match {
      case Right((_, jwtClaims)) => {
        val userIdFromJWT = jwtClaims.getStringClaim("sub")
        getUserFromJWTwithCache(userIdFromJWT)
      }
      case Left(e) =>
        IO(None: Option[User])
    }
    OptionT(userFromTokenIO)
  }
}
