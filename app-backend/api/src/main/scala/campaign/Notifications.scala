package com.rasterfoundry.api.campaign

import com.rasterfoundry.api.user.PasswordResetTicket
import com.rasterfoundry.datamodel.Campaign
import com.rasterfoundry.notification.email.Model._

object Notifications {
  def getInvitationMessage(
      sharingUserEmail: String,
      campaign: Campaign,
      passwordResetTicket: PasswordResetTicket
  ): (HtmlBody, PlainBody) = {
    val richBody = HtmlBody(s"""
<html>
  <head>
    <style type="text/css">
      @import url(https://use.typekit.net/yrj5flr.css);.ExternalClass,.ExternalClass div,.ExternalClass font,.ExternalClass p,.ExternalClass span,.ExternalClass td,img{line-height:100%}#outlook a{padding:0}.ExternalClass,.ReadMsgBody{width:100%}a,blockquote,body,li,p,table,td{-webkit-text-size-adjust:100%;-ms-text-size-adjust:100%}table,td{mso-table-lspace:0;mso-table-rspace:0}img{-ms-interpolation-mode:bicubic;border:0;height:auto;outline:0;text-decoration:none}table{border-collapse:collapse!important}#bodyCell,#bodyTable,body{height:100%!important;margin:0;padding:0;font-family:proxima-nova,sans-serif}#bodyCell{padding:20px}#bodyTable{width:600px}@media only screen and (max-width:480px){#bodyTable,body{width:100%!important}a,blockquote,body,li,p,table,td{-webkit-text-size-adjust:none!important}body{min-width:100%!important}#bodyTable{max-width:600px!important}#signIn{max-width:280px!important}}
    </style>
  </head>
  <body>
    <center>
      <table
        style='width: 600px;-webkit-text-size-adjust: 100%;-ms-text-size-adjust: 100%;mso-table-lspace: 0pt;mso-table-rspace: 0pt;margin: 0;padding: 0;font-family: proxima-nova, sans-serif;border-collapse: collapse !important;height: 100% !important;'
        align="center"
        border="0"
        cellpadding="0"
        cellspacing="0"
        height="100%"
        width="100%"
        id="bodyTable"
      >
        <tr>
          <td
            align="center"
            valign="top"
            id="bodyCell"
            style='-webkit-text-size-adjust: 100%;-ms-text-size-adjust: 100%;mso-table-lspace: 0pt;mso-table-rspace: 0pt;margin: 0;padding: 20px;font-family: proxima-nova, sans-serif;;height: 100% !important;'
          >
            <div class="main">
              <p
                style="text-align: center;-webkit-text-size-adjust: 100%;-ms-text-size-adjust: 100%; margin-bottom: 30px;"
              >
                <img
                  src="https://groundwork.azavea.com/assets/img/groundwork_logo_email_2x.png"
                  width="200"
                  alt="GroundWork"
                  style="-ms-interpolation-mode: bicubic;border: 0;height: auto;line-height: 100%;outline: none;text-decoration: none;"
                />
              </p>

              <h1>Collaboration request</h1>

              <p>
                <strong>${sharingUserEmail}</strong> needs your help! They've invited you to be a collaborator on their labeling campaign <strong>${campaign.name}</strong>
              </p>

              <p>
                <a href="${passwordResetTicket.ticket}">Create an account and accept their invitation.</a>
              </p>

              <p>GroundWork is an image annotation tool designed for working with geospatial data like satellite, drone, and aerial imagery.</p>

              <br /><br />
              <hr style="border: 2px solid #EAEEF3; border-bottom: 0; margin: 20px 0;" />
              <p style="text-align: center;color: #A9B3BC;-webkit-text-size-adjust: 100%;-ms-text-size-adjust: 100%;">
                If you do not know ${sharingUserEmail}, please ignore this request.
              </p>
            </div>
          </td>
        </tr>
      </table>
    </center>
  </body>
</html>
""")
    val plainBody = PlainBody(s"""
    | ${sharingUserEmail} needs your help! They've invited you to be a collaborator on their labeling campaign ${campaign.name} in Groundwork.
    |
    | Groundwork is an image annotation tool designed for working with geospatial data like satellite, drone, and aerial imagery.
    |
    | Made by your friends at Azavea.
    | """.trim.stripMargin)
    (richBody, plainBody)
  }
}
