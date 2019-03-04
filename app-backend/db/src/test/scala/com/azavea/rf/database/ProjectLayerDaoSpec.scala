package com.rasterfoundry.database

import com.rasterfoundry.common.datamodel._
import com.rasterfoundry.common.datamodel.Generators.Implicits._

import com.lonelyplanet.akka.http.extensions.PageRequest

import doobie.implicits._
import io.circe.syntax._
import org.scalacheck.Prop.forAll
import org.scalatest._
import org.scalatest.prop.Checkers

class ProjectLayerDaoSpec
    extends FunSuite
    with Matchers
    with Checkers
    with DBTestConfig
    with PropTestHelpers {
  test("list project layers works") {
    check {
      forAll {
        (orgCreate: Organization.Create,
         userCreate: User.Create,
         projectCreate: Project.Create,
         projectLayerCreate: ProjectLayer.Create,
         pageRequest: PageRequest) =>
          {
            val insertAndListIO = for {
              (_, _, dbProject) <- insertUserOrgProject(userCreate,
                                                        orgCreate,
                                                        projectCreate)
              inserted <- ProjectLayerDao.insertProjectLayer(
                projectLayerCreate
                  .copy(projectId = Some(dbProject.id))
                  .toProjectLayer
              )
              listed <- ProjectLayerDao.listProjectLayersForProject(
                pageRequest,
                dbProject.id)
            } yield { (dbProject, inserted, listed) }

            val (project, inserted, listed) =
              xa.use(t => insertAndListIO.transact(t)).unsafeRunSync

            assert(
              (listed.results map { _.id } toSet) == Set(project.defaultLayerId,
                                                         inserted.id),
              "Default layer id and inserted layer id were not the only results in layers for project"
            )
            true
          }
      }
    }
  }

  test("update a project layer") {
    check {
      forAll {
        (orgCreate: Organization.Create,
         userCreate: User.Create,
         projectCreate: Project.Create,
         projectLayerCreate: ProjectLayer.Create) =>
          {
            val newSingleBandOptions = SingleBandOptions.Params(
              3,
              BandDataType.Diverging,
              80,
              "a good color scheme" asJson,
              "a legend orientation"
            )
            val updateIO = for {
              (_, _, dbProject) <- insertUserOrgProject(userCreate,
                                                        orgCreate,
                                                        projectCreate)
              inserted <- ProjectLayerDao.insertProjectLayer(
                projectLayerCreate
                  .copy(projectId = Some(dbProject.id))
                  .toProjectLayer
              )
              _ <- ProjectLayerDao.updateProjectLayer(
                inserted.copy(isSingleBand = true,
                              singleBandOptions = Some(newSingleBandOptions)),
                inserted.id
              )
              fetched <- ProjectLayerDao.unsafeGetProjectLayerById(inserted.id)
            } yield { (inserted, fetched) }

            val (inserted, fetched) =
              xa.use(t => updateIO.transact(t)).unsafeRunSync

            assert(
              inserted.isSingleBand == false && inserted.singleBandOptions != Some(
                newSingleBandOptions),
              "Originally inserted project layer for some reason had single band settings"
            )
            assert(
              fetched.isSingleBand == true && fetched.singleBandOptions == Some(
                newSingleBandOptions),
              "Update to set single band options was unsuccessful")
            true
          }
      }
    }
  }

}
