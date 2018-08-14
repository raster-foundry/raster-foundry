package com.azavea.rf.common.utils

import java.security.MessageDigest

object CryptoUtils {
  def sha1(s: String): String = {
    val sha1 = MessageDigest.getInstance("SHA-1")
    sha1.digest(s.getBytes("UTF-8")).map("%02x".format(_)).mkString
  }
}
