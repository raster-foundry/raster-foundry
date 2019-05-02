package com.rasterfoundry.database.notification.templates

import com.rasterfoundry.database._
import com.rasterfoundry.datamodel._

import doobie.ConnectionIO

import java.util.UUID

final case class PlainGroupInvitation(
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
      | ${subjectEmail} has invited you to join the ${groupType.toString.toLowerCase} ${groupName}.
      |
      | To approve or reject this request, visit https://${platformHost}/user/me/${groupType.toString.toLowerCase}s
      |
      | If you have questions, support is available via in-app chat at ${platformHost} or less quickly via email to
      | ${platform.publicSettings.emailSupport}.
      |
      | -- The ${platform.name} Team
      """.trim.stripMargin
      val richBody = s"""
<html>
  <p>${subjectEmail} has invited you to join the ${groupType.toString.toLowerCase} ${groupName}.
     To approve or reject this invitation, visit
     <a href="https://${platformHost}/user/me/${groupType.toString.toLowerCase}s">
       your ${groupType.toString.toLowerCase} invitations page
     </a>.
  </p>
  <p>
    If you have questions, support is available via in-app chat at ${platformHost} or less quickly via email to
    ${platform.publicSettings.emailSupport}
  </p>
  <p>
    -- The ${platform.name} Team
  </p>
</html>
"""
      val subject = s"Join ${groupName} on ${platform.name}"
      EmailData(subject, plainBody, richBody)
    }
  }
}
