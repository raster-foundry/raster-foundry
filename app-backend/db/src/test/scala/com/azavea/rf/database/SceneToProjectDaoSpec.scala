package com.azavea.rf.database

import com.azavea.rf.datamodel._
import com.azavea.rf.datamodel.Generators.Implicits._
import com.azavea.rf.database.Implicits._

import com.lonelyplanet.akka.http.extensions.{PageRequest, Order}

import doobie._, doobie.implicits._
import cats._, cats.data._, cats.effect.IO
import cats.implicits._
import doobie.postgres._, doobie.postgres.implicits._
import org.scalacheck.Prop.{forAll, exists}
import org.scalatest._
import org.scalatest.prop.Checkers

import java.sql.Timestamp
import java.time.LocalDate


class SceneToProjectDaoSpec extends FunSuite with Matchers with Checkers with DBTestConfig with PropTestHelpers {
  test("Insert scenes to projects and accept them") {
    check {
      forAll {
        (user: User.Create, org: Organization.Create, project: Project.Create, scenes: List[Scene.Create],
         dsCreate: Datasource.Create, page: PageRequest, csq: CombinedSceneQueryParams) => {
          val acceptedSceneAndStpIO = for {
            orgUserProject <- insertUserOrgProject(user, org, project)
            (dbOrg, dbUser, dbProject) = orgUserProject
            datasource <- DatasourceDao.create(dsCreate.toDatasource(dbUser), dbUser)
            scenesInsert <- (scenes map { fixupSceneCreate(dbUser, datasource, _) }).traverse(
              (scene: Scene.Create) => SceneDao.insert(scene, dbUser)
            )
            _ <- ProjectDao.addScenesToProject(scenesInsert map { _.id }, dbProject.id, false)
            acceptedSceneCount <- SceneToProjectDao.acceptScenes(dbProject.id, scenesInsert map { _.id })
            stps <- SceneToProjectDao.query.filter(fr"project_id = ${dbProject.id}").list
          } yield (acceptedSceneCount, stps)

          val (acceptedSceneCount, stps) = acceptedSceneAndStpIO.transact(xa).unsafeRunSync

          acceptedSceneCount == scenes.length &&
            stps.length == scenes.length &&
            stps.filter(_.accepted).length == scenes.length
        }
      }
    }
  }
}
