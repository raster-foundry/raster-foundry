package com.azavea.rf.datamodel

sealed abstract class JobStatus(val repr: String)

object JobStatus {
  case object UPLOADING extends JobStatus("UPLOADING")
  case object SUCCESS extends JobStatus("SUCCESS")
  case object FAILURE extends JobStatus("FAILURE")
  case object PARTIALFAILURE extends JobStatus("PARTIALFAILURE")
  case object QUEUED extends JobStatus("QUEUED")
  case object PROCESSING extends JobStatus("PROCESSING")

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


