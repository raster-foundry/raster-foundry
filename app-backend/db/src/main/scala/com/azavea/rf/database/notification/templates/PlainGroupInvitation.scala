package com.azavea.rf.database.notification.templates

import com.azavea.rf.database._
import com.azavea.rf.datamodel._

import doobie.ConnectionIO

import java.util.UUID

case class PlainGroupInvitation(
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
      | ${subjectEmail} has invited you to join the ${groupType.toString.toLowerCase} ${groupName}.
      |
      | To approve or reject this request, visit {Admin_Link}.
      |
      | If you have questions, support is available via in-app chat at ${platformHost} or less quickly via email to
      | ${platform.publicSettings.emailUser}.
      |
      | -- The ${platform.name} Team
      """.trim.stripMargin
      val richBody = s"""
<html>
  <p>${subjectEmail} has invited you to join the ${groupType.toString.toLowerCase} ${groupName}.
     To approve or reject this request, visit {Admin_Link}.
  </p>
  <p>
    If you have questions, support is available via in-app chat at ${platformHost} or less quickly via email to
    ${platform.publicSettings.emailUser}
  </p>
  <p>
    -- The ${platform.name} team
  </p>
</html>
"""
      val subject = s"Join ${groupName} on ${platform.name}"
      EmailData(subject, plainBody, richBody)
    }
  }
}
