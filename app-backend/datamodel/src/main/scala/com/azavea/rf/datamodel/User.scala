package com.azavea.rf.datamodel

import spray.json._
import spray.json.DefaultJsonProtocol._
import java.sql.Timestamp
import java.util.UUID

case class User(id: String)


object User {

  def create = Create.apply _

  implicit val defaultUserFormat = jsonFormat1(User.apply _)

  sealed abstract class Role(val repr: String) extends Product with Serializable
  case object Viewer extends Role("VIEWER")
  case object Owner extends Role("OWNER")
  case object Admin extends Role("ADMIN")

  object Role {
    def fromString(s: String) = s.toUpperCase match {
      case "VIEWER" => Viewer
      case "OWNER" => Owner
      case "ADMIN" => Admin
      case _ => throw new Exception(s"Unsupported user role string: $s")
    }

    implicit object DefaultJobStatusJsonFormat extends RootJsonFormat[Role] {
      def write(role: Role): JsValue = JsString(role.toString)
      def read(js: JsValue): Role = js match {
        case JsString(js) => fromString(js)
        case _ =>
          deserializationError("Failed to parse thumbnail size string representation (${js}) to JobStatus")
      }
    }
  }

  case class Create(
    id: String,
    organizationId: UUID,
    role: Role = Viewer
  ) {
    def toUsersOrgTuple(): (User, User.ToOrganization)= {
      val now = new Timestamp((new java.util.Date()).getTime())
      val newUUID = java.util.UUID.randomUUID
      val user = User(id)

      val userToOrg = User.ToOrganization(
        userId=id,
        organizationId=organizationId,
        role,
        now,
        now
      )
      (user, userToOrg)
    }
  }

  object Create {
    implicit val defaultUserCreateFormat = jsonFormat3(Create.apply _)
  }

  case class ToOrganization(
    userId: String,
    organizationId: UUID,
    role: User.Role,
    createdAt: Timestamp,
    modifiedAt: Timestamp
  )
  object ToOrganization {
    def tupled = (ToOrganization.apply _).tupled
    implicit val defaultUserToOrgFormat = jsonFormat5(ToOrganization.apply _)
  }

  case class WithRole(id: String, role: User.Role, createdAt: Timestamp, modifiedAt: Timestamp)
  object WithRole {
    def tupled = (WithRole.apply _).tupled
    implicit val defaultUserWithRoleFormat = jsonFormat4(WithRole.apply _)
  }

  case class WithRoleCreate(id: String, role: User.Role) {
    def toUserWithRole(): User.WithRole = {
      val now = new Timestamp((new java.util.Date()).getTime())
      User.WithRole(id, role, now, now)
    }
  }
  object WithRoleCreate {
    def tupled = (WithRoleCreate.apply _).tupled
    implicit val defaultUserWithRoleCreateFormat = jsonFormat2(WithRoleCreate.apply _)
  }

  // join between role and org
  case class RoleOrgJoin(userId: String, orgId: java.util.UUID, orgName: String, userRole: User.Role)
  object RoleOrgJoin {
    def tupled = (RoleOrgJoin.apply _).tupled
    implicit val defaultUserWithRoleFormat = jsonFormat4(RoleOrgJoin.apply _)
  }

  case class WithOrgs(id: String, organizations: Seq[Organization.WithRole])
  object WithOrgs {
    implicit val defaultUserWithOrgsFormat = jsonFormat2(WithOrgs.apply _)
  }
}


