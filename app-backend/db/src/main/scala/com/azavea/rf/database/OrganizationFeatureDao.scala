package com.azavea.rf.database

import com.azavea.rf.database.Implicits._
import com.azavea.rf.datamodel.OrgFeatures

import doobie._, doobie.implicits._
import doobie.postgres._, doobie.postgres.implicits._
import cats._, cats.data._, cats.effect.IO, cats.implicits._

object OrganizationFeatureDao extends Dao[OrgFeatures] {

  val tableName = "organization_features"

  val selectF = sql"""
    SELECT
      organization, feature_flag, active
    FROM
  """ ++ tableF
}
