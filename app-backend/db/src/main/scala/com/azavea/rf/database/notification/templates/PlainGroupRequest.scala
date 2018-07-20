package com.azavea.rf.database.notification.templates

import com.azavea.rf.database._
import com.azavea.rf.datamodel._

import doobie.ConnectionIO

import java.util.UUID

case class PlainGroupRequest(
  groupId: UUID,
  groupType: GroupType,
  subjectId: String,
  platform: Platform
) {
  def build: ConnectionIO[EmailData] = {
    val platformHost = platform.publicSettings.platformHost.getOrElse("app.rasterfoundry.com")
    for {
      subjectEmail <- UserDao.unsafeGetUserById(subjectId).map((usr: User) => usr.email)
      groupName <- groupType match {
        case GroupType.Team =>
          TeamDao.unsafeGetTeamById(groupId) map { (team: Team) => team.name }
        case GroupType.Organization =>
          OrganizationDao.unsafeGetOrganizationById(groupId) map {
            (org: Organization) => org.name
          }
        case _ =>
          throw new IllegalArgumentException(
            "Users can only request to add themselves to organizations and teams"
          )
      }
    } yield {
      val plainBody = s"""
      | User ${subjectEmail} has requested your permission to join the ${groupName} ${groupType.toString.toLowerCase}.
      |
      | To approve or reject this request, visit http://${platformHost}/admin/${groupType.toString.toLowerCase}/${groupId.toString}/users
      |
      | -- The ${platform.name} Team
      """.trim.stripMargin
      val richBody = s"""
<html>
  <p>${subjectEmail} has requested your permission to join the ${groupName} ${groupType.toString.toLowerCase}.
    To approve or reject this request, visit
     <a href="http://${platformHost}/admin/${groupType.toString.toLowerCase}/${groupId.toString}/users">
       your ${groupType.toString.toLowerCase} requests page
     </a>.
  </p>
  <p>
    -- The ${platform.name} team
  </p>
</html>
      """
      val subject = s"User ${subjectEmail} has requested to join ${groupName}"
      EmailData(subject, plainBody, richBody)
    }
  }
}
