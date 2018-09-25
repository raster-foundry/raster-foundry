package com.azavea.rf.database

import com.azavea.rf.datamodel.FeatureFlag

import doobie._, doobie.implicits._
import doobie.postgres._, doobie.postgres.implicits._
import cats._, cats.data._, cats.effect.IO, cats.implicits._

import java.util.UUID

object FeatureFlagDao extends Dao[FeatureFlag] {

  val tableName = "feature_flags"

  def selectF =
    fr"""
      SELECT
        id, key, active, name, description
      FROM
    """ ++ tableF

}
