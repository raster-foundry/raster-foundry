package com.azavea.rf.database.tables

import com.azavea.rf.database.query.OrgQueryParameters
import com.azavea.rf.datamodel.Organization
import slick.lifted.ForeignKeyQuery
import com.azavea.rf.database.ExtendedPostgresDriver.api._

trait HasOrganizationFK  { self: Table[_] =>
  def organizationId: Rep[java.util.UUID]
  def organizationsFk: ForeignKeyQuery[Organizations, Organization]
}

object HasOrganizationFK {
  implicit class DefaultQuery[M <: HasOrganizationFK, U, C[_]](that: Query[M, U, Seq]) {
    def filterByOrganization(orgParams: OrgQueryParameters) = {
      if (orgParams.organizations.nonEmpty) {
        that.filter { rec =>
          rec.organizationId inSet orgParams.organizations.toSet
        }
      } else {
        that
      }
    }
  }
}
