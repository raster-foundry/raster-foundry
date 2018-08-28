package com.azavea.rf.database.notification.templates

import java.util.UUID

import com.azavea.rf.database._
import com.azavea.rf.datamodel._
import doobie.ConnectionIO

final case class PlainGroupRequest(
    groupId: UUID,
    groupType: GroupType,
    subjectId: String,
    platformId: UUID
) {
  def build: ConnectionIO[EmailData] = {
    for {
      platform <- PlatformDao.unsafeGetPlatformById(platformId)
      platformHost = platform.publicSettings.platformHost
        .getOrElse("app.rasterfoundry.com")
      subjectEmail <- UserDao
        .unsafeGetUserById(subjectId)
        .map((usr: User) => usr.email)
      groupName <- groupType match {
        case GroupType.Team =>
          TeamDao.unsafeGetTeamById(groupId) map { (team: Team) =>
            team.name
          }
        case GroupType.Organization =>
          OrganizationDao.unsafeGetOrganizationById(groupId) map {
            (org: Organization) =>
              org.name
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
      | To approve or reject this request, visit https://${platformHost}/admin/${groupType.toString.toLowerCase}/${groupId.toString}/users
      |
      | -- The ${platform.name} Team
      """.trim.stripMargin
      val richBody = s"""
<html>
  <p>${subjectEmail} has requested your permission to join the ${groupName} ${groupType.toString.toLowerCase}.
    To approve or reject this request, visit
     <a href="https://${platformHost}/admin/${groupType.toString.toLowerCase}/${groupId.toString}/users">
       your ${groupType.toString.toLowerCase} requests page
     </a>.
  </p>
  <p>
    -- The ${platform.name} Team
  </p>
</html>
      """
      val subject = s"User ${subjectEmail} has requested to join ${groupName}"
      EmailData(subject, plainBody, richBody)
    }
  }
}
