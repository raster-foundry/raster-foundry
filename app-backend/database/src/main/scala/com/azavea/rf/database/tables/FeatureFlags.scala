package com.azavea.rf.database.tables

import com.azavea.rf.database.ExtendedPostgresDriver.api._
import com.azavea.rf.database.{Database => DB}
import java.util.UUID
import com.azavea.rf.datamodel.FeatureFlag
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import com.typesafe.scalalogging.LazyLogging

class FeatureFlags(_tableTag: Tag) extends Table[FeatureFlag](_tableTag, "feature_flags")
{
  def * = (id, key, active, name, description) <> (FeatureFlag.tupled, FeatureFlag.unapply)

  val id: Rep[UUID] = column[UUID]("id", O.PrimaryKey)
  val key: Rep[String] = column[String]("key")
  val active: Rep[Boolean] = column[Boolean]("active")
  val name: Rep[String] = column[String]("name")
  val description: Rep[String] = column[String]("name")
}

object FeatureFlags extends TableQuery(tag => new FeatureFlags(tag)) with LazyLogging {
  type TableQuery = Query[FeatureFlags, FeatureFlags#TableElementType, Seq]

  def listFeatureFlags()(implicit database: DB): Future[Seq[FeatureFlag]] = {
    database.db.run(FeatureFlags.result)
  }

  // This is not a proper API resource so we're not going to add other methods beyond list for now.
}
