package com.azavea.rf.database

import java.sql.Timestamp

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

class ProjectDaoSpec extends FunSuite with Matchers with Checkers with DBTestConfig with PropTestHelpers {

  // insertProject
  test("insert a project") {
    check {
      forAll {
        (user: User.Create, org: Organization.Create, project: Project.Create) => {
          val projInsertIO = insertUserAndOrg(user, org) flatMap {
            case (org: Organization, user: User) => {
              ProjectDao.insertProject(fixupProjectCreate(user, project), user)
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
        (user: User.Create, org: Organization.Create, insertProject: Project.Create, updateProject: Project.Create) => {
          val projInsertWithUserAndOrgIO = insertUserAndOrg(user, org) flatMap {
            case (dbOrg: Organization, dbUser: User) => {
              ProjectDao.insertProject(fixupProjectCreate(dbUser, insertProject), dbUser) map {
                (_, dbUser, dbOrg)
              }
            }
          }
          val updateProjectWithUpdatedIO = projInsertWithUserAndOrgIO flatMap {
            case (dbProject: Project, dbUser: User, dbOrg: Organization) => {
              val fixedUpUpdateProject = fixupProjectCreate(dbUser, updateProject).toProject(dbUser)
              ProjectDao.updateProject(fixedUpUpdateProject, dbProject.id, dbUser) flatMap {
                (affectedRows: Int) => {
                  ProjectDao.unsafeGetProjectById(dbProject.id) map {
                    (affectedRows, _)
                  }
                }
              }
            }
          }

          val (affectedRows, updatedProject) = updateProjectWithUpdatedIO.transact(xa).unsafeRunSync

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
        (user: User.Create, org: Organization.Create, project: Project.Create) => {
          val projInsertWithUserIO = insertUserAndOrg(user, org) flatMap {
            case (dbOrg: Organization, dbUser: User) => {
              ProjectDao.insertProject(fixupProjectCreate(dbUser, project), dbUser) map {
                (_, dbUser)
              }
            }
          }

          val projDeleteIO = projInsertWithUserIO flatMap {
            case (dbProject: Project, dbUser: User) => {
              ProjectDao.deleteProject(dbProject.id, dbUser) flatMap {
                _ => ProjectDao.getProjectById(dbProject.id, Some(dbUser))
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
        (user: User.Create, org: Organization.Create, project: Project.Create, pageRequest: PageRequest) => {
          val projectsListIO = for {
            orgAndUser <- insertUserAndOrg(user, org)
            (dbOrg, dbUser) = orgAndUser
            _ <- ProjectDao.insertProject(fixupProjectCreate(dbUser, project), dbUser)
            listedProjects <- {
              ProjectDao
                .authQuery(dbUser, ObjectType.Project)
                .filter(dbUser)
                .page(pageRequest)
                .flatMap(ProjectDao.projectsToProjectsWithRelated)
            }
          } yield { listedProjects }

          val returnedThinUser = projectsListIO.transact(xa).unsafeRunSync.results.head.owner
          assert(returnedThinUser.id == user.id, "Listed project's owner should be the same as the creating user")
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
        (user: User.Create, org: Organization.Create, scenes: List[Scene.Create], project: Project.Create) => {
          val projAndScenesInsertWithUserIO = insertUserAndOrg(user, org) flatMap {
            case (dbOrg: Organization, dbUser: User) => {
              val scenesInsertIO = unsafeGetRandomDatasource flatMap {
                (dbDatasource: Datasource) => {
                  scenes.traverse(
                    (scene: Scene.Create) => {
                      SceneDao.insert(fixupSceneCreate(dbUser, dbDatasource, scene), dbUser)
                    }
                  )
                }
              }
              val projectInsertIO = ProjectDao.insertProject(fixupProjectCreate(dbUser, project), dbUser)
              (projectInsertIO, scenesInsertIO, dbUser.pure[ConnectionIO]).tupled
            }
          }

          val addScenesIO = projAndScenesInsertWithUserIO flatMap {
            case (dbProject: Project, dbScenes: List[Scene.WithRelated], dbUser: User) => {
              ProjectDao.addScenesToProject(
                // this.get is safe because the arbitrary instance only produces NELs
                (dbScenes map {_.id}).toNel.get,
                dbProject.id,
                dbUser,
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
        (user: User.Create, org: Organization.Create, scenes: List[Scene.Create], project: Project.Create, pageRequest: PageRequest) => {
          val projAndScenesInsertWithUserIO = insertUserAndOrg(user, org) flatMap {
            case (dbOrg: Organization, dbUser: User) => {
              val scenesInsertIO = unsafeGetRandomDatasource flatMap {
                (dbDatasource: Datasource) => {
                  scenes.traverse(
                    (scene: Scene.Create) => {
                      SceneDao.insert(fixupSceneCreate(dbUser, dbDatasource, scene), dbUser)
                    }
                  )
                }
              }
              val projectInsertIO = ProjectDao.insertProject(fixupProjectCreate(dbUser, project), dbUser)
              (projectInsertIO, scenesInsertIO, dbUser.pure[ConnectionIO]).tupled
            }
          }

          val addScenesWithProjectAndUserAndScenesIO = projAndScenesInsertWithUserIO flatMap {
            case (dbProject: Project, dbScenes: List[Scene.WithRelated], dbUser: User) => {
              ProjectDao.addScenesToProject(dbScenes map { _.id }, dbProject.id, dbUser) map {
                _ => (dbProject, dbUser, dbScenes)
              }
            }
          }

          val listAddedSceneIDsIO = addScenesWithProjectAndUserAndScenesIO flatMap {
            case (dbProject: Project, dbUser: User, dbScenes: List[Scene.WithRelated]) => {
              ProjectDao.listProjectSceneOrder(dbProject.id, pageRequest, dbUser) map {
                (resp: PaginatedResponse[UUID]) => (resp.results, dbScenes map { _.id })
              }
            }
          }
          val (foundScenes, createdScenes) = listAddedSceneIDsIO.transact(xa).unsafeRunSync
          foundScenes.toSet == createdScenes.toSet
        }
      }
    }
  }

  // deleteScenesFromProject
  test("delete scenes from a project") {
    check {
      forAll {
        (user: User.Create, org: Organization.Create, scenes: List[Scene.Create], project: Project.Create, pageRequest: PageRequest) => {
          val projAndScenesInsertWithUserIO = insertUserAndOrg(user, org) flatMap {
            case (dbOrg: Organization, dbUser: User) => {
              val scenesInsertIO = unsafeGetRandomDatasource flatMap {
                (dbDatasource: Datasource) => {
                  scenes.traverse(
                    (scene: Scene.Create) => {
                      SceneDao.insert(fixupSceneCreate(dbUser, dbDatasource, scene), dbUser)
                    }
                  )
                }
              }
              val projectInsertIO = ProjectDao.insertProject(fixupProjectCreate(dbUser, project), dbUser)
              (projectInsertIO, scenesInsertIO, dbUser.pure[ConnectionIO]).tupled
            }
          }

          val addAndDeleteScenesWithProjectAndUserIO = projAndScenesInsertWithUserIO flatMap {
            case (dbProject: Project, dbScenes: List[Scene.WithRelated], dbUser: User) => {
              val sceneIds = dbScenes map {_.id}
              ProjectDao.addScenesToProject(
                // this.get is safe because the arbitrary instance only produces NELs
                (dbScenes map {_.id}).toNel.get, dbProject.id, dbUser, true) flatMap {
                _ => ProjectDao.deleteScenesFromProject(dbScenes map {_.id}, dbProject.id) map {
                  _ => (dbProject, dbUser)
                }
              }
            }
          }

          val listAddedSceneIDsIO = addAndDeleteScenesWithProjectAndUserIO flatMap {
            case (dbProject: Project, dbUser: User) => {
              ProjectDao.listProjectSceneOrder(dbProject.id, pageRequest, dbUser) map {
                (resp: PaginatedResponse[UUID]) => resp.results
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
            perms <- ProjectDao.addPermission(projectInsert.id, acrInsert)
            permissions <- ProjectDao.getPermissions(projectInsert.id)
          } yield { (permissions, acrInsert) }

          val (permissions, acrInsert) = projectPermissionIO.transact(xa).unsafeRunSync

          assert(permissions.flatten.headOption == Some(acrInsert),
            "Inserting a permission to a project and get it back should return the granted permission")
          true
        }
      }
    }
  }

  // addPermissionsMany
  test("add multiple permissions to a project and get it back") {
    check {
      forAll {
        ( userTeamOrgPlat: (User.Create, Team.Create, Organization.Create, Platform),
          acr1: ObjectAccessControlRule,
          acr2: ObjectAccessControlRule,
          acr3: ObjectAccessControlRule,
          project: Project.Create,
          grantedUser: User.Create
        ) => {
          val projectPermissionsIO = for {
            projMiscInsert <- fixUpProjMiscInsert(userTeamOrgPlat, project)
            (projectInsert, dbUserTeamOrgPlat) = projMiscInsert
            dbGrantedUser <- UserDao.create(grantedUser)
            dbUserGrantedTeamOrgPlat = dbUserTeamOrgPlat.copy(_1 = dbGrantedUser)
            acrInsert1 = fixUpObjectAcr(acr1, dbUserGrantedTeamOrgPlat)
            acrInsert2 = fixUpObjectAcr(acr2, dbUserGrantedTeamOrgPlat)
            acrInsert3 = fixUpObjectAcr(acr3, dbUserGrantedTeamOrgPlat)
            permissions <- ProjectDao.addPermissionsMany(projectInsert.id, List(
              acrInsert1,
              acrInsert2,
              acrInsert3
            ))
          } yield { (permissions, List(acrInsert1, acrInsert2, acrInsert3).map(Some(_))) }

          val (permissions, acrOptionList) = projectPermissionsIO.transact(xa).unsafeRunSync

          assert(permissions.diff(acrOptionList).length == 0,
            "Inserting a list of permissions to a project should get back the same list of them")
          true
        }
      }
    }
  }

  // addPermission and replacePermissions
  test("add a permission to a project and replace it with many others") {
    check {
      forAll {
        ( userTeamOrgPlat: (User.Create, Team.Create, Organization.Create, Platform),
          acr1: ObjectAccessControlRule,
          acr2: ObjectAccessControlRule,
          acr3: ObjectAccessControlRule,
          project: Project.Create,
          grantedUser: User.Create
        ) => {
          val projectReplacePermissionsIO = for {
            projMiscInsert <- fixUpProjMiscInsert(userTeamOrgPlat, project)
            (projectInsert, dbUserTeamOrgPlat) = projMiscInsert
            dbGrantedUser <- UserDao.create(grantedUser)
            dbUserGrantedTeamOrgPlat = dbUserTeamOrgPlat.copy(_1 = dbGrantedUser)
            acrInsert1 = fixUpObjectAcr(acr1, dbUserGrantedTeamOrgPlat)
            permAdded <- ProjectDao.addPermission(projectInsert.id, acrInsert1)
            acrInsert2 = fixUpObjectAcr(acr2, dbUserGrantedTeamOrgPlat)
            acrInsert3 = fixUpObjectAcr(acr3, dbUserGrantedTeamOrgPlat)
            permReplaced <- ProjectDao.replacePermissions(projectInsert.id, List(
              acrInsert2,
              acrInsert3
            ))
          } yield { (
            permAdded,
            List(Some(acrInsert1)),
            permReplaced,
            List(acrInsert2, acrInsert3).map(Some(_))
          ) }

          val (permAdded, acrAdded, permReplaced, acrReplaced) =
            projectReplacePermissionsIO.transact(xa).unsafeRunSync

          assert(permAdded.diff(acrAdded).length == 0,
            "Inserting a permission to a project should get the permission back")
          assert(permReplaced.diff(acrReplaced).length == 0,
            "Replacing project permissions should get the replaced permissions back")
          true
        }
      }
    }
  }

  // deletePermissions
  test("add permissions to a project and delete them") {
    check {
      forAll {
        ( userTeamOrgPlat: (User.Create, Team.Create, Organization.Create, Platform),
          acr1: ObjectAccessControlRule,
          acr2: ObjectAccessControlRule,
          project: Project.Create,
          grantedUser: User.Create
        ) => {
          val projectDeletePermissionsIO = for {
            projMiscInsert <- fixUpProjMiscInsert(userTeamOrgPlat, project)
            (projectInsert, dbUserTeamOrgPlat) = projMiscInsert
            dbGrantedUser <- UserDao.create(grantedUser)
            dbUserGrantedTeamOrgPlat = dbUserTeamOrgPlat.copy(_1 = dbGrantedUser)
            acrInsert1 = fixUpObjectAcr(acr1, dbUserGrantedTeamOrgPlat)
            acrInsert2 = fixUpObjectAcr(acr2, dbUserGrantedTeamOrgPlat)
            permsAdded <- ProjectDao.addPermissionsMany(projectInsert.id, List(
              acrInsert1,
              acrInsert2
            ))
            permsDeleted <- ProjectDao.deletePermissions(projectInsert.id)
          } yield { (permsAdded, List(acrInsert1, acrInsert2).map(Some(_)), permsDeleted) }

          val (permsAdded, acrsAdded, permsDeleted) = projectDeletePermissionsIO.transact(xa).unsafeRunSync

          assert(permsAdded.diff(acrsAdded).length == 0,
            "Inserting permissions to a project should get them back")
          assert(permsDeleted.length == 0,
            "Deleting project permissions should get an empty list back")
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
          acr1: ObjectAccessControlRule,
          acr2: ObjectAccessControlRule,
          acr3: ObjectAccessControlRule,
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
            acrInsert1 = fixUpObjectAcr(acr1, dbUserGrantedTeamOrgPlat)
            acrInsert2 = fixUpObjectAcr(acr2, dbUserGrantedTeamOrgPlat)
            acrInsert3 = fixUpObjectAcr(acr3, dbUserGrantedTeamOrgPlat)
            permissions <- ProjectDao.addPermissionsMany(projectInsert.id, List(
              acrInsert1,
              acrInsert2,
              acrInsert3
            ))
            actions <- ProjectDao.listUserActions(dbGrantedUser, projectInsert.id)
          } yield { (actions, List(acrInsert1, acrInsert2, acrInsert3)) }

          val (userActions, acrsList) = userActionsIO.transact(xa).unsafeRunSync

          val acrActionsDistinct = acrsList.map(_.actionType.toString).distinct

          assert(
            acrActionsDistinct.diff(userActions).length == 0,
            "Listing actions granted to a user on a project should return all action string")
          true
        }
      }
    }
  }
}
