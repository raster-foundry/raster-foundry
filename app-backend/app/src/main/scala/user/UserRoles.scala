package com.azavea.rf.user

object UserRoles extends Enumeration {
  val User = roleValue("user")
  val Viewer = roleValue("viewer")
  val Admin = roleValue("admin")

  def roleValue(name: String): Value with Matching =
    new Val(nextId, name) with Matching

  def unapply(s: String): Option[Value] =
    values.find(s == _.toString)

  trait Matching {
    def unapply(s: String): Boolean =
      (s == toString)
  }
}
