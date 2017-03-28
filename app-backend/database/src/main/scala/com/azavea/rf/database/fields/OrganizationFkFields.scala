package com.azavea.rf.database.fields

import com.azavea.rf.database.ExtendedPostgresDriver.api._
import com.azavea.rf.database.query.OrgQueryParameters
import com.azavea.rf.database.tables.Organizations
import com.azavea.rf.datamodel.{Organization, User}
import slick.lifted.ForeignKeyQuery

trait OrganizationFkFields  { self: Table[_] =>
  def organizationId: Rep[java.util.UUID]
  def organizationsFk: ForeignKeyQuery[Organizations, Organization]
}

object OrganizationFkFields {
  implicit class DefaultQuery[M <: OrganizationFkFields, U, C[_]](that: Query[M, U, Seq]) {
    def filterByOrganization(orgParams: OrgQueryParameters) = {
      if (orgParams.organizations.nonEmpty) {
        that.filter { rec =>
          rec.organizationId inSet orgParams.organizations.toSet
        }
      } else {
        that
      }
    }

    def filterToSharedOrganizationIfNotInRoot(user: User) = {
      if (!user.isInRootOrganization) {
        that.filter(_.organizationId === user.organizationId)
      } else {
        that
      }
    }
  }
}
