package com.azavea.rf.datamodel.enums


// Enum to track visibility of database objects
sealed abstract class Visibility(val repr: String)
case object PUBLIC extends Visibility("PUBLIC")
case object ORGANIZATION extends Visibility("ORGANIZATION")
case object PRIVATE extends Visibility("PRIVATE")


object Visibility {
  def fromString(s: String): Visibility = s match {
    case "PUBLIC" => PUBLIC
    case "ORGANIZATION" => ORGANIZATION
    case "PRIVATE" => PRIVATE
  }
  def values = Seq(PUBLIC, ORGANIZATION, PRIVATE)
}


// Enum to track status of asynchronous tasks
sealed abstract class JobStatus(val repr: String)
case object UPLOADING extends JobStatus("UPLOADING")
case object SUCCESS extends JobStatus("SUCCESS")
case object FAILURE extends JobStatus("FAILURE")
case object PARTIALFAILURE extends JobStatus("PARTIALFAILURE")
case object QUEUED extends JobStatus("QUEUED")
case object PROCESSING extends JobStatus("PROCESSING")


object JobStatus {
  def fromString(s: String): JobStatus = s match {
    case "UPLOADING" => UPLOADING
    case "SUCCESS" => SUCCESS
    case "FAILURE" => FAILURE
    case "PARTIALFAILURE" => PARTIALFAILURE
    case "QUEUED" => QUEUED
    case "PROCESSING" => PROCESSING
  }

  def values = Seq(UPLOADING, SUCCESS, PARTIALFAILURE, QUEUED, PROCESSING)
}
