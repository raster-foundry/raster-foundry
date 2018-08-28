package com.azavea.rf.database.util

import com.azavea.rf.datamodel._
import com.azavea.rf.database.filter.Filterables

import doobie._
import doobie.implicits._

object Ownership extends Filterables {

  def checkOwner(createUser: User, ownerUserId: Option[String]): String = {
    (createUser, ownerUserId) match {
      case (user, Some(id)) if user.id == id    => user.id
      case (user, Some(id)) if user.isSuperuser => id
      case (user, Some(id)) if !user.isSuperuser =>
        throw new IllegalArgumentException(
          "Insufficient permissions to set owner on object")
      case (user, _) => user.id
    }
  }
}
