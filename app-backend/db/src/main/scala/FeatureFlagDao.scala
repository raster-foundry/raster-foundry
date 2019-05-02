package com.rasterfoundry.database

import com.rasterfoundry.datamodel.FeatureFlag

import doobie.implicits._
import doobie.postgres.implicits._

object FeatureFlagDao extends Dao[FeatureFlag] {

  val tableName = "feature_flags"

  def selectF =
    fr"""
      SELECT
        id, key, active, name, description
      FROM
    """ ++ tableF

}
