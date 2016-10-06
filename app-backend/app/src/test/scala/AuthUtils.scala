package com.azavea.rf

import spray.json._
import de.choffmeister.auth.common.JsonWebToken
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}

import com.azavea.rf.utils.Config


/** Utility functions for testing endpoints that require authentication / authorization
  */
object AuthUtils extends Config{
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
  ): String = {
    val createdAt = created match {
      case null => {
        new java.util.Date()
      }
      case _ => created
    }
    val expiresAt = expires match {
      case null => {
        val cal = java.util.Calendar.getInstance();
        cal.setTime(createdAt);
        cal.add(java.util.Calendar.DATE, 1)
        cal.getTime()
      }
      case _ => expires
    }
    val claims = Map(
      "sub" -> JsString(sub)
    )
    val token = JsonWebToken(claims, createdAt.toInstant(), expiresAt.toInstant())
    val encodedToken = JsonWebToken.write(token: JsonWebToken, auth0Secret)
    val authorization = Authorization(OAuth2BearerToken(encodedToken))
    encodedToken
  }

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
