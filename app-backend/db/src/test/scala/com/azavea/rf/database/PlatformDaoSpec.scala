package com.rasterfoundry.database

import com.rasterfoundry.common.Generators.Implicits._
import com.rasterfoundry.datamodel.PageRequest
import com.rasterfoundry.datamodel._

import com.typesafe.scalalogging.LazyLogging
import doobie.implicits._
import org.scalacheck.Prop.forAll
import org.scalatest._
import org.scalatestplus.scalacheck.Checkers

class PlatformDaoSpec
    extends FunSuite
    with Matchers
    with Checkers
    with DBTestConfig
    with LazyLogging
    with PropTestHelpers {

  test("list platforms") {
    check {
      forAll { pageRequest: PageRequest =>
        {
          PlatformDao
            .listPlatforms(pageRequest)
            .transact(xa)
            .unsafeRunSync
            .results
            .length >= 0
        }
      }
    }
  }

  test("insert a platform") {
    check {
      forAll { platform: Platform =>
        {
          val insertPlatformIO = PlatformDao.create(platform)
          val dbPlatform =
            insertPlatformIO.transact(xa).unsafeRunSync

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
        (
            userCreate: User.Create,
            orgCreate: Organization.Create,
            platform: Platform,
            platformUpdate: Platform
        ) =>
          {
            val platformUpdateIO = for {
              (_, _, dbPlatform) <- insertUserOrgPlatform(
                userCreate,
                orgCreate,
                platform
              )
              affectedRows <- PlatformDao.update(platformUpdate, dbPlatform.id)
              fetched <- PlatformDao.unsafeGetPlatformById(dbPlatform.id)
            } yield { (affectedRows, fetched) }

            val (affectedRows, updatedPlatform) =
              platformUpdateIO
                .transact(xa)
                .unsafeRunSync
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
      forAll { platform: Platform =>
        {
          val deletePlatformWithPlatformIO = for {
            insertedPlatform <- PlatformDao.create(platform)
            deletePlatform <- PlatformDao.delete(insertedPlatform.id)
            byIdPlatform <- PlatformDao.getPlatformById(insertedPlatform.id)
          } yield { (deletePlatform, byIdPlatform) }

          val (rowsAffected, platformById) =
            deletePlatformWithPlatformIO.transact(xa).unsafeRunSync
          rowsAffected == 1 && platformById.isEmpty
        }
      }
    }
  }

  test("add a user role") {
    check {
      forAll {
        (
            userCreate: User.Create,
            orgCreate: Organization.Create,
            platform: Platform,
            userRole: GroupRole
        ) =>
          {
            val addPlatformRoleWithPlatformIO = for {
              (dbUser, _, dbPlatform) <- insertUserOrgPlatform(
                userCreate,
                orgCreate,
                platform,
                doUserGroupRole = false
              )
              insertedUserGroupRole <- PlatformDao.addUserRole(
                dbUser,
                dbUser.id,
                dbPlatform.id,
                userRole
              )
              byIdUserGroupRole <- UserGroupRoleDao.getOption(
                insertedUserGroupRole.id
              )
            } yield { (dbPlatform, byIdUserGroupRole) }

            val (dbPlatform, dbUserGroupRole) =
              addPlatformRoleWithPlatformIO.transact(xa).unsafeRunSync
            dbUserGroupRole match {
              case Some(ugr) =>
                assert(ugr.isActive, "; Added role should be active")
                assert(
                  ugr.groupType == GroupType.Platform,
                  "; Added role should be for a Platform"
                )
                assert(
                  ugr.groupId == dbPlatform.id,
                  "; Added role should be for the correct Platform"
                )
                assert(
                  ugr.groupRole == userRole,
                  "; Added role should have the correct role"
                )
                true
              case _ => false
            }
          }
      }
    }
  }

  test("replace a user's roles") {
    check {
      forAll {
        (
            userCreate: User.Create,
            orgCreate: Organization.Create,
            platform: Platform,
            userRole: GroupRole
        ) =>
          {
            val setPlatformRoleIO = for {
              (dbUser, _, insertedPlatform) <- insertUserOrgPlatform(
                userCreate,
                orgCreate,
                platform,
                doUserGroupRole = false
              )
              originalUserGroupRole <- PlatformDao.addUserRole(
                dbUser,
                dbUser.id,
                insertedPlatform.id,
                userRole
              )
              updatedUserGroupRoles <- PlatformDao.setUserRole(
                dbUser,
                dbUser.id,
                insertedPlatform.id,
                userRole
              )
            } yield {
              (insertedPlatform, originalUserGroupRole, updatedUserGroupRoles)
            }

            val (_, dbOldUGR, dbNewUGRs) =
              setPlatformRoleIO.transact(xa).unsafeRunSync

            assert(
              dbNewUGRs.count(ugr => ugr.isActive) == 1,
              "; Updated UGRs should have one set to active"
            )
            assert(
              dbNewUGRs.count(ugr => !ugr.isActive) == 1,
              "; Updated UGRs should have one set to inactive"
            )
            assert(
              dbNewUGRs
                .count(ugr => ugr.id == dbOldUGR.id && !ugr.isActive) == 1,
              "; Old UGR should be set to inactive"
            )
            assert(
              dbNewUGRs
                .count(ugr => ugr.id != dbOldUGR.id && ugr.isActive) == 1,
              "; New UGR should be set to active"
            )
            assert(dbNewUGRs.size == 2, "; Update should have old and new UGRs")
            true
          }
      }
    }
  }

  test("deactivate a user's roles") {
    check {
      forAll {
        (
            userCreate: User.Create,
            orgCreate: Organization.Create,
            platform: Platform,
            userRole: GroupRole
        ) =>
          {
            val setPlatformRoleIO = for {
              (dbUser, _, insertedPlatform) <- insertUserOrgPlatform(
                userCreate,
                orgCreate,
                platform,
                doUserGroupRole = false
              )
              originalUserGroupRole <- PlatformDao.addUserRole(
                dbUser,
                dbUser.id,
                insertedPlatform.id,
                userRole
              )
              updatedUserGroupRoles <- PlatformDao.deactivateUserRoles(
                dbUser.id,
                insertedPlatform.id
              )
            } yield {
              (insertedPlatform, originalUserGroupRole, updatedUserGroupRoles)
            }

            val (_, _, dbNewUGRs) =
              setPlatformRoleIO.transact(xa).unsafeRunSync

            assert(
              dbNewUGRs.count(ugr => !ugr.isActive) == 1,
              "; The updated UGR should be inactive"
            )
            assert(
              dbNewUGRs.size == 1,
              "; There should only be a single UGR updated"
            )
            true
          }
      }
    }
  }

  test("get Platform Users And Projects By Consumers And Scene IDs") {
    check {
      forAll {
        (
            userCreate: User.Create,
            userCreateAnother: User.Create,
            orgCreate: Organization.Create,
            platform: Platform,
            platform2: Platform,
            projectCreate: Project.Create,
            projectCreateAnother: Project.Create,
            sceneCreate: Scene.Create
        ) =>
          {
            val listOfPwuIO = for {
              userOrgPlatProject <- insertUserOrgPlatProject(
                userCreate,
                orgCreate,
                platform,
                projectCreate
              )
              (dbUser, dbOrg, dbPlatform, dbProject) = userOrgPlatProject
              secondPlatform <- PlatformDao.create(platform2)
              deactivatedRole <- PlatformDao.addUserRole(
                dbUser,
                dbUser.id,
                secondPlatform.id,
                GroupRole.Member
              )
              _ <- UserGroupRoleDao.deactivate(deactivatedRole.id)
              userProjectAnother <- insertUserProject(
                userCreateAnother,
                dbOrg,
                dbPlatform,
                projectCreateAnother
              )
              (dbUserAnother, dbProjectAnother) = userProjectAnother
              datasource <- unsafeGetRandomDatasource
              sceneInsert <- SceneDao.insert(
                fixupSceneCreate(dbUser, datasource, sceneCreate),
                dbUser
              )
              _ <- ProjectDao.addScenesToProject(
                List(sceneInsert.id),
                dbProject.id,
                dbProject.defaultLayerId
              )
              numScenesAdded <- ProjectDao.addScenesToProject(
                List(sceneInsert.id),
                dbProjectAnother.id,
                dbProjectAnother.defaultLayerId
              )
              _ = logger.trace(s"Added $numScenesAdded scenes")
              listOfUserIds = List(dbUser.id, dbUserAnother.id)
              listOfPUSP <- PlatformDao.getPlatUsersAndProjByConsumerAndSceneID(
                listOfUserIds,
                sceneInsert.id
              )
            } yield
              (
                dbUser,
                dbUserAnother,
                dbPlatform,
                dbProject,
                dbProjectAnother,
                listOfPUSP
              )

            val (
              dbUser,
              dbUserAnother,
              dbPlatform,
              dbProject,
              dbProjectAnother,
              listOfPUSP
            ) =
              listOfPwuIO.transact(xa).unsafeRunSync

            assert(listOfPUSP.length == 2, "; list of return length is not 2")
            assert(
              listOfPUSP.head.platId == dbPlatform.id &&
                listOfPUSP(1).platId == dbPlatform.id,
              "; platform ID don't match"
            )
            assert(
              listOfPUSP.head.platName == dbPlatform.name &&
                listOfPUSP(1).platName == dbPlatform.name,
              "; platform name don't match"
            )
            assert(
              (listOfPUSP.head.uId == dbUser.id || listOfPUSP.head.uId == dbUserAnother.id) &&
                (listOfPUSP(1).uId == dbUser.id || listOfPUSP(1).uId == dbUserAnother.id),
              "; user ID don't match"
            )
            assert(
              (listOfPUSP.head.uName == dbUser.name || listOfPUSP.head.uName == dbUserAnother.name) &&
                (listOfPUSP(1).uName == dbUser.name || listOfPUSP(1).uName == dbUserAnother.name),
              "; user name don't match"
            )
            assert(
              listOfPUSP.head.pubSettings == dbPlatform.publicSettings &&
                listOfPUSP(1).pubSettings == dbPlatform.publicSettings,
              "; platform public settings don't match"
            )
            assert(
              listOfPUSP.head.priSettings == dbPlatform.privateSettings &&
                listOfPUSP(1).priSettings == dbPlatform.privateSettings,
              "; platform private settings don't match"
            )
            assert(
              (listOfPUSP.head.email == dbUser.email || listOfPUSP.head.email == dbUserAnother.email) &&
                (listOfPUSP(1).email == dbUser.email || listOfPUSP(1).email == dbUserAnother.email),
              "; user email don't match"
            )
            assert(
              (listOfPUSP.head.emailNotifications == dbUser.emailNotifications || listOfPUSP.head.emailNotifications == dbUserAnother.emailNotifications) &&
                listOfPUSP(1).emailNotifications == dbUser.emailNotifications || listOfPUSP(
                1
              ).emailNotifications == dbUserAnother.emailNotifications,
              "; user email notification don't match"
            )
            assert(
              (listOfPUSP.head.projectId == dbProject.id || listOfPUSP.head.projectId == dbProjectAnother.id) &&
                listOfPUSP(1).projectId == dbProject.id || listOfPUSP(1).projectId == dbProjectAnother.id,
              "; project ID don't match"
            )
            assert(
              (listOfPUSP.head.projectName == dbProject.name || listOfPUSP.head.projectName == dbProjectAnother.name) &&
                (listOfPUSP(1).projectName == dbProject.name || listOfPUSP(1).projectName == dbProjectAnother.name),
              "; project name don't match"
            )
            assert(
              (listOfPUSP.head.personalInfo == dbUser.personalInfo || listOfPUSP.head.personalInfo == dbUserAnother.personalInfo) &&
                (listOfPUSP(1).personalInfo == dbUser.personalInfo || listOfPUSP(
                  1
                ).personalInfo == dbUserAnother.personalInfo),
              "; user personal info don't match"
            )
            true
          }
      }
    }
  }

  test("get Platform And Users By Scene Owner Id") {
    check {
      forAll {
        (
            userCreate: User.Create,
            orgCreate: Organization.Create,
            platform: Platform,
            projectCreate: Project.Create,
            sceneCreate: Scene.Create
        ) =>
          {
            val puIO = for {
              (dbUser, _, dbPlatform, dbProject) <- insertUserOrgPlatProject(
                userCreate,
                orgCreate,
                platform,
                projectCreate
              )
              datasource <- unsafeGetRandomDatasource
              sceneInsert <- SceneDao.insert(
                fixupSceneCreate(dbUser, datasource, sceneCreate),
                dbUser
              )
              _ <- ProjectDao.addScenesToProject(
                List(sceneInsert.id),
                dbProject.id,
                dbProject.defaultLayerId
              )
              pUO <- PlatformDao.getPlatAndUsersBySceneOwnerId(
                sceneInsert.owner
              )
            } yield (dbUser, dbPlatform, dbProject, pUO)

            val (dbUser, dbPlatform, _, pU) =
              puIO.transact(xa).unsafeRunSync

            assert(pU.platId == dbPlatform.id, "; platform ID don't match")
            assert(
              pU.platName == dbPlatform.name,
              "; platform name don't match"
            )
            assert(pU.uId == dbUser.id, "; user ID don't match")
            assert(pU.uName == dbUser.name, "; user name don't match")
            assert(
              pU.pubSettings == dbPlatform.publicSettings,
              "; platform public settings don't match"
            )
            assert(
              pU.priSettings == dbPlatform.privateSettings,
              "; platform private settings don't match"
            )
            assert(pU.email == dbUser.email, "; user email don't match")
            assert(
              pU.emailNotifications == dbUser.emailNotifications,
              "; user email notification don't match"
            )
            assert(
              pU.personalInfo == dbUser.personalInfo,
              "; user personal info don't match"
            )
            true
          }
      }
    }
  }

  test(
    "list teams a platform user belongs to or can see due to organization memberships"
  ) {
    check {
      forAll {
        (
            userPlat: (User.Create, Platform),
            orgCreate1: Organization.Create,
            orgCreate2: Organization.Create,
            orgCreate3: Organization.Create,
            teamCreate1: Team.Create,
            teamCreate2: Team.Create,
            teamCreate3: Team.Create,
            teamNamePartial: SearchQueryParameters
        ) =>
          {
            val (userCreate, platform) = userPlat
            val createAndGetTeamsIO = for {
              // Team# is in Org#
              // User belongs to Org1 and Team1
              (user, org1, dbPlatform) <- insertUserOrgPlatform(
                userCreate,
                orgCreate1,
                platform
              )
              teamInsert1 <- TeamDao.create(
                fixupTeam(fixTeamName(teamCreate1, teamNamePartial), org1, user)
              )
              _ <- UserGroupRoleDao.create(
                UserGroupRole
                  .Create(
                    user.id,
                    GroupType.Team,
                    teamInsert1.id,
                    GroupRole.Member
                  )
                  .toUserGroupRole(user, MembershipStatus.Approved)
              )

              // User belongs to Org2 but not Team2
              org2 <- OrganizationDao.createOrganization(
                orgCreate2.copy(platformId = dbPlatform.id)
              )
              _ <- UserGroupRoleDao.create(
                UserGroupRole
                  .Create(
                    user.id,
                    GroupType.Organization,
                    org2.id,
                    GroupRole.Member
                  )
                  .toUserGroupRole(user, MembershipStatus.Approved)
              )
              teamInsert2 <- TeamDao.create(
                fixupTeam(fixTeamName(teamCreate2, teamNamePartial), org2, user)
              )

              // User belongs to Team3 but not Org3
              org3 <- OrganizationDao.createOrganization(
                orgCreate3.copy(platformId = dbPlatform.id)
              )
              teamInsert3 <- TeamDao.create(fixupTeam(teamCreate3, org3, user))
              _ <- UserGroupRoleDao.create(
                UserGroupRole
                  .Create(
                    user.id,
                    GroupType.Team,
                    teamInsert3.id,
                    GroupRole.Member
                  )
                  .toUserGroupRole(user, MembershipStatus.Approved)
              )

              searchedTeams <- PlatformDao.listPlatformUserTeams(
                user,
                teamNamePartial
              )

            } yield (teamInsert1, teamInsert2, teamInsert3, searchedTeams)

            val (teamInsert1, teamInsert2, teamInsert3, searchedTeams) =
              createAndGetTeamsIO.transact(xa).unsafeRunSync

            val teams = List(teamInsert1, teamInsert2, teamInsert3)

            teamNamePartial.search match {
              case Some(teamName) if teamName.length != 0 =>
                teams.count(_.name.toUpperCase.contains(teamName.toUpperCase)) == searchedTeams.length
              case _ => searchedTeams.length == 3
            }
          }
      }
    }
  }
}
