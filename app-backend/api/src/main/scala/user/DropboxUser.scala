package com.azavea.rf.api.user

import java.util.Base64

import com.dropbox.core.DbxSessionStore
import io.circe.generic.JsonCodec

import scala.beans.BeanProperty
import scala.util.Random

@JsonCodec
final case class DropboxAuthRequest(
    authorizationCode: String,
    redirectURI: String
)

/** Mock a DbxSessionStore.
  *
  * To implement the DbxSessionStore interface, we need
  * get, set, and clear. The Dropbox SDK is really happy
  * if we're using the servlet API, but since we're not,
  * we have this dumb class instead.
  *
  * get returns a random 16 byte string that we're using
  * to pretend we set state in the /authorize request.
  * set and clear are around just to make the interface
  * happy
  */
class DummySessionStore extends DbxSessionStore {

  @BeanProperty
  var token: String = ""

  def get: String = {
    val s = this.getToken()
    s match {
      case "" =>
        val bytes: Array[Byte] =
          Array.fill(16)((Random.nextInt(255) - 128).toByte)
        val encoder = Base64.getEncoder()
        this.setToken(encoder.encodeToString(bytes))
        this.getToken()
      case _ => s
    }
  }
  def set(s: String): Unit = this.setToken(s)
  def clear(): Unit = this.clear
}
