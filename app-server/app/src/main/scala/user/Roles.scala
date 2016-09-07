package com.azavea.rf.user

object Roles extends Enumeration {
  val User = Role("user")
  val Viewer = Role("viewer")
  val Admin = Role("admin")

  def Role(name: String): Value with Matching =
    new Val(nextId, name) with Matching

  def unapply(s: String): Option[Value] =
    values.find(s == _.toString)

  trait Matching {
    def unapply(s: String): Boolean =
      (s == toString)
  }
}
