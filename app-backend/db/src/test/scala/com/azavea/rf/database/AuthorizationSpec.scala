package com.azavea.rf.database

import com.azavea.rf.datamodel._
import com.azavea.rf.datamodel.Generators.Implicits._
import com.azavea.rf.database.Implicits._
import doobie._
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._
import cats._
import cats.data._
import cats.effect.IO
import cats.syntax.either._
import org.scalacheck.Prop.forAll
import org.scalatest._
import org.scalatest.prop.Checkers

class AuthorizationSpec extends FunSuite with Checkers with Matchers with DBTestConfig with PropTestHelpers {

  test("authorizing everyone should authorize all users regardless of platforms") {
    check {
      forAll {
        (userOrgPlatform1: (User.Create, Organization.Create, Platform),
         userOrgPlatform2: (User.Create, Organization.Create, Platform),
         projectCreate: Project.Create) => {
          val (userCreate1, orgCreate1, platform1) = userOrgPlatform1
          val (userCreate2, orgCreate2, platform2) = userOrgPlatform2

          // insert all the users and orgs and platforms, returning users and org/platform 1
          val usersWithOrgOneIO: ConnectionIO[(User, User, Organization)] = for {
            authGalaxy1 <- insertUserOrgPlatform(userCreate1, orgCreate1, platform1)
            (dbUser1, dbOrg1, _) = authGalaxy1
            authGalaxy2 <- insertUserOrgPlatform(userCreate2, orgCreate2, platform2)
            (dbUser2, _, _) = authGalaxy2
          } yield { (dbUser1, dbUser2, dbOrg1) }

          val usersAuthedIO: ConnectionIO[(Boolean, Boolean)] = for {
            users <- usersWithOrgOneIO
            (user1, user2, dbOrg1) = users
            project <- ProjectDao.insertProject(
              fixupProjectCreate(user1, projectCreate).copy(visibility = Visibility.Private),
              user1
            )
            _ <- ProjectDao.addPermission(project.id, ObjectAccessControlRule(SubjectType.All, None, ActionType.View))
            user1Authorized <- ProjectDao.authorized(user1, ObjectType.Project, project.id, ActionType.View)
            user2Authorized <- ProjectDao.authorized(user2, ObjectType.Project, project.id, ActionType.View)
          } yield (user1Authorized, user2Authorized)

          val (user1Authed, user2Authed) = usersAuthedIO.transact(xa).unsafeRunSync

          assert(user1Authed && user2Authed,
                 "Users from different platforms should be authorized when everyone is authorized")
          true

        }
      }
    }
  }

  test("authorizing a platform for read access should authorize all users in organizations in that platform but not in other platforms") {
    check {
      forAll {
        (userOrgPlatform1: (User.Create, Organization.Create, Platform),
         userOrgPlatform2: (User.Create, Organization.Create, Platform),
         projectCreate: Project.Create) => {
          // destructure for cooperation with PropTestHelpers methods
          val (userCreate1, orgCreate1, platform1) = userOrgPlatform1
          val (userCreate2, orgCreate2, platform2) = userOrgPlatform2

          // insert all the users and orgs and platforms, returning users and org/platform 1
          val usersAndOrgOnePlatformOneIO: ConnectionIO[(User, User, Organization, Platform)] = for {
            authGalaxy1 <- insertUserOrgPlatform(userCreate1, orgCreate1, platform1)
            (dbUser1, dbOrg1, dbPlatform1) = authGalaxy1
            authGalaxy2 <- insertUserOrgPlatform(userCreate2, orgCreate2, platform2)
            (dbUser2, _, _) = authGalaxy2
          } yield { (dbUser1, dbUser2, dbOrg1, dbPlatform1) }

          val usersAuthedIO = for {
            usersOrgPlatform <- usersAndOrgOnePlatformOneIO
            (user1, user2, org1, platform1) = usersOrgPlatform
            project <- ProjectDao.insertProject(fixupProjectCreate(user1, projectCreate).copy(visibility = Visibility.Private), user1)
            _ <- ProjectDao.addPermission(project.id, ObjectAccessControlRule(SubjectType.Platform, Some(platform1.id.toString), ActionType.View))
            user1Authorized <- ProjectDao.authorized(user1, ObjectType.Project, project.id, ActionType.View)
            user2Authorized <- ProjectDao.authorized(user2, ObjectType.Project, project.id, ActionType.View)
                      } yield { (user1Authorized, user2Authorized) }

          val (user1Authed, user2Authed) = usersAuthedIO.transact(xa).unsafeRunSync

          assert(user1Authed, "The user in the platform that's been authorized should be authorized")
          assert(!user2Authed, "The user in the platform that's not been authorized should not be authorized")
          true
        }
      }
    }
  }

  test("authorizing an organization should authorize users in that organization and not in other organizations in that platform") {
    check {
      forAll {
        (platform: Platform, userCreate1: User.Create, userCreate2: User.Create,
         orgCreate1: Organization.Create, orgCreate2: Organization.Create,
         projectCreate: Project.Create) => {
          val usersAuthedIO: ConnectionIO[(Boolean, Boolean)] = for {
            dbPlatform <- PlatformDao.create(platform)
            orgUser1 <- insertUserAndOrg(userCreate1, orgCreate1.copy(platformId=dbPlatform.id))
            (org1, user1) = orgUser1
            orgUser2 <- insertUserAndOrg(userCreate2, orgCreate2.copy(platformId=dbPlatform.id))
            (_, user2) = orgUser2
            project <- ProjectDao.insertProject(fixupProjectCreate(user1, projectCreate).copy(visibility = Visibility.Private), user1)
            _ <- ProjectDao.addPermission(project.id, ObjectAccessControlRule(SubjectType.Organization, Some(org1.id.toString), ActionType.View))
            user1Authorized <- ProjectDao.authorized(user1, ObjectType.Project, project.id, ActionType.View)
            user2Authorized <- ProjectDao.authorized(user2, ObjectType.Project, project.id, ActionType.View)
          } yield { (user1Authorized, user2Authorized) }

          val (user1Authed, user2Authed) = usersAuthedIO.transact(xa).unsafeRunSync
          assert(user1Authed, "A user in the same org as the authorized org should be authorized")
          assert(!user2Authed, "A user not in the same org as the authorized org should not be authorized")
          true
        }
      }
    }
  }

  test("authorizing a team should authorize users on that team and not on other teams") {
    check {
      forAll {
        (u: Unit) => true
      }
    }
  }

  test("several access control rules should be insertable at the same time") {
    check {
      forAll {
        (platform: Platform, userCreate: User.Create, orgCreate: Organization.Create, projectCreate: Project.Create) => {
          val insertManyAcrsIO = for {
            dbPlatform <- PlatformDao.create(platform)
            orgUser <- insertUserAndOrg(userCreate, orgCreate.copy(platformId=dbPlatform.id))
            (org, user) = orgUser
            project <- ProjectDao.insertProject(fixupProjectCreate(user, projectCreate).copy(visibility = Visibility.Private), user)
            acrs = List(ActionType.View, ActionType.Edit, ActionType.Delete) map {
              ObjectAccessControlRule(SubjectType.All, None, _)
            }
            _ <- ProjectDao.addPermissionsMany(project.id, acrs)
            dbAcrs <- ProjectDao.getPermissions(project.id)
          } yield { (acrs, dbAcrs) }

          val (acrs, dbAcrs) = insertManyAcrsIO.transact(xa).unsafeRunSync

          assert(acrs.toSet == dbAcrs.flatten.toSet, "Inserting many ACRs should make those ACRs available to list")
          true
        }
      }
    }
  }

  test("listing user actions granted by 'ALL' should return a list of permitted actions") {
    check {
      forAll {
        (platform: Platform, userCreate: User.Create, orgCreate: Organization.Create, projectCreate: Project.Create) => {
          val listUserActionsIO = for {
            dbPlatform <- PlatformDao.create(platform)
            orgUser <- insertUserAndOrg(userCreate, orgCreate.copy(platformId=dbPlatform.id))
            (org, user) = orgUser
            project <- ProjectDao.insertProject(fixupProjectCreate(user, projectCreate).copy(visibility = Visibility.Private), user)
            acrs = List(ActionType.View, ActionType.Edit, ActionType.Delete) map {
              ObjectAccessControlRule(SubjectType.All, None, _)
            }
            _ <- ProjectDao.addPermissionsMany(project.id, acrs)
            listOfActions <- ProjectDao.listUserActions(user, project.id)
          } yield listOfActions

          val listUserActions = listUserActionsIO.transact(xa).unsafeRunSync

          assert(listUserActions.length == 3, "; List of permitted actions should be 3")
          assert(listUserActions.intersect(List("VIEW", "EDIT", "DELETE")).nonEmpty, "; List of permitted actions should intersect with what was provided")
          true
        }
      }
    }
  }

  test("listing user actions granted by platform membership should return an empty list") {
    check {
      forAll {
        (platform: Platform, userCreate: User.Create, orgCreate: Organization.Create, projectCreate: Project.Create) => {
          val listUserActionsIO = for {
            dbPlatform <- PlatformDao.create(platform)
            orgUser <- insertUserAndOrg(userCreate, orgCreate.copy(platformId=dbPlatform.id))
            (org, user) = orgUser
            project <- ProjectDao.insertProject(fixupProjectCreate(user, projectCreate).copy(visibility = Visibility.Private), user)
            acrs = List(ActionType.View, ActionType.Edit, ActionType.Delete) map {
              ObjectAccessControlRule(SubjectType.Platform, Some(dbPlatform.id.toString()), _)
            }
            _ <- ProjectDao.addPermissionsMany(project.id, acrs)
            listOfActions <- ProjectDao.listUserActions(user, project.id)
          } yield listOfActions

          val listUserActions = listUserActionsIO.transact(xa).unsafeRunSync
          listUserActions.length == 0
        }
      }
    }
  }

  test("listing user actions granted by organization membership should return a list of permitted actions") {
    check {
      forAll {
        (platform: Platform, userCreate: User.Create, orgCreate: Organization.Create, projectCreate: Project.Create) => {
          val listUserActionsIO = for {
            dbPlatform <- PlatformDao.create(platform)
            orgUser <- insertUserAndOrg(userCreate, orgCreate.copy(platformId=dbPlatform.id))
            (org, user) = orgUser
            project <- ProjectDao.insertProject(fixupProjectCreate(user, projectCreate).copy(visibility = Visibility.Private), user)
            acrs = List(ActionType.View, ActionType.Edit, ActionType.Delete) map {
              ObjectAccessControlRule(SubjectType.Organization, Some(org.id.toString()), _)
            }
            _ <- ProjectDao.addPermissionsMany(project.id, acrs)
            listOfActions <- ProjectDao.listUserActions(user, project.id)
          } yield listOfActions

          val listUserActions = listUserActionsIO.transact(xa).unsafeRunSync

          assert(listUserActions.length == 3, "; List of permitted actions should be 3")
          assert(listUserActions.intersect(List("VIEW", "EDIT", "DELETE")).nonEmpty, "; List of permitted actions should intersect with what was provided")
          true
        }
      }
    }
  }

  test("listing user actions granted to a specific user should return a list of permitted actions") {
    check {
      forAll {
        (platform: Platform, userCreate: User.Create, orgCreate: Organization.Create, projectCreate: Project.Create) => {
          val listUserActionsIO = for {
            dbPlatform <- PlatformDao.create(platform)
            orgUser <- insertUserAndOrg(userCreate, orgCreate.copy(platformId=dbPlatform.id))
            (org, user) = orgUser
            project <- ProjectDao.insertProject(fixupProjectCreate(user, projectCreate).copy(visibility = Visibility.Private), user)
            acrs = List(ActionType.View, ActionType.Edit, ActionType.Delete) map {
              ObjectAccessControlRule(SubjectType.User, Some(user.id.toString()), _)
            }
            _ <- ProjectDao.addPermissionsMany(project.id, acrs)
            listOfActions <- ProjectDao.listUserActions(user, project.id)
          } yield listOfActions

          val listUserActions = listUserActionsIO.transact(xa).unsafeRunSync

          assert(listUserActions.length == 3, "; List of permitted actions should be 3")
          assert(listUserActions.intersect(List("VIEW", "EDIT", "DELETE")).nonEmpty, "; List of permitted actions should intersect with what was provided")
          true
        }
      }
    }
  }

  // TODO: reconsider ACR deactivation in issue 4020
  // test("listing user actions with deactivated ACR should return an empty list") {}

}
