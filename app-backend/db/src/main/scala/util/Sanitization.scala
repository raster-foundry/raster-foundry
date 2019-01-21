package com.rasterfoundry.database.util

import com.rasterfoundry.datamodel.{User, Credential}

trait Sanitization {

  private def sanitizeUserContactInfo(user: User): User = {
    user.copy(
      name = Email.obfuscate(user.name),
      email = Email.obfuscate(user.email),
      personalInfo = user.personalInfo.copy(
        email = Email.obfuscate(user.personalInfo.email),
        phoneNumber = ""
      )
    )
  }

  private def sanitizeUserCredentials(user: User): User = {
    user.copy(
      planetCredential = Credential(Some("")),
      dropboxCredential = Credential(Some(""))
    )
  }

  def sanitizeUser(user: User): User = {
    sanitizeUserCredentials(sanitizeUserContactInfo(user))
  }
}
