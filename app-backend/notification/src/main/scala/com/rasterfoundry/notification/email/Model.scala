package com.rasterfoundry.notification.email

import io.estatico.newtype.macros.newtype

object Model {

  sealed abstract class EncryptionScheme(val repr: String) {
    override def toString: String = repr
  }
  object EncryptionScheme {
    def fromStringE(s: String) = s.toLowerCase match {
      case "ssl"      => Right(SSL)
      case "tls"      => Right(TLS)
      case "starttls" => Right(StartTLS)
      case s =>
        Left(
          s"$s is not a valid encryption scheme. Must be in ssl, tls, or starttls"
        )
    }
  }
  case object SSL extends EncryptionScheme("ssl")
  case object TLS extends EncryptionScheme("tls")
  case object StartTLS extends EncryptionScheme("starttls")

  @newtype case class EmailHost(underlying: String)
  @newtype case class EmailPort(underlying: Int)

  @newtype case class EmailUserName(underlying: String)
  @newtype case class EmailPassword(underlying: String)
  @newtype case class FromEmailAddress(underlying: String)
  @newtype case class ToEmailAddress(underlying: String)

  @newtype case class FromEmailDisplayName(underlying: String)
  @newtype case class Subject(underlying: String)
  @newtype case class HtmlBody(underlying: String)
  @newtype case class PlainBody(underlying: String)

  final case class EmailConfig(
      host: EmailHost,
      port: EmailPort,
      encryption: EncryptionScheme,
      username: EmailUserName,
      password: EmailPassword
  )

  final case class EmailSettings(
      config: EmailConfig,
      fromUserEmail: FromEmailAddress,
      toUserEmail: ToEmailAddress
  )
}
