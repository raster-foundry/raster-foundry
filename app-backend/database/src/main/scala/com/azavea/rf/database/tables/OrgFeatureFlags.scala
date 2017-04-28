package com.azavea.rf.database.tables

import scala.concurrent.ExecutionContext.Implicits.global
import com.typesafe.scalalogging.LazyLogging
import java.util.UUID

import com.azavea.rf.database.ExtendedPostgresDriver.api._
import com.azavea.rf.database.{Database => DB}
import com.azavea.rf.datamodel.FeatureFlag
import com.azavea.rf.datamodel.OrgFeatures

import scala.concurrent.Future

class OrgFeatureFlags(_tableTag: Tag) extends Table[OrgFeatures](_tableTag, "organization_features") {
  def * = (organization, featureFlag, active) <> (OrgFeatures.tupled, OrgFeatures.unapply)

  val organization: Rep[UUID] = column[UUID]("organization")
  val featureFlag: Rep[UUID] = column[UUID]("feature_flag")
  val active: Rep[Boolean] = column[Boolean]("active")

  val pk = primaryKey("organization_features_pkey", (organization, featureFlag))

  lazy val orgFk = foreignKey("organization_features_org_fkey", organization, Organizations)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.Cascade)
  lazy val flagFk = foreignKey("organization_features_flag_fkey", featureFlag, FeatureFlags)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.Cascade)
}

object OrgFeatureFlags extends TableQuery(tag => new OrgFeatureFlags(tag)) with LazyLogging {
  def getFeatures(orgId: UUID)(implicit database: DB): Future[Seq[FeatureFlag]] = {
    val dbAction =
      for {
        orgFfs <- OrgFeatureFlags.filter(o => o.organization === orgId)
        ffs <- FeatureFlags if (orgFfs.featureFlag === ffs.id)
      } yield (ffs, orgFfs.active)

    database.db.run(dbAction.result) map (results =>
      results map {
        case (ff: FeatureFlag, orgOverride: Boolean) => ff.copy(active = orgOverride)
        case _ => throw new IllegalArgumentException("Invalid arguments for constructor FeatureFlag")
      })
  }
}
