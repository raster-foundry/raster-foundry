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
              ProjectDao.insertProject(fixupProjectCreate(user, org, project), user)
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
              ProjectDao.insertProject(fixupProjectCreate(dbUser, dbOrg, insertProject), dbUser) map {
                (_, dbUser, dbOrg)
              }
            }
          }
          val updateProjectWithUpdatedIO = projInsertWithUserAndOrgIO flatMap {
            case (dbProject: Project, dbUser: User, dbOrg: Organization) => {
              val fixedUpUpdateProject = fixupProjectCreate(dbUser, dbOrg, updateProject).toProject(dbUser)
              ProjectDao.updateProject(fixedUpUpdateProject, dbProject.id, dbUser) flatMap {
                (affectedRows: Int) => {
                  ProjectDao.unsafeGetProjectById(dbProject.id, Some(dbUser)) map {
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
              ProjectDao.insertProject(fixupProjectCreate(dbUser, dbOrg, project), dbUser) map {
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
    ProjectDao.query.list.transact(xa).unsafeRunSync.length should be >= 0
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
                      SceneDao.insert(fixupSceneCreate(dbUser, dbOrg, dbDatasource, scene), dbUser)
                    }
                  )
                }
              }
              val projectInsertIO = ProjectDao.insertProject(fixupProjectCreate(dbUser, dbOrg, project), dbUser)
              (projectInsertIO, scenesInsertIO, dbUser.pure[ConnectionIO]).tupled
            }
          }

          val addScenesIO = projAndScenesInsertWithUserIO flatMap {
            case (dbProject: Project, dbScenes: List[Scene.WithRelated], dbUser: User) => {
              ProjectDao.addScenesToProject(
                // this.get is safe because the arbitrary instance only produces NELs
                (dbScenes map {_.id}).toNel.get,
                dbProject.id,
                dbUser
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
                      SceneDao.insert(fixupSceneCreate(dbUser, dbOrg, dbDatasource, scene), dbUser)
                    }
                  )
                }
              }
              val projectInsertIO = ProjectDao.insertProject(fixupProjectCreate(dbUser, dbOrg, project), dbUser)
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
                      SceneDao.insert(fixupSceneCreate(dbUser, dbOrg, dbDatasource, scene), dbUser)
                    }
                  )
                }
              }
              val projectInsertIO = ProjectDao.insertProject(fixupProjectCreate(dbUser, dbOrg, project), dbUser)
              (projectInsertIO, scenesInsertIO, dbUser.pure[ConnectionIO]).tupled
            }
          }

          val addAndDeleteScenesWithProjectAndUserIO = projAndScenesInsertWithUserIO flatMap {
            case (dbProject: Project, dbScenes: List[Scene.WithRelated], dbUser: User) => {
              val sceneIds = dbScenes map {_.id}
              ProjectDao.addScenesToProject(
                // this.get is safe because the arbitrary instance only produces NELs
                (dbScenes map {_.id}).toNel.get, dbProject.id, dbUser) flatMap {
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
}

