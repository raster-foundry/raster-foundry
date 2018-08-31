package com.azavea.rf.database

import java.sql.Timestamp
import scala.util.Random

import com.azavea.rf.datamodel._
import com.azavea.rf.datamodel.Generators.Implicits._
import com.azavea.rf.database.Implicits._
import doobie._
import doobie.implicits._
import cats._
import cats.data._
import cats.effect.IO
import cats.implicits._
import cats.syntax._
import doobie.postgres._
import doobie.postgres.implicits._
import org.scalacheck.Prop.forAll
import org.scalatest._
import org.scalatest.prop.Checkers
import io.circe._
import io.circe.syntax._
import com.lonelyplanet.akka.http.extensions.PageRequest
import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global

class ProjectDaoSpec
    extends FunSuite
    with Matchers
    with Checkers
    with DBTestConfig
    with PropTestHelpers {

  // insertProject
  test("insert a project") {
    check {
      forAll {
        (user: User.Create,
         org: Organization.Create,
         project: Project.Create) =>
          {
            val projInsertIO = insertUserAndOrg(user, org) flatMap {
              case (org: Organization, user: User) => {
                ProjectDao.insertProject(fixupProjectCreate(user, project),
                                         user)
              }
            }
            val insertedProject = projInsertIO.transact(xa).unsafeRunSync
            insertedProject.name == project.name &&
            insertedProject.description == project.description &&
            insertedProject.visibility == project.visibility &&
            insertedProject.tileVisibility == project.tileVisibility &&
            insertedProject.isAOIProject == project.isAOIProject &&
            insertedProject.aoiCadenceMillis == project.aoiCadenceMillis &&
            insertedProject.tags == project.tags &&
            insertedProject.isSingleBand == project.isSingleBand &&
            insertedProject.singleBandOptions == project.singleBandOptions
          }
      }
    }
  }

  // updateProject
  test("update a project") {
    check {
      forAll {
        (user: User.Create,
         org: Organization.Create,
         insertProject: Project.Create,
         updateProject: Project.Create) =>
          {
            val projInsertWithUserAndOrgIO = insertUserAndOrg(user, org) flatMap {
              case (dbOrg: Organization, dbUser: User) => {
                ProjectDao.insertProject(fixupProjectCreate(dbUser,
                                                            insertProject),
                                         dbUser) map {
                  (_, dbUser, dbOrg)
                }
              }
            }
            val updateProjectWithUpdatedIO = projInsertWithUserAndOrgIO flatMap {
              case (dbProject: Project, dbUser: User, dbOrg: Organization) => {
                val fixedUpUpdateProject =
                  fixupProjectCreate(dbUser, updateProject).toProject(dbUser)
                ProjectDao.updateProject(fixedUpUpdateProject,
                                         dbProject.id,
                                         dbUser) flatMap {
                  (affectedRows: Int) =>
                    {
                      ProjectDao.unsafeGetProjectById(dbProject.id) map {
                        (affectedRows, _)
                      }
                    }
                }
              }
            }

            val (affectedRows, updatedProject) =
              updateProjectWithUpdatedIO.transact(xa).unsafeRunSync

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

  // deleteProject
  test("delete a project") {
    check {
      forAll {
        (user: User.Create,
         org: Organization.Create,
         project: Project.Create) =>
          {
            val projInsertWithUserIO = insertUserAndOrg(user, org) flatMap {
              case (dbOrg: Organization, dbUser: User) => {
                ProjectDao.insertProject(fixupProjectCreate(dbUser, project),
                                         dbUser) map {
                  (_, dbUser)
                }
              }
            }

            val projDeleteIO = projInsertWithUserIO flatMap {
              case (dbProject: Project, dbUser: User) => {
                ProjectDao.deleteProject(dbProject.id) flatMap { _ =>
                  ProjectDao.getProjectById(dbProject.id)
                }
              }
            }

            projDeleteIO.transact(xa).unsafeRunSync == None
          }
      }
    }
  }

  // list projects
  test("list projects") {
    check {
      forAll {
        (user: User.Create,
         org: Organization.Create,
         project: Project.Create,
         pageRequest: PageRequest) =>
          {
            val projectsListIO = for {
              orgAndUser <- insertUserAndOrg(user, org)
              (dbOrg, dbUser) = orgAndUser
              _ <- ProjectDao.insertProject(fixupProjectCreate(dbUser, project),
                                            dbUser)
              listedProjects <- {
                ProjectDao
                  .authQuery(dbUser, ObjectType.Project)
                  .filter(dbUser)
                  .page(pageRequest, fr"")
                  .flatMap(ProjectDao.projectsToProjectsWithRelated)
              }
            } yield { listedProjects }

            val returnedThinUser =
              projectsListIO.transact(xa).unsafeRunSync.results.head.owner
            assert(
              returnedThinUser.id == user.id,
              "Listed project's owner should be the same as the creating user")
            true
          }
      }
    }
  }

  // add scenes to a project
  // also exercises createScenesToProject
  test("add scenes to a project") {
    check {
      forAll {
        (user: User.Create,
         org: Organization.Create,
         scenes: List[Scene.Create],
         project: Project.Create) =>
          {
            val projAndScenesInsertWithUserIO = insertUserAndOrg(user, org) flatMap {
              case (dbOrg: Organization, dbUser: User) => {
                val scenesInsertIO = unsafeGetRandomDatasource flatMap {
                  (dbDatasource: Datasource) =>
                    {
                      scenes.traverse(
                        (scene: Scene.Create) => {
                          SceneDao.insert(fixupSceneCreate(dbUser,
                                                           dbDatasource,
                                                           scene),
                                          dbUser)
                        }
                      )
                    }
                }
                val projectInsertIO =
                  ProjectDao.insertProject(fixupProjectCreate(dbUser, project),
                                           dbUser)
                (projectInsertIO, scenesInsertIO, dbUser.pure[ConnectionIO]).tupled
              }
            }

            val addScenesIO = projAndScenesInsertWithUserIO flatMap {
              case (dbProject: Project,
                    dbScenes: List[Scene.WithRelated],
                    dbUser: User) => {
                ProjectDao.addScenesToProject(
                  // this.get is safe because the arbitrary instance only produces NELs
                  (dbScenes map { _.id }).toNel.get,
                  dbProject.id,
                  true
                )
              }
            }
            addScenesIO.transact(xa).unsafeRunSync == scenes.length
          }
      }
    }
  }

  // listProjectSceneOrder
  test("list project scenes order") {
    check {
      forAll {
        (user: User.Create,
         org: Organization.Create,
         scenes: List[Scene.Create],
         project: Project.Create,
         pageRequest: PageRequest) =>
          {
            val projAndScenesInsertWithUserIO = insertUserAndOrg(user, org) flatMap {
              case (dbOrg: Organization, dbUser: User) => {
                val scenesInsertIO = unsafeGetRandomDatasource flatMap {
                  (dbDatasource: Datasource) =>
                    {
                      scenes.traverse(
                        (scene: Scene.Create) => {
                          SceneDao.insert(fixupSceneCreate(dbUser,
                                                           dbDatasource,
                                                           scene),
                                          dbUser)
                        }
                      )
                    }
                }
                val projectInsertIO =
                  ProjectDao.insertProject(fixupProjectCreate(dbUser, project),
                                           dbUser)
                (projectInsertIO, scenesInsertIO, dbUser.pure[ConnectionIO]).tupled
              }
            }

            val addScenesWithProjectAndUserAndScenesIO = projAndScenesInsertWithUserIO flatMap {
              case (dbProject: Project,
                    dbScenes: List[Scene.WithRelated],
                    dbUser: User) => {
                ProjectDao.addScenesToProject(dbScenes map { _.id },
                                              dbProject.id) map { _ =>
                  (dbProject, dbUser, dbScenes)
                }
              }
            }

            val listAddedSceneIDsIO = addScenesWithProjectAndUserAndScenesIO flatMap {
              case (dbProject: Project,
                    dbUser: User,
                    dbScenes: List[Scene.WithRelated]) => {
                // TODO test the normal list endpoint here
                ProjectScenesDao.listProjectScenes(
                  dbProject.id,
                  pageRequest,
                  CombinedSceneQueryParams()) map {
                  (resp: PaginatedResponse[Scene.ProjectScene]) =>
                    (
                      resp.results map { _.id },
                      dbScenes map { _.id }
                    )
                }
              }
            }
            val (foundScenes, createdScenes) =
              listAddedSceneIDsIO.transact(xa).unsafeRunSync
            foundScenes.toSet == createdScenes.toSet
          }
      }
    }
  }

  // deleteScenesFromProject
  test("delete scenes from a project") {
    check {
      forAll {
        (user: User.Create,
         org: Organization.Create,
         scenes: List[Scene.Create],
         project: Project.Create,
         pageRequest: PageRequest) =>
          {
            val projAndScenesInsertWithUserIO = insertUserAndOrg(user, org) flatMap {
              case (dbOrg: Organization, dbUser: User) => {
                val scenesInsertIO = unsafeGetRandomDatasource flatMap {
                  (dbDatasource: Datasource) =>
                    {
                      scenes.traverse(
                        (scene: Scene.Create) => {
                          SceneDao.insert(fixupSceneCreate(dbUser,
                                                           dbDatasource,
                                                           scene),
                                          dbUser)
                        }
                      )
                    }
                }
                val projectInsertIO =
                  ProjectDao.insertProject(fixupProjectCreate(dbUser, project),
                                           dbUser)
                (projectInsertIO, scenesInsertIO, dbUser.pure[ConnectionIO]).tupled
              }
            }

            val addAndDeleteScenesWithProjectAndUserIO = projAndScenesInsertWithUserIO flatMap {
              case (dbProject: Project,
                    dbScenes: List[Scene.WithRelated],
                    dbUser: User) => {
                val sceneIds = dbScenes map { _.id }
                ProjectDao.addScenesToProject(
                                              // this.get is safe because the arbitrary instance only produces NELs
                                              (dbScenes map { _.id }).toNel.get,
                                              dbProject.id,
                                              true) flatMap { _ =>
                  ProjectDao.deleteScenesFromProject(dbScenes map { _.id },
                                                     dbProject.id) map { _ =>
                    (dbProject, dbUser)
                  }
                }
              }
            }

            val listAddedSceneIDsIO = addAndDeleteScenesWithProjectAndUserIO flatMap {
              case (dbProject: Project, dbUser: User) => {
                ProjectScenesDao.listProjectScenes(
                  dbProject.id,
                  pageRequest,
                  CombinedSceneQueryParams()) map {
                  (resp: PaginatedResponse[Scene.ProjectScene]) =>
                    resp.results
                }
              }
            }

            listAddedSceneIDsIO.transact(xa).unsafeRunSync == List()
          }
      }
    }
  }
  // addPermission and getPermissions
  test("add a permission to a project and get it back") {
    check {
      forAll {
        ( userTeamOrgPlat: (User.Create, Team.Create, Organization.Create, Platform),
          acr: ObjectAccessControlRule,
          project: Project.Create,
          grantedUser: User.Create
        ) => {
          val projectPermissionIO = for {
            projMiscInsert <- fixUpProjMiscInsert(userTeamOrgPlat, project)
            (projectInsert, dbUserTeamOrgPlat) = projMiscInsert
            dbGrantedUser <- UserDao.create(grantedUser)
            dbUserGrantedTeamOrgPlat = dbUserTeamOrgPlat.copy(_1 = dbGrantedUser)
            acrInsert = fixUpObjectAcr(acr, dbUserGrantedTeamOrgPlat)
            _ <- ProjectDao.addPermission(projectInsert.id, acrInsert)
            permissions <- ProjectDao.getPermissions(projectInsert.id)
          } yield { (permissions, acrInsert) }

          val (permissions, acrInsert) = projectPermissionIO.transact(xa).unsafeRunSync

          assert(permissions.flatten.headOption == Some(acrInsert),
            "Inserting a permission to a project and get it back should return the same granted permission.")
          true
        }
      }
    }
  }

  // addPermissionsMany and getPermissions
  test("add multiple permissions to a project and get them back") {
    check {
      forAll {
        ( userTeamOrgPlat: (User.Create, Team.Create, Organization.Create, Platform),
          acrList: List[ObjectAccessControlRule],
          project: Project.Create,
          grantedUser: User.Create
        ) => {
          val projectPermissionsIO = for {
            projMiscInsert <- fixUpProjMiscInsert(userTeamOrgPlat, project)
            (projectInsert, dbUserTeamOrgPlat) = projMiscInsert
            dbGrantedUser <- UserDao.create(grantedUser)
            dbUserGrantedTeamOrgPlat = dbUserTeamOrgPlat.copy(_1 = dbGrantedUser)
            acrListToInsert = acrList.map(fixUpObjectAcr(_, dbUserGrantedTeamOrgPlat))
            permissionsInsert <- ProjectDao.addPermissionsMany(projectInsert.id, acrListToInsert)
            permissionsBack <- ProjectDao.getPermissions(projectInsert.id)
          } yield { (permissionsInsert, permissionsBack) }

          val (permissionsInsert, permissionsBack) = projectPermissionsIO.transact(xa).unsafeRunSync

          assert(permissionsInsert.diff(permissionsBack).length == 0,
            "Inserting a list of permissions and getting them back should be the same list of permissions.")
          true
        }
      }
    }
  }

  // addPermission, replacePermissions, and getPermissions
  test("add a permission to a project and replace it with many others") {
    check {
      forAll {
        ( userTeamOrgPlat: (User.Create, Team.Create, Organization.Create, Platform),
          acr1: ObjectAccessControlRule,
          acrList: List[ObjectAccessControlRule],
          project: Project.Create,
          grantedUser: User.Create
        ) => {
          val projectReplacePermissionsIO = for {
            projMiscInsert <- fixUpProjMiscInsert(userTeamOrgPlat, project)
            (projectInsert, dbUserTeamOrgPlat) = projMiscInsert
            dbGrantedUser <- UserDao.create(grantedUser)
            dbUserGrantedTeamOrgPlat = dbUserTeamOrgPlat.copy(_1 = dbGrantedUser)
            acrInsert1 = fixUpObjectAcr(acr1, dbUserGrantedTeamOrgPlat)
            _ <- ProjectDao.addPermission(projectInsert.id, acrInsert1)
            acrListToInsert = acrList.map(fixUpObjectAcr(_, dbUserGrantedTeamOrgPlat))
            permReplaced <- ProjectDao.replacePermissions(projectInsert.id, acrListToInsert)
            permissionsBack <- ProjectDao.getPermissions(projectInsert.id)
          } yield { (permReplaced,permissionsBack) }

          val (permReplaced, permissionsBack) =
            projectReplacePermissionsIO.transact(xa).unsafeRunSync

          assert(permReplaced.diff(permissionsBack).length == 0,
            "Replacing project permissions and get them back should return same permissions.")
          true
        }
      }
    }
  }

  // addPermissionsMany, deletePermissions, and getPermissions
  test("add permissions to a project and delete them") {
    check {
      forAll {
        ( userTeamOrgPlat: (User.Create, Team.Create, Organization.Create, Platform),
          acrList: List[ObjectAccessControlRule],
          project: Project.Create,
          grantedUser: User.Create
        ) => {
          val projectDeletePermissionsIO = for {
            projMiscInsert <- fixUpProjMiscInsert(userTeamOrgPlat, project)
            (projectInsert, dbUserTeamOrgPlat) = projMiscInsert
            dbGrantedUser <- UserDao.create(grantedUser)
            dbUserGrantedTeamOrgPlat = dbUserTeamOrgPlat.copy(_1 = dbGrantedUser)
            acrListToInsert = acrList.map(fixUpObjectAcr(_, dbUserGrantedTeamOrgPlat))
            _ <- ProjectDao.addPermissionsMany(projectInsert.id, acrListToInsert)
            permsDeleted <- ProjectDao.deletePermissions(projectInsert.id)
            permissionsBack <- ProjectDao.getPermissions(projectInsert.id)
          } yield { (permsDeleted, permissionsBack) }

          val (permsDeleted, permissionsBack) = projectDeletePermissionsIO.transact(xa).unsafeRunSync

          assert(permsDeleted.length == 0,
            "Deleting project permissions should get an empty list back.")
          assert(permissionsBack.length == 0,
            "Getting back permissions after deletion should return an empty list.")
          true
        }
      }
    }
  }

  // listUserActions
  test("add permissions to a project and list user actions") {
    check {
      forAll {
        ( userTeamOrgPlat: (User.Create, Team.Create, Organization.Create, Platform),
          acrList: List[ObjectAccessControlRule],
          project: Project.Create,
          grantedUser: User.Create
        ) => {
          val userActionsIO = for {
            projMiscInsert <- fixUpProjMiscInsert(userTeamOrgPlat, project)
            (projectInsert, dbUserTeamOrgPlat) = projMiscInsert
            (dbUser, dbTeam, dbOrg, dbPlatform) = dbUserTeamOrgPlat
            dbGrantedUser <- UserDao.create(grantedUser)
            dbUserGrantedTeamOrgPlat = dbUserTeamOrgPlat.copy(_1 = dbGrantedUser)
            _ <- {
              UserGroupRoleDao.create(UserGroupRole.Create(dbGrantedUser.id, GroupType.Platform, dbPlatform.id, GroupRole.Member)
                .toUserGroupRole(dbUser, MembershipStatus.Approved)) >>
              UserGroupRoleDao.create(UserGroupRole.Create(dbGrantedUser.id, GroupType.Organization, dbOrg.id, GroupRole.Member)
                .toUserGroupRole(dbUser, MembershipStatus.Approved)) >>
              UserGroupRoleDao.create(UserGroupRole.Create(dbGrantedUser.id, GroupType.Team, dbTeam.id, GroupRole.Member)
                .toUserGroupRole(dbUser, MembershipStatus.Approved))
            }
            acrListToInsert = acrList.map(fixUpObjectAcr(_, dbUserGrantedTeamOrgPlat))
            _ <- ProjectDao.addPermissionsMany(projectInsert.id, acrListToInsert)
            actions <- ProjectDao.listUserActions(dbGrantedUser, projectInsert.id)
            permissionsBack <- ProjectDao.getPermissions(projectInsert.id)
          } yield { (actions, permissionsBack) }

          val (userActions, permissionsBack) = userActionsIO.transact(xa).unsafeRunSync

          val acrActionsDistinct = permissionsBack.flatten.map(_.actionType.toString).distinct

          assert(
            acrActionsDistinct.diff(userActions).length == 0,
            "Listing actions granted to a user on a project should return all action string")
          true
        }
      }
    }
  }

  // authQuery -- a function for listing viewable objects
  test("list projects a user can view") {
    check {
      forAll {
        ( userTeamOrgPlat: (User.Create, Team.Create, Organization.Create, Platform),
          acrList: List[ObjectAccessControlRule],
          project1: Project.Create,
          project2: Project.Create,
          grantedUser: User.Create,
          page: PageRequest
        ) => {
          val listProjectsIO = for {
            projMiscInsert <- fixUpProjMiscInsert(userTeamOrgPlat, project1)
            (projectInsert1, dbUserTeamOrgPlat) = projMiscInsert
            (dbUser, dbTeam, dbOrg, dbPlatform) = dbUserTeamOrgPlat
            projectInsert2 <- ProjectDao.insertProject(fixupProjectCreate(dbUser, project2), dbUser)
            dbGrantedUserInsert <- UserDao.create(grantedUser)
            dbGrantedUser = dbGrantedUserInsert.copy(isSuperuser = false)
            dbUserGrantedTeamOrgPlat = dbUserTeamOrgPlat.copy(_1 = dbGrantedUser)
            _ <- {
              UserGroupRoleDao.create(UserGroupRole.Create(dbGrantedUser.id, GroupType.Platform, dbPlatform.id, GroupRole.Member)
                .toUserGroupRole(dbUser, MembershipStatus.Approved)) >>
              UserGroupRoleDao.create(UserGroupRole.Create(dbGrantedUser.id, GroupType.Organization, dbOrg.id, GroupRole.Member)
                .toUserGroupRole(dbUser, MembershipStatus.Approved)) >>
              UserGroupRoleDao.create(UserGroupRole.Create(dbGrantedUser.id, GroupType.Team, dbTeam.id, GroupRole.Member)
                .toUserGroupRole(dbUser, MembershipStatus.Approved))
            }
            acrListToInsert = acrList.map(fixUpObjectAcr(_, dbUserGrantedTeamOrgPlat))
            _ <- ProjectDao.addPermissionsMany(projectInsert1.id, acrListToInsert)
            permissionsBack <- ProjectDao.getPermissions(projectInsert1.id)
            paginatedProjects <- ProjectDao.authQuery(dbGrantedUser, ObjectType.Project).filter(fr"owner=${dbUser.id}").page(page, fr"")
          } yield { (projectInsert1, projectInsert2, permissionsBack, paginatedProjects) }

          val (projectInsert1, projectInsert2, permissionsBack, paginatedProjects) = listProjectsIO.transact(xa).unsafeRunSync

          val hasViewPermission = permissionsBack.flatten.exists(_.actionType == ActionType.View)

          (hasViewPermission, projectInsert1.visibility, projectInsert2.visibility) match {
            case (true, _, Visibility.Public) =>
              paginatedProjects.results.map(_.id).diff(List(projectInsert1.id, projectInsert2.id)).length == 0
            case (true, _, _) =>
              paginatedProjects.results.map(_.id).diff(List(projectInsert1.id)).length == 0
            case (false, Visibility.Public, Visibility.Public) =>
              paginatedProjects.results.map(_.id).diff(List(projectInsert1.id, projectInsert2.id)).length == 0
            case (false, Visibility.Public, _) =>
              paginatedProjects.results.map(_.id).diff(List(projectInsert1.id)).length == 0
            case (false, _, Visibility.Public) =>
              paginatedProjects.results.map(_.id).diff(List(projectInsert2.id)).length == 0
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
        ( userTeamOrgPlat: (User.Create, Team.Create, Organization.Create, Platform),
          acrList: List[ObjectAccessControlRule],
          project1: Project.Create,
          project2: Project.Create,
          grantedUser: User.Create
        ) => {
          val projectCreateIO = for {
            projMiscInsert <- fixUpProjMiscInsert(userTeamOrgPlat, project1)
            (projectInsert1, dbUserTeamOrgPlat) = projMiscInsert
            (dbUser, dbTeam, dbOrg, dbPlatform) = dbUserTeamOrgPlat
            projectInsert2 <- ProjectDao.insertProject(fixupProjectCreate(dbUser, project2), dbUser)
            dbGrantedUserInsert <- UserDao.create(grantedUser)
            dbGrantedUser = dbGrantedUserInsert.copy(isSuperuser = false)
            dbUserGrantedTeamOrgPlat = dbUserTeamOrgPlat.copy(_1 = dbGrantedUser)
            _ <- {
              UserGroupRoleDao.create(UserGroupRole.Create(dbGrantedUser.id, GroupType.Platform, dbPlatform.id, GroupRole.Member)
                .toUserGroupRole(dbUser, MembershipStatus.Approved)) >>
              UserGroupRoleDao.create(UserGroupRole.Create(dbGrantedUser.id, GroupType.Organization, dbOrg.id, GroupRole.Member)
                .toUserGroupRole(dbUser, MembershipStatus.Approved)) >>
              UserGroupRoleDao.create(UserGroupRole.Create(dbGrantedUser.id, GroupType.Team, dbTeam.id, GroupRole.Member)
                .toUserGroupRole(dbUser, MembershipStatus.Approved))
            }
          } yield { (projectInsert1, projectInsert2, dbUserGrantedTeamOrgPlat, dbGrantedUser) }

          val isUserPermittedIO = for {
            projectCreate <- projectCreateIO
            (projectInsert1, projectInsert2, dbUserGrantedTeamOrgPlat, dbGrantedUser) = projectCreate
            acrListToInsert = acrList.map(fixUpObjectAcr(_, dbUserGrantedTeamOrgPlat))
            _ <- ProjectDao.addPermissionsMany(projectInsert1.id, acrListToInsert)
            action = Random.shuffle(acrListToInsert.map(_.actionType)).head
            isPermitted1 <- ProjectDao.authorized(dbGrantedUser, ObjectType.Project, projectInsert1.id, action)
            isPermitted2 <- ProjectDao.authorized(dbGrantedUser, ObjectType.Project, projectInsert2.id, action)
          } yield { (action, projectInsert2, isPermitted1, isPermitted2) }

          val (action, projectInsert2, isPermitted1, isPermitted2) = isUserPermittedIO.transact(xa).unsafeRunSync

          if (projectInsert2.visibility == Visibility.Public) {
            (action) match {
              case ActionType.View | ActionType.Export | ActionType.Annotate => isPermitted1 && isPermitted2
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
