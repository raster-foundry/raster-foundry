package com.azavea.rf.datamodel.enums

/** enum trait for easier custom enum column types
  *
  */
trait Enum[A] {
  trait Value { self: A =>
    _values :+= this
  }
  private var _values = List.empty[A]
  def values = _values map (v => (v.toString, v)) toMap
}

// Enum to track visibility of database objects
object Visibility extends Enum[Visibility]
sealed trait Visibility extends Visibility.Value
case object PUBLIC extends Visibility // viewable by all
case object ORGANIZATION extends Visibility // viewable within organization
case object PRIVATE extends Visibility // viewable only by creator


// Enum to track status of asynchronous tasks
object JobStatus extends Enum[JobStatus]
sealed trait JobStatus extends JobStatus.Value
case object UPLOADING extends JobStatus
case object SUCCESS extends JobStatus
case object FAILURE extends JobStatus
case object PARTIALFAILURE extends JobStatus
case object QUEUED extends JobStatus
case object PROCESSING extends JobStatus

