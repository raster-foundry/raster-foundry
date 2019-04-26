package com.rasterfoundry.database

import com.rasterfoundry.datamodel.OrgFeatures

import doobie.implicits._
import doobie.postgres.implicits._

object OrganizationFeatureDao extends Dao[OrgFeatures] {

  val tableName = "organization_features"

  val selectF = sql"""
    SELECT
      organization, feature_flag, active
    FROM
  """ ++ tableF
}
