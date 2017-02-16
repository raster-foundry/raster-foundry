package com.azavea.rf.common.cache

import net.spy.memcached._
import org.scalatest._

import java.net._


class HeapBackedMemcachedClientSpec extends FunSpec with Matchers {

  it("should throw if passed a string that is too long") {
    // Keys need to be <250 characters long
    val keyTooLong: String = (1 to 250).map(_.toString).foldLeft("")(_ ++ _)
    an [AssertionError] should be thrownBy {
      HeapBackedMemcachedClient.sanitizeKey(keyTooLong)
    }
  }

  it("should throw if passed a string that contains illicit characters") {
    // The Memcached key spec prohibits control characters
    val keyInvalid: String = " ⌫ "
    an [AssertionError] should be thrownBy {
      HeapBackedMemcachedClient.sanitizeKey(keyInvalid)
    }
  }

  it("should not throw if passed a string that contains valid characters") {
    // The Memcached key spec prohibits control characters
    val keyValid: String = "test!"
    noException should be thrownBy {
      HeapBackedMemcachedClient.sanitizeKey(keyValid)
    }
  }

  it("should replace whitespace with underscores") {
    // The Memcached key spec prohibits control characters
    val keyWhiteSpace: String = "This is a test."
    HeapBackedMemcachedClient.sanitizeKey(keyWhiteSpace) shouldEqual ("This_is_a_test.")
  }
}

