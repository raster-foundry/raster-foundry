package com.azavea.rf.database

import com.azavea.rf.datamodel._
import com.azavea.rf.datamodel.Generators.Implicits._
import com.azavea.rf.database.Implicits._
import doobie._
import doobie.implicits._
import cats._
import cats.data._
import cats.effect.IO
import cats.syntax.either._
import doobie.postgres._
import doobie.postgres.implicits._
import org.scalacheck.Prop.forAll
import org.scalatest._
import org.scalatest.prop.Checkers
import io.circe._
import io.circe.syntax._
import com.lonelyplanet.akka.http.extensions.PageRequest
import java.util.UUID

class PlatformDaoSpec extends FunSuite with Matchers with Checkers with DBTestConfig with PropTestHelpers {

  test("list platforms") {
    check {
      forAll {
        (pageRequest: PageRequest) => {
          PlatformDao.listPlatforms(pageRequest).transact(xa).unsafeRunSync.results.length >= 0
        }
      }
    }
  }

  test("insert a platform") {
    check {
      forAll {
        (userCreate: User.Create, orgCreate: Organization.Create, platform: Platform) => {
          val insertPlatformIO = for {
            orgAndUser <- insertUserAndOrg(userCreate, orgCreate)
            (_, dbUser) = orgAndUser
            insertedPlatform <- PlatformDao.create(platform)
          } yield { insertedPlatform }

          val dbPlatform = insertPlatformIO.transact(xa).unsafeRunSync

          dbPlatform.name == platform.name &&
            dbPlatform.publicSettings == platform.publicSettings &&
            dbPlatform.privateSettings == platform.privateSettings
        }
      }
    }
  }

  test("update a platform") {
    check {
      forAll {
        (userCreate: User.Create, orgCreate: Organization.Create, platform: Platform, platformUpdate: Platform) => {
          val insertPlatformWithUserIO = for {
            orgAndUser <- insertUserAndOrg(userCreate, orgCreate)
            (_, dbUser) = orgAndUser
            insertedPlatform <- PlatformDao.create(platform)
          } yield { (insertedPlatform, dbUser) }

          val updatePlatformWithPlatformAndAffectedRowsIO = insertPlatformWithUserIO flatMap {
            case (dbPlatform: Platform, dbUser: User) => {
              PlatformDao.update(platformUpdate, dbPlatform.id) flatMap {
                (affectedRows: Int) => {
                  PlatformDao.unsafeGetPlatformById(dbPlatform.id) map { (affectedRows, _) }
                }
              }
            }
          }

        val (affectedRows, updatedPlatform) = updatePlatformWithPlatformAndAffectedRowsIO.transact(xa).unsafeRunSync
          affectedRows == 1 &&
            updatedPlatform.name == platformUpdate.name &&
            updatedPlatform.publicSettings == platformUpdate.publicSettings &&
            updatedPlatform.privateSettings == platformUpdate.privateSettings
        }
      }
    }
  }

  test("delete a platform") {
    check {
      forAll {
        (userCreate: User.Create, orgCreate: Organization.Create, platform: Platform) => {
          val deletePlatformWithPlatformIO = for {
            orgAndUser <- insertUserAndOrg(userCreate, orgCreate)
            (_, dbUser) = orgAndUser
            insertedPlatform <- PlatformDao.create(platform)
            deletePlatform <- PlatformDao.delete(insertedPlatform.id)
            byIdPlatform <- PlatformDao.getPlatformById(insertedPlatform.id)
          } yield { (deletePlatform, byIdPlatform) }

          val (rowsAffected, platformById) = deletePlatformWithPlatformIO.transact(xa).unsafeRunSync
          rowsAffected == 1 && platformById == None
        }
      }
    }
  }

  test("add a user role") {
    check {
      forAll{
        (
          userCreate: User.Create, orgCreate: Organization.Create, platform: Platform,
          userRole: GroupRole
        ) => {
          val addPlatformRoleWithPlatformIO = for {
            orgAndUser <- insertUserAndOrg(userCreate, orgCreate)
                                          (org, dbUser) = orgAndUser
            insertedPlatform <- PlatformDao.create(platform)
            insertedUserGroupRole <- PlatformDao.addUserRole(dbUser, dbUser.id, insertedPlatform.id, userRole)
            byIdUserGroupRole <- UserGroupRoleDao.getOption(insertedUserGroupRole.id)
          } yield { (insertedPlatform, byIdUserGroupRole) }

          val (dbPlatform, dbUserGroupRole) = addPlatformRoleWithPlatformIO.transact(xa).unsafeRunSync
          dbUserGroupRole match {
            case Some(ugr) =>
              assert(ugr.isActive, "; Added role should be active")
              assert(ugr.groupType == GroupType.Platform, "; Added role should be for a Platform")
              assert(ugr.groupId == dbPlatform.id, "; Added role should be for the correct Platform")
              assert(ugr.groupRole == userRole, "; Added role should have the correct role")
              true
            case _ => false
          }
        }
      }
    }
  }

  test("replace a user's roles") {
    check {
      forAll{
        (
          userCreate: User.Create, orgCreate: Organization.Create, platform: Platform,
          userRole: GroupRole
        ) => {
          val setPlatformRoleIO = for {
            orgAndUser <- insertUserAndOrg(userCreate, orgCreate)
            (org, dbUser) = orgAndUser
            insertedPlatform <- PlatformDao.create(platform)
            originalUserGroupRole <- PlatformDao.addUserRole(dbUser, dbUser.id, insertedPlatform.id, userRole)
            updatedUserGroupRoles <- PlatformDao.setUserRole(dbUser, dbUser.id, insertedPlatform.id, userRole)
          } yield { (insertedPlatform, originalUserGroupRole, updatedUserGroupRoles ) }

          val (dbPlatform, dbOldUGR, dbNewUGRs) = setPlatformRoleIO.transact(xa).unsafeRunSync

          assert(dbNewUGRs.filter((ugr) => ugr.isActive == true).size == 1,
                 "; Updated UGRs should have one set to active")
          assert(dbNewUGRs.filter((ugr) => ugr.isActive == false).size == 1,
                 "; Updated UGRs should have one set to inactive")
          assert(dbNewUGRs.filter((ugr) => ugr.id == dbOldUGR.id && ugr.isActive == false).size == 1,
                 "; Old UGR should be set to inactive")
          assert(dbNewUGRs.filter((ugr) => ugr.id != dbOldUGR.id && ugr.isActive == true).size == 1,
                 "; New UGR should be set to active")
          assert(dbNewUGRs.size == 2, "; Update should have old and new UGRs")
          true
        }
      }
    }
  }

  test("deactivate a user's roles") {
    check {
      forAll{
        (
          userCreate: User.Create, orgCreate: Organization.Create, platform: Platform,
          userRole: GroupRole
        ) => {
          val setPlatformRoleIO = for {
            orgAndUser <- insertUserAndOrg(userCreate, orgCreate)
            (org, dbUser) = orgAndUser
            insertedPlatform <- PlatformDao.create(platform)
            originalUserGroupRole <- PlatformDao.addUserRole(dbUser, dbUser.id, insertedPlatform.id, userRole)
            updatedUserGroupRoles <- PlatformDao.deactivateUserRoles(dbUser, dbUser.id, insertedPlatform.id)
          } yield { (insertedPlatform, originalUserGroupRole, updatedUserGroupRoles ) }

          val (dbPlatform, dbOldUGR, dbNewUGRs) = setPlatformRoleIO.transact(xa).unsafeRunSync

          assert(dbNewUGRs.filter((ugr) => ugr.isActive == false).size == 1,
                 "; The updated UGR should be inactive")
          assert(dbNewUGRs.size == 1, "; There should only be a single UGR updated")
          true
        }
      }
    }
  }

  test("get Platform Users And Projests By Consumers And Scene IDs") {
    check {
      forAll{
        (
          userCreate: User.Create,
          userCreateAnother: User.Create,
          orgCreate: Organization.Create,
          platform: Platform,
          projectCreate: Project.Create,
          projectCreateAnother: Project.Create,
          sceneCreate: Scene.Create
        ) => {
          val listOfPwuIO = for {
            userOrgPlatProject <- insertUserOrgPlatProject(userCreate, orgCreate, platform, projectCreate)
            (dbUser, dbOrg, dbPlatform, dbProject) = userOrgPlatProject
            userProjectAnother <- insertUserProject(userCreateAnother, dbOrg, dbPlatform, projectCreateAnother)
            (dbUserAnother, dbProjectAnother) = userProjectAnother
            datasource <- unsafeGetRandomDatasource
            sceneInsert <- SceneDao.insert(fixupSceneCreate(dbUser, datasource, sceneCreate), dbUser)
            _ <- ProjectDao.addScenesToProject(List(sceneInsert.id), dbProject.id)
            _ <- ProjectDao.addScenesToProject(List(sceneInsert.id), dbProjectAnother.id)
            listOfUserIds = List(dbUser.id, dbUserAnother.id)
            listOfPUSP <- PlatformDao.getPlatUsersAndProjByConsumerAndSceneID(listOfUserIds, sceneInsert.id)
          } yield (dbUser, dbUserAnother, dbPlatform, dbProject, dbProjectAnother, listOfPUSP)

          val (dbUser, dbUserAnother, dbPlatform, dbProject, dbProjectAnother, listOfPUSP) = listOfPwuIO.transact(xa).unsafeRunSync

          assert(listOfPUSP.length == 2, "; list of return length is not 2")
          assert(listOfPUSP(0).platId == dbPlatform.id &&
            listOfPUSP(1).platId == dbPlatform.id, "; platform ID don't match")
          assert(listOfPUSP(0).platName == dbPlatform.name &&
            listOfPUSP(1).platName == dbPlatform.name, "; platform name don't match")
          assert((listOfPUSP(0).uId == dbUser.id || listOfPUSP(0).uId == dbUserAnother.id) &&
            (listOfPUSP(1).uId == dbUser.id || listOfPUSP(1).uId == dbUserAnother.id), "; user ID don't match")
          assert((listOfPUSP(0).uName == dbUser.name || listOfPUSP(0).uName == dbUserAnother.name) &&
            (listOfPUSP(1).uName == dbUser.name || listOfPUSP(1).uName == dbUserAnother.name), "; user name don't match")
          assert(listOfPUSP(0).pubSettings == dbPlatform.publicSettings &&
            listOfPUSP(1).pubSettings == dbPlatform.publicSettings, "; platform public settings don't match")
          assert(listOfPUSP(0).priSettings == dbPlatform.privateSettings &&
            listOfPUSP(1).priSettings == dbPlatform.privateSettings, "; platform private settings don't match")
          assert((listOfPUSP(0).email == dbUser.email || listOfPUSP(0).email == dbUserAnother.email) &&
            (listOfPUSP(1).email == dbUser.email || listOfPUSP(1).email == dbUserAnother.email), "; user email don't match")
          assert((listOfPUSP(0).emailNotifications == dbUser.emailNotifications || listOfPUSP(0).emailNotifications == dbUserAnother.emailNotifications) &&
            listOfPUSP(1).emailNotifications == dbUser.emailNotifications || listOfPUSP(1).emailNotifications == dbUserAnother.emailNotifications, "; user email notification don't match")
          assert((listOfPUSP(0).projectId == dbProject.id || listOfPUSP(0).projectId == dbProjectAnother.id) &&
            listOfPUSP(1).projectId == dbProject.id || listOfPUSP(1).projectId == dbProjectAnother.id, "; project ID don't match")
          assert((listOfPUSP(0).projectName == dbProject.name || listOfPUSP(0).projectName == dbProjectAnother.name) &&
            (listOfPUSP(1).projectName == dbProject.name || listOfPUSP(1).projectName == dbProjectAnother.name), "; project name don't match")
          assert((listOfPUSP(0).personalInfo == dbUser.personalInfo || listOfPUSP(0).personalInfo == dbUserAnother.personalInfo) &&
            (listOfPUSP(1).personalInfo == dbUser.personalInfo || listOfPUSP(1).personalInfo == dbUserAnother.personalInfo), "; user personal info don't match")
          true
        }
      }
    }
  }

  test("get Platform And Users By Scene Owner Id") {
    check {
      forAll{
        (
          userCreate: User.Create,
          orgCreate: Organization.Create,
          platform: Platform,
          projectCreate: Project.Create,
          sceneCreate: Scene.Create
        ) => {
          val puIO = for {
            userOrgPlatProject <- insertUserOrgPlatProject(userCreate, orgCreate, platform, projectCreate)
            (dbUser, dbOrg, dbPlatform, dbProject) = userOrgPlatProject
            datasource <- unsafeGetRandomDatasource
            sceneInsert <- SceneDao.insert(fixupSceneCreate(dbUser, datasource, sceneCreate), dbUser)
            _ <- ProjectDao.addScenesToProject(List(sceneInsert.id), dbProject.id)
            pUO <- PlatformDao.getPlatAndUsersBySceneOwnerId(sceneInsert.owner)
          } yield (dbUser, dbPlatform, dbProject, pUO)

          val (dbUser, dbPlatform, dbProject, pU) = puIO.transact(xa).unsafeRunSync

          assert(pU.platId == dbPlatform.id, "; platform ID don't match")
          assert(pU.platName == dbPlatform.name, "; platform name don't match")
          assert(pU.uId == dbUser.id, "; user ID don't match")
          assert(pU.uName == dbUser.name, "; user name don't match")
          assert(pU.pubSettings == dbPlatform.publicSettings, "; platform public settings don't match")
          assert(pU.priSettings == dbPlatform.privateSettings, "; platform private settings don't match")
          assert(pU.email == dbUser.email, "; user email don't match")
          assert(pU.emailNotifications == dbUser.emailNotifications, "; user email notification don't match")
          assert(pU.personalInfo == dbUser.personalInfo, "; user personal info don't match")
          true
        }
      }
    }
  }

  test("list teams a platform user belongs to or can see due to organization memberships") {
    check {
      forAll{
        (
          orgCreate1: Organization.Create,
          orgCreate2: Organization.Create,
          orgCreate3: Organization.Create,
          teamCreate1: Team.Create,
          teamCreate2: Team.Create,
          teamCreate3: Team.Create,
          userCreate: User.Create,
          teamNamePartial: SearchQueryParameters
        ) => {
          val createAndGetTeamsIO =  for {
            // Team# is in Org#
            // User belongs to Org1 and Team1
            orgUserInserted1 <- insertUserAndOrg(userCreate, orgCreate1, true)
            (org1, user) = orgUserInserted1
            teamInsert1 <- TeamDao.create(fixupTeam(
              fixTeamName(teamCreate1, teamNamePartial), org1, user))
            _ <- UserGroupRoleDao.create(
              UserGroupRole.Create(
                user.id,
                GroupType.Team,
                teamInsert1.id,
                GroupRole.Member
              ).toUserGroupRole(user, MembershipStatus.Approved))

            // User belongs to Org2 but not Team2
            org2 <- OrganizationDao.createOrganization(orgCreate2)
            _ <- UserGroupRoleDao.create(
              UserGroupRole.Create(
                user.id,
                GroupType.Organization,
                org2.id,
                GroupRole.Member
              ).toUserGroupRole(user, MembershipStatus.Approved))
            teamInsert2 <- TeamDao.create(fixupTeam(
              fixTeamName(teamCreate2, teamNamePartial), org2, user))

            // User belongs to Team3 but not Org3
            org3 <- OrganizationDao.createOrganization(orgCreate3)
            teamInsert3 <- TeamDao.create(fixupTeam(teamCreate3, org3, user))
            _ <- UserGroupRoleDao.create(
              UserGroupRole.Create(
                user.id,
                GroupType.Team,
                teamInsert3.id,
                GroupRole.Member
              ).toUserGroupRole(user, MembershipStatus.Approved))

            searchedTeams <- PlatformDao.listPlatformUserTeams(user, teamNamePartial)

          } yield (teamInsert1, teamInsert2, teamInsert3, searchedTeams)

          val (teamInsert1, teamInsert2, teamInsert3, searchedTeams) = createAndGetTeamsIO.transact(xa).unsafeRunSync

          val teams = List(teamInsert1, teamInsert2, teamInsert3)

          teamNamePartial.search match {
            case Some(teamName) if teamName.length != 0 =>
              teams.filter(_.name.toUpperCase.contains(teamName.toUpperCase)).length == searchedTeams.length
            case _ => searchedTeams.length == 3
          }
        }
      }
    }
  }
}
