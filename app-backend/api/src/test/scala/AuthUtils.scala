package com.azavea.rf.api

import com.azavea.rf.api.utils.Config

import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
//import pdi.jwt.{Jwt, JwtJson4s, JwtAlgorithm, JwtClaim}
import spray.json._

/** Utility functions for testing endpoints that require authentication / authorization
  */
object AuthUtils extends Config {
  /** Generate a JWT token string
    *
    * @param sub the user's identifier (auth0 id, google oauth, etc)
    * @param claims additional claims to make in the token (Not implemented)
    * @param created date the token will say it was created at. Defaults to the current time
    * @param expires date the token will expire. Defaults to 1 day from the current time
    * @return a valid JWT token string
    */
  def generateToken(
    sub: String, claims: Map[String, JsValue] = Map(), created: java.util.Date = null,
    expires: java.util.Date = null
  ): String = "encodedToken"

  /** Generate an authentication header containing a valid JWT token
    *
    * @param sub the user's identifier (auth0 id, google oauth, etc)
    * @param claims additional claims to make in the token (Not implemented)
    * @param created date the token will say it was created at. Defaults to the current time
    * @param expires date the token will expire. Defaults to 1 day from the current time
    * @return an Auth header with a valid JWT token
    */
  def generateAuthHeader(
    sub: String, claims: Map[String, JsValue] = Map(), created: java.util.Date = null,
    expires: java.util.Date = null
  ): Authorization  = {
    Authorization(OAuth2BearerToken(generateToken(sub, claims, created, expires)))
  }
}
