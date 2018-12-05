package com.rasterfoundry.database.util

object Email {
  def obfuscate(email: String): String = email.split("@").headOption match {
    case Some(emailName) if emailName.length != 0 => emailName
    case _                                        => "Anonymous"
  }
}
