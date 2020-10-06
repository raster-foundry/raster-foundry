package com.rasterfoundry.database

import com.rasterfoundry.common.Generators.Implicits._
import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.datamodel.PageRequest
import com.rasterfoundry.datamodel._

import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import doobie.implicits._
import org.scalacheck.Prop.forAll
import org.scalatestplus.scalacheck.Checkers

import scala.util.Random
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class ProjectDaoSpec
    extends AnyFunSuite
    with Matchers
    with Checkers
    with DBTestConfig
    with LazyLogging
    with PropTestHelpers {

  test("insert a project") {
    check {
      forAll {
        (
            user: User.Create,
            org: Organization.Create,
            project: Project.Create,
            platform: Platform
        ) =>
          {
            val projInsertIO = for {
              (_, _, _, dbProject) <- insertUserOrgPlatProject(
                user,
                org,
                platform,
                project
              )
              dbProjectLayer <- ProjectLayerDao.unsafeGetProjectLayerById(
                dbProject.defaultLayerId
              )
            } yield (dbProject, dbProjectLayer)
            val (insertedProject, insertedDefaultLayer) =
              projInsertIO.transact(xa).unsafeRunSync

            insertedProject.name == project.name &&
            insertedProject.description == project.description &&
            insertedProject.visibility == project.visibility &&
            insertedProject.tileVisibility == project.tileVisibility &&
            insertedProject.isAOIProject == project.isAOIProject &&
            insertedProject.aoiCadenceMillis == project.aoiCadenceMillis &&
            insertedProject.tags == project.tags &&
            insertedProject.isSingleBand == project.isSingleBand &&
            insertedProject.singleBandOptions == project.singleBandOptions &&
            insertedProject.isSingleBand == insertedDefaultLayer.isSingleBand &&
            insertedProject.singleBandOptions == insertedDefaultLayer.singleBandOptions
          }
      }
    }
  }

  test("update a project") {
    check {
      forAll {
        (
            user: User.Create,
            org: Organization.Create,
            insertProject: Project.Create,
            updateProject: Project.Create,
            platform: Platform
        ) =>
          {
            val rowsAndUpdateIO = for {
              (dbUser, _, _, dbProject) <- insertUserOrgPlatProject(
                user,
                org,
                platform,
                insertProject
              )
              fixedUpUpdateProject = fixupProjectCreate(dbUser, updateProject)
                .toProject(dbUser, dbProject.defaultLayerId)
              affectedRows <- ProjectDao.updateProject(
                fixedUpUpdateProject,
                dbProject.id
              )
              fetched <- ProjectDao.unsafeGetProjectById(dbProject.id)
            } yield { (affectedRows, fetched) }

            val (affectedRows, updatedProject) =
              rowsAndUpdateIO.transact(xa).unsafeRunSync

            affectedRows == 1 &&
            updatedProject.owner == user.id &&
            updatedProject.name == updateProject.name &&
            updatedProject.description == updateProject.description &&
            updatedProject.visibility == updateProject.visibility &&
            updatedProject.tileVisibility == updateProject.tileVisibility &&
            updatedProject.isAOIProject == updateProject.isAOIProject &&
            updatedProject.aoiCadenceMillis == updateProject.aoiCadenceMillis &&
            updatedProject.tags == updateProject.tags &&
            updatedProject.isSingleBand == updateProject.isSingleBand &&
            updatedProject.singleBandOptions == updateProject.singleBandOptions
          }
      }
    }
  }

  test("delete a project") {
    check {
      forAll {
        (
            user: User.Create,
            org: Organization.Create,
            project: Project.Create,
            platform: Platform
        ) =>
          {
            val deleteIO = for {
              (_, _, _, dbProject) <- insertUserOrgPlatProject(
                user,
                org,
                platform,
                project
              )
              fetched <- ProjectDao.getProjectById(dbProject.id)
              _ <- ProjectDao.deleteProject(dbProject.id)
              fetched2 <- ProjectDao.getProjectById(dbProject.id)
            } yield { (fetched, fetched2) }

            val (fetched1, fetched2) = deleteIO.transact(xa).unsafeRunSync

            !fetched1.isEmpty && fetched2.isEmpty
          }
      }
    }
  }

  test("list projects") {
    check {
      forAll {
        (
            user: User.Create,
            org: Organization.Create,
            project: Project.Create,
            platform: Platform,
            pageRequest: PageRequest
        ) =>
          {
            val projectsListIO = for {
              (dbUser, _, _, _) <- insertUserOrgPlatProject(
                user,
                org,
                platform,
                project
              )
              listedProjects <- {
                ProjectDao
                  .authQuery(dbUser, ObjectType.Project)
                  .filter(dbUser)
                  .page(pageRequest)
                  .flatMap(ProjectDao.projectsToProjectsWithRelated)
              }
            } yield { listedProjects }

            val projectsWithRelatedPage =
              projectsListIO.transact(xa).unsafeRunSync.results.head.owner
            assert(
              projectsWithRelatedPage.id == user.id,
              "Listed project's owner should be the same as the creating user"
            )
            true
          }
      }
    }
  }

  // addPermission and getPermissions
  test("add a permission to a project and get it back") {
    check {
      forAll {
        (
            userTeamOrgPlat: (
                User.Create,
                Team.Create,
                Organization.Create,
                Platform
            ),
            acr: ObjectAccessControlRule,
            project: Project.Create,
            grantedUser: User.Create
        ) =>
          {
            val projectPermissionIO = for {
              projMiscInsert <- fixUpProjMiscInsert(userTeamOrgPlat, project)
              (projectInsert, dbUserTeamOrgPlat) = projMiscInsert
              dbGrantedUser <- UserDao.create(grantedUser)
              dbUserGrantedTeamOrgPlat = dbUserTeamOrgPlat.copy(
                _1 = dbGrantedUser
              )
              acrInsert = fixUpObjectAcr(acr, dbUserGrantedTeamOrgPlat)
              _ <- ProjectDao.addPermission(projectInsert.id, acrInsert)
              permissions <- ProjectDao.getPermissions(projectInsert.id)
            } yield { (permissions, acrInsert) }

            val (permissions, acrInsert) =
              projectPermissionIO.transact(xa).unsafeRunSync

            assert(
              permissions.headOption == Some(acrInsert),
              "Inserting a permission to a project and get it back should return the same granted permission."
            )
            true
          }
      }
    }
  }

  // addPermissionsMany and getPermissions
  test("add multiple permissions to a project and get them back") {
    check {
      forAll {
        (
            userTeamOrgPlat: (
                User.Create,
                Team.Create,
                Organization.Create,
                Platform
            ),
            acrList: List[ObjectAccessControlRule],
            project: Project.Create,
            grantedUser: User.Create
        ) =>
          {
            val projectPermissionsIO = for {
              projMiscInsert <- fixUpProjMiscInsert(userTeamOrgPlat, project)
              (projectInsert, dbUserTeamOrgPlat) = projMiscInsert
              dbGrantedUser <- UserDao.create(grantedUser)
              dbUserGrantedTeamOrgPlat = dbUserTeamOrgPlat.copy(
                _1 = dbGrantedUser
              )
              acrListToInsert = acrList.map(
                fixUpObjectAcr(_, dbUserGrantedTeamOrgPlat)
              )
              permissionsInsert <- ProjectDao.addPermissionsMany(
                projectInsert.id,
                acrListToInsert
              )
              permissionsBack <- ProjectDao.getPermissions(projectInsert.id)
            } yield { (permissionsInsert, permissionsBack) }

            val (permissionsInsert, permissionsBack) =
              projectPermissionsIO.transact(xa).unsafeRunSync

            assert(
              permissionsInsert.diff(permissionsBack).length == 0,
              "Inserting a list of permissions and getting them back should be the same list of permissions."
            )
            true
          }
      }
    }
  }

  // addPermission, replacePermissions, and getPermissions
  test("add a permission to a project and replace it with many others") {
    check {
      forAll {
        (
            userTeamOrgPlat: (
                User.Create,
                Team.Create,
                Organization.Create,
                Platform
            ),
            acr1: ObjectAccessControlRule,
            acrList: List[ObjectAccessControlRule],
            project: Project.Create,
            grantedUser: User.Create
        ) =>
          {
            val projectReplacePermissionsIO = for {
              projMiscInsert <- fixUpProjMiscInsert(userTeamOrgPlat, project)
              (projectInsert, dbUserTeamOrgPlat) = projMiscInsert
              dbGrantedUser <- UserDao.create(grantedUser)
              dbUserGrantedTeamOrgPlat = dbUserTeamOrgPlat.copy(
                _1 = dbGrantedUser
              )
              acrInsert1 = fixUpObjectAcr(acr1, dbUserGrantedTeamOrgPlat)
              permissions <- ProjectDao.addPermission(
                projectInsert.id,
                acrInsert1
              )
              _ = logger.trace(s"Access Control Rules Available: $permissions")
              acrListToInsert = acrList.map(
                fixUpObjectAcr(_, dbUserGrantedTeamOrgPlat)
              )
              // Right(permReplaced) is a warning, but `map { _.right.get }` is _fine_
              // and scapegoat doesn't care about our testing code -- if that changes
              // we'll need to suppress a warning here
              permReplaced <- ProjectDao.replacePermissions(
                projectInsert.id,
                acrListToInsert
              ) map { _.right.get }
              permissionsBack <- ProjectDao.getPermissions(projectInsert.id)
            } yield { (permReplaced, permissionsBack) }

            val (permReplaced, permissionsBack) =
              projectReplacePermissionsIO.transact(xa).unsafeRunSync

            assert(
              permReplaced.diff(permissionsBack).length == 0,
              "Replacing project permissions and get them back should return same permissions."
            )
            true
          }
      }
    }
  }

  // addPermissionsMany, deletePermissions, and getPermissions
  test("add permissions to a project and delete them") {
    check {
      forAll {
        (
            userTeamOrgPlat: (
                User.Create,
                Team.Create,
                Organization.Create,
                Platform
            ),
            acrList: List[ObjectAccessControlRule],
            project: Project.Create,
            grantedUser: User.Create
        ) =>
          {
            val projectDeletePermissionsIO = for {
              projMiscInsert <- fixUpProjMiscInsert(userTeamOrgPlat, project)
              (projectInsert, dbUserTeamOrgPlat) = projMiscInsert
              dbGrantedUser <- UserDao.create(grantedUser)
              dbUserGrantedTeamOrgPlat = dbUserTeamOrgPlat.copy(
                _1 = dbGrantedUser
              )
              acrListToInsert = acrList.map(
                fixUpObjectAcr(_, dbUserGrantedTeamOrgPlat)
              )
              _ <- ProjectDao.addPermissionsMany(
                projectInsert.id,
                acrListToInsert
              )
              permsDeleted <- ProjectDao.deletePermissions(projectInsert.id)
              permissionsBack <- ProjectDao.getPermissions(projectInsert.id)
            } yield { (permsDeleted, permissionsBack) }

            val (permsDeleted, permissionsBack) =
              projectDeletePermissionsIO.transact(xa).unsafeRunSync

            assert(
              permsDeleted == 1,
              "Deleting project permissions should give number of rows updated."
            )
            assert(
              permissionsBack.length == 0,
              "Getting back permissions after deletion should return an empty list."
            )
            true
          }
      }
    }
  }

  // listUserActions
  test("add permissions to a project and list user actions") {
    check {
      forAll {
        (
            userTeamOrgPlat: (
                User.Create,
                Team.Create,
                Organization.Create,
                Platform
            ),
            acrList: List[ObjectAccessControlRule],
            project: Project.Create,
            grantedUser: User.Create
        ) =>
          {
            val userActionsIO = for {
              projMiscInsert <- fixUpProjMiscInsert(userTeamOrgPlat, project)
              (projectInsert, dbUserTeamOrgPlat) = projMiscInsert
              (dbUser, dbTeam, dbOrg, dbPlatform) = dbUserTeamOrgPlat
              dbGrantedUser <- UserDao.create(grantedUser)
              dbUserGrantedTeamOrgPlat = dbUserTeamOrgPlat.copy(
                _1 = dbGrantedUser
              )
              ugr <- {
                UserGroupRoleDao.create(
                  UserGroupRole
                    .Create(
                      dbGrantedUser.id,
                      GroupType.Platform,
                      dbPlatform.id,
                      GroupRole.Member
                    )
                    .toUserGroupRole(dbUser, MembershipStatus.Approved)
                ) >>
                  UserGroupRoleDao.create(
                    UserGroupRole
                      .Create(
                        dbGrantedUser.id,
                        GroupType.Organization,
                        dbOrg.id,
                        GroupRole.Member
                      )
                      .toUserGroupRole(dbUser, MembershipStatus.Approved)
                  ) >>
                  UserGroupRoleDao.create(
                    UserGroupRole
                      .Create(
                        dbGrantedUser.id,
                        GroupType.Team,
                        dbTeam.id,
                        GroupRole.Member
                      )
                      .toUserGroupRole(dbUser, MembershipStatus.Approved)
                  )
              }
              _ = logger.trace(s"Created UGR: $ugr")
              acrListToInsert = acrList.map(
                fixUpObjectAcr(_, dbUserGrantedTeamOrgPlat)
              )
              _ <- ProjectDao.addPermissionsMany(
                projectInsert.id,
                acrListToInsert
              )
              actions <- ProjectDao.listUserActions(
                dbGrantedUser,
                projectInsert.id
              )
              permissionsBack <- ProjectDao.getPermissions(projectInsert.id)
            } yield { (actions, permissionsBack) }

            val (userActions, permissionsBack) =
              userActionsIO.transact(xa).unsafeRunSync

            val acrActionsDistinct =
              permissionsBack.map(_.actionType.toString).distinct

            assert(
              acrActionsDistinct.diff(userActions).length == 0,
              "Listing actions granted to a user on a project should return all action string"
            )
            true
          }
      }
    }
  }

  test("list projects a user can view") {
    check {
      forAll {
        (
            userTeamOrgPlat: (
                User.Create,
                Team.Create,
                Organization.Create,
                Platform
            ),
            acrList: List[ObjectAccessControlRule],
            project1: Project.Create,
            project2: Project.Create,
            grantedUser: User.Create,
            page: PageRequest
        ) =>
          {
            val listProjectsIO = for {
              projMiscInsert <- fixUpProjMiscInsert(userTeamOrgPlat, project1)
              (projectInsert1, dbUserTeamOrgPlat) = projMiscInsert
              (dbUser, dbTeam, dbOrg, dbPlatform) = dbUserTeamOrgPlat
              projectInsert2 <- ProjectDao.insertProject(
                fixupProjectCreate(dbUser, project2),
                dbUser
              )
              dbGrantedUserInsert <- UserDao.create(grantedUser)
              dbGrantedUser = dbGrantedUserInsert.copy(isSuperuser = false)
              dbUserGrantedTeamOrgPlat = dbUserTeamOrgPlat.copy(
                _1 = dbGrantedUser
              )
              ugr <- {
                UserGroupRoleDao.create(
                  UserGroupRole
                    .Create(
                      dbGrantedUser.id,
                      GroupType.Platform,
                      dbPlatform.id,
                      GroupRole.Member
                    )
                    .toUserGroupRole(dbUser, MembershipStatus.Approved)
                ) >>
                  UserGroupRoleDao.create(
                    UserGroupRole
                      .Create(
                        dbGrantedUser.id,
                        GroupType.Organization,
                        dbOrg.id,
                        GroupRole.Member
                      )
                      .toUserGroupRole(dbUser, MembershipStatus.Approved)
                  ) >>
                  UserGroupRoleDao.create(
                    UserGroupRole
                      .Create(
                        dbGrantedUser.id,
                        GroupType.Team,
                        dbTeam.id,
                        GroupRole.Member
                      )
                      .toUserGroupRole(dbUser, MembershipStatus.Approved)
                  )
              }
              _ = logger.trace(s"Created UGR: $ugr")
              acrListToInsert = acrList.map(
                fixUpObjectAcr(_, dbUserGrantedTeamOrgPlat)
              )
              _ <- ProjectDao.addPermissionsMany(
                projectInsert1.id,
                acrListToInsert
              )
              permissionsBack <- ProjectDao.getPermissions(projectInsert1.id)
              paginatedProjects <- ProjectDao
                .authQuery(dbGrantedUser, ObjectType.Project)
                .filter(fr"owner=${dbUser.id}")
                .page(page)
            } yield {
              (
                projectInsert1,
                projectInsert2,
                permissionsBack,
                paginatedProjects
              )
            }

            val (
              projectInsert1,
              projectInsert2,
              permissionsBack,
              paginatedProjects
            ) =
              listProjectsIO.transact(xa).unsafeRunSync

            val hasViewPermission =
              permissionsBack.exists(_.actionType == ActionType.View)

            (
              hasViewPermission,
              projectInsert1.visibility,
              projectInsert2.visibility
            ) match {
              case (true, _, Visibility.Public) =>
                paginatedProjects.results
                  .map(_.id)
                  .diff(List(projectInsert1.id, projectInsert2.id))
                  .length == 0
              case (true, _, _) =>
                paginatedProjects.results
                  .map(_.id)
                  .diff(List(projectInsert1.id))
                  .length == 0
              case (false, Visibility.Public, Visibility.Public) =>
                paginatedProjects.results
                  .map(_.id)
                  .diff(List(projectInsert1.id, projectInsert2.id))
                  .length == 0
              case (false, Visibility.Public, _) =>
                paginatedProjects.results
                  .map(_.id)
                  .diff(List(projectInsert1.id))
                  .length == 0
              case (false, _, Visibility.Public) =>
                paginatedProjects.results
                  .map(_.id)
                  .diff(List(projectInsert2.id))
                  .length == 0
              case _ =>
                paginatedProjects.results.length == 0
            }
          }
      }
    }
  }

  // authorized -- a function for checking a permission of a user on an object
  test("check if a user can have a certain action on a project") {
    check {
      forAll {
        (
            userTeamOrgPlat: (
                User.Create,
                Team.Create,
                Organization.Create,
                Platform
            ),
            acrList: List[ObjectAccessControlRule],
            project1: Project.Create,
            project2: Project.Create,
            grantedUser: User.Create
        ) =>
          {
            val projectCreateIO = for {
              projMiscInsert <- fixUpProjMiscInsert(userTeamOrgPlat, project1)
              (projectInsert1, dbUserTeamOrgPlat) = projMiscInsert
              (dbUser, dbTeam, dbOrg, dbPlatform) = dbUserTeamOrgPlat
              projectInsert2 <- ProjectDao.insertProject(
                fixupProjectCreate(dbUser, project2),
                dbUser
              )
              dbGrantedUserInsert <- UserDao.create(grantedUser)
              dbGrantedUser = dbGrantedUserInsert.copy(isSuperuser = false)
              dbUserGrantedTeamOrgPlat = dbUserTeamOrgPlat.copy(
                _1 = dbGrantedUser
              )
              _ <- {
                UserGroupRoleDao.create(
                  UserGroupRole
                    .Create(
                      dbGrantedUser.id,
                      GroupType.Platform,
                      dbPlatform.id,
                      GroupRole.Member
                    )
                    .toUserGroupRole(dbUser, MembershipStatus.Approved)
                ) >>
                  UserGroupRoleDao.create(
                    UserGroupRole
                      .Create(
                        dbGrantedUser.id,
                        GroupType.Organization,
                        dbOrg.id,
                        GroupRole.Member
                      )
                      .toUserGroupRole(dbUser, MembershipStatus.Approved)
                  ) >>
                  UserGroupRoleDao.create(
                    UserGroupRole
                      .Create(
                        dbGrantedUser.id,
                        GroupType.Team,
                        dbTeam.id,
                        GroupRole.Member
                      )
                      .toUserGroupRole(dbUser, MembershipStatus.Approved)
                  )
              }
            } yield {
              (
                projectInsert1,
                projectInsert2,
                dbUserGrantedTeamOrgPlat,
                dbGrantedUser
              )
            }

            val isUserPermittedIO = for {
              projectCreate <- projectCreateIO
              (
                projectInsert1,
                projectInsert2,
                dbUserGrantedTeamOrgPlat,
                dbGrantedUser
              ) = projectCreate
              acrListToInsert = acrList.map(
                fixUpObjectAcr(_, dbUserGrantedTeamOrgPlat)
              )
              acrs <- ProjectDao.addPermissionsMany(
                projectInsert1.id,
                acrListToInsert
              )
              _ = logger.debug(s"ACRS Added: $acrs")
              action = Random.shuffle(acrListToInsert.map(_.actionType)).head
              isPermitted1 <- ProjectDao.authorized(
                dbGrantedUser,
                ObjectType.Project,
                projectInsert1.id,
                action
              )
              isPermitted2 <- ProjectDao.authorized(
                dbGrantedUser,
                ObjectType.Project,
                projectInsert2.id,
                action
              )
            } yield {
              (
                action,
                projectInsert2,
                isPermitted1.toBoolean,
                isPermitted2.toBoolean
              )
            }

            val (action, projectInsert2, isPermitted1, isPermitted2) =
              isUserPermittedIO.transact(xa).unsafeRunSync

            if (projectInsert2.visibility == Visibility.Public) {
              (action) match {
                case ActionType.View | ActionType.Export |
                    ActionType.Annotate =>
                  isPermitted1 && isPermitted2
                case _ => isPermitted1 && !isPermitted2
              }
            } else {
              isPermitted1
            }
          }
      }
    }
  }
}
