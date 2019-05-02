package com.rasterfoundry.database

import com.rasterfoundry.datamodel._

import doobie.implicits._

object LicenseDao extends Dao[License] {

  val tableName = "licenses"

  val selectF =
    fr"""
       SELECT
         short_name, name, url, osi_approved, id
       FROM
      """ ++ tableF
}
