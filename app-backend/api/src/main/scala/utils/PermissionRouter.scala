package com.azavea.rf.api.utils

import com.azavea.rf.common.Authentication
import com.azavea.rf.database._
import com.azavea.rf.database.Implicits._
import com.azavea.rf.datamodel._

import akka.http.scaladsl.server.Route
import cats.effect.IO
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import doobie.implicits._
import doobie.Transactor

import java.util.UUID

case class PermissionRouter[T](xa: Transactor[IO], dao: Dao[T], objectType: ObjectType) extends Authentication {

  def addPermission(id: UUID): Route = authenticate { user =>
    authorizeAsync {
      dao.query.ownedBy(user, id).exists.transact(xa).unsafeToFuture
    } {
      entity(as[AccessControlRule.Create]) { acrCreate =>
        complete {
          AccessControlRuleDao.createWithResults(
            acrCreate.toAccessControlRule(user, objectType, id)
          ).transact(xa).unsafeToFuture
        }
      }
    }
  }

  def replacePermissions(id: UUID): Route = authenticate { user =>
    authorizeAsync {
      dao.query.ownedBy(user, id).exists.transact(xa).unsafeToFuture
    } {
      entity(as[List[AccessControlRule.Create]]) { acrCreates =>
        complete {
          AccessControlRuleDao.replaceWithResults(
            user, objectType, id, acrCreates
          ).transact(xa).unsafeToFuture
        }
      }
    }
  }

  def listPermissions(id: UUID): Route = authenticate { user =>
    authorizeAsync {
      dao.query.ownedBy(user, id).exists.transact(xa).unsafeToFuture
    } {
      complete {
        AccessControlRuleDao.listByObject(objectType, id).transact(xa).unsafeToFuture
      }
    }
  }
}
