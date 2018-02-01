package com.azavea.rf.database.util

import com.azavea.rf.datamodel._

object Ownership {

  def checkOwner(createUser: User, ownerUserId: Option[String]): String = {
    (createUser, ownerUserId) match {
      case (user, Some(id)) if user.id == id => user.id
      case (user, Some(id)) if user.isInRootOrganization => id
      case (user, Some(id)) if !user.isInRootOrganization =>
        throw new IllegalArgumentException("Insufficient permissions to set owner on object")
      case (user, _) => user.id
    }
  }

}
