package com.azavea.rf.migration.manager

import com.liyaos.forklift.slick.SlickCodegen

trait RFCodegen extends SlickCodegen {
  // Override the package name
  override def pkgName(version: String) = "com.azavea.rf.datamodel." + version + ".schema"

  // Set the models requiring code generation here
  override def tableNames = List(
    "organizations",
    "users"
  )
}
