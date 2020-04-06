package com.rasterfoundry.api.annotationProject

import com.rasterfoundry.api.user.PasswordResetTicket
import com.rasterfoundry.datamodel.AnnotationProject
import com.rasterfoundry.notification.email.Model._

object Notifications {
  def getInvitationMessage(
      sharingUserEmail: String,
      annotationProject: AnnotationProject,
      passwordResetTicket: PasswordResetTicket,
  ): (HtmlBody, PlainBody) = {
    val richBody = HtmlBody(s"""
<html>
  <p>
    <img src="https://groundwork.azavea.com/assets/img/GroundworkBranding-ProductOfAzavea.png" width="400"/>
  </p>
  <p>
    <img src="https://user-images.githubusercontent.com/12401491/73973100-f00d2b80-48ef-11ea-9e37-06e7a41bafc6.gif" width="400"/>
  </p>
  <p>
    ${sharingUserEmail} needs your help! They've invited you to be a collaborator on their project ${annotationProject.name}.
  </p>
  <p>
    <a href="${passwordResetTicket.ticket}">Accept their invitation</a>
  </p>
  <p>
    GroundWork is an image annotation tool designed for working with geospatial data like satellite, drone, and aerial imagery.
  </p>
</html>
""")
    val plainBody = PlainBody(s"""
    | ${sharingUserEmail} needs your help! They've invited you to be a collaborator on their project ${annotationProject.name} in Groundwork.
    |
    | Groundwork is an image annotation tool designed for working with geospatial data like satellite, drone, and aerial imagery.
    |
    | Made by your friends at Azavea.
    | """.trim.stripMargin)
    (richBody, plainBody)
  }
}
