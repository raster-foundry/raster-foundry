package com.azavea.rf.database.fields

import com.azavea.rf.database.ExtendedPostgresDriver.api._
import com.azavea.rf.datamodel.{Organization, Visibility}

trait OrgFkVisibleFields extends OrganizationFkFields with VisibilityField { self: Table[_] => }

object OrgFkVisibleFields {
  implicit class DefaultQuery[M <: OrgFkVisibleFields, U, C[_]](that: Query[M, U, Seq]) {
    def filterOrgVisibility(org: Organization) = {
      that.filter { rec => {
          rec.visibility === Visibility.fromString("PUBLIC") || rec.organizationId === org.id
        }
      }
    }
  }
}
