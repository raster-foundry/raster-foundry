package com.azavea.rf.datamodel


// Enum to track visibility of database objects
sealed abstract class Visibility(val repr: String)

object Visibility {
  case object PUBLIC extends Visibility("PUBLIC")
  case object ORGANIZATION extends Visibility("ORGANIZATION")
  case object PRIVATE extends Visibility("PRIVATE")

  def fromString(s: String): Visibility = s match {
    case "PUBLIC" => PUBLIC
    case "ORGANIZATION" => ORGANIZATION
    case "PRIVATE" => PRIVATE
  }
  def values = Seq(PUBLIC, ORGANIZATION, PRIVATE)
}







