package com.azavea.rf.database

import com.azavea.rf.database.meta.RFMeta._
import com.azavea.rf.database.util._
import com.azavea.rf.datamodel._

import doobie._, doobie.implicits._
import doobie.postgres._, doobie.postgres.implicits._
import cats._, cats.data._, cats.effect.IO, cats.implicits._

import scala.concurrent.Future


/**
 * This is abstraction over the listing of arbitrary types from the DB with filters/pagination
 */
trait Dao[Model] {

  implicit val composite: Composite[Model] = implicitly[Composite[Model]]

  val tableName: String

  /** The fragment which holds the associated table's name */
  val tableF = Fragment.const(tableName)

  /** An abstract select statement to be used for constructing queries */
  def selectF: Fragment

  /** A fragment which holds count SQL */
  val countF = (sql"SELECT count(*) FROM" ++ tableF)

}

