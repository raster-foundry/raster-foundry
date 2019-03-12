package com.rasterfoundry.database

import com.rasterfoundry.common.datamodel._
import com.rasterfoundry.common.datamodel.Generators.Implicits._
import com.rasterfoundry.database.Implicits._

import doobie.implicits._
import doobie.postgres.implicits._
import org.scalacheck.Prop.forAll
import org.scalatest._
import org.scalatest.prop.Checkers

class AnnotationGroupDaoSpec
    extends FunSuite
    with Matchers
    with Checkers
    with DBTestConfig
    with PropTestHelpers {

  test("insert annotation group") {
    check {
      forAll {
        (user: User.Create,
         org: Organization.Create,
         platform: Platform,
         project: Project.Create,
         annotationGroupCreate: AnnotationGroup.Create) =>
          {
            val annotationGroupIO = for {
              (dbUser, _, _, dbProject) <- insertUserOrgPlatProject(
                user,
                org,
                platform,
                project
              )
              annotationGroup <- AnnotationGroupDao.createAnnotationGroup(
                dbProject.id,
                annotationGroupCreate,
                dbUser)
            } yield annotationGroup
            val annotationGroup = xa
              .use(t =>
                annotationGroupIO
                  .transact(t))
              .unsafeRunSync()
            assert(annotationGroup.name == annotationGroupCreate.name,
                   "; Annotation group should be inserted")
            true
          }
      }
    }
  }

  test("list project annotation groups") {
    check {
      forAll {
        (user: User.Create,
         org: Organization.Create,
         platform: Platform,
         project1: Project.Create,
         project2: Project.Create,
         agc1: AnnotationGroup.Create,
         agc2: AnnotationGroup.Create,
         agc3: AnnotationGroup.Create) =>
          {
            val annotationGroupIO = for {
              (dbUser, _, _, dbProject1) <- insertUserOrgPlatProject(user,
                                                                     org,
                                                                     platform,
                                                                     project1)
              dbProject2 <- ProjectDao.insertProject(project2, dbUser)
              agDb1 <- AnnotationGroupDao.createAnnotationGroup(dbProject1.id,
                                                                agc1,
                                                                dbUser)
              agDb2 <- AnnotationGroupDao.createAnnotationGroup(dbProject1.id,
                                                                agc2,
                                                                dbUser)
              agDb3 <- AnnotationGroupDao.createAnnotationGroup(dbProject2.id,
                                                                agc3,
                                                                dbUser)
              p1AnnotationGroups <- AnnotationGroupDao
                .listForProject(dbProject1.id)
              p2AnnotationGroups <- AnnotationGroupDao
                .listForProject(dbProject2.id)
            } yield
              (agDb1, agDb2, agDb3, p1AnnotationGroups, p2AnnotationGroups)

            val (_, _, _, p1ag, p2ag) =
              xa.use(t => annotationGroupIO.transact(t)).unsafeRunSync()

            assert(p1ag.length == 2 && p2ag.length == 1,
                   "; annotation groups are filters to project")
            true
          }
      }
    }
  }

  test("delete annotation group and associated annotations") {
    check {
      forAll {
        (user: User.Create,
         org: Organization.Create,
         platform: Platform,
         project1: Project.Create,
         agc1: AnnotationGroup.Create,
         agc2: AnnotationGroup.Create,
         agc1Annotations: List[Annotation.Create],
         agc2Annotations: List[Annotation.Create]) =>
          {
            val annotationGroupIO = for {
              (dbUser, _, _, dbProject) <- insertUserOrgPlatProject(user,
                                                                    org,
                                                                    platform,
                                                                    project1)
              agDb1 <- AnnotationGroupDao.createAnnotationGroup(dbProject.id,
                                                                agc1,
                                                                dbUser)
              agDb2 <- AnnotationGroupDao.createAnnotationGroup(dbProject.id,
                                                                agc2,
                                                                dbUser)
              ag1annotations <- AnnotationDao.insertAnnotations(
                agc1Annotations.map(_.copy(annotationGroup = Some(agDb1.id))),
                dbProject.id,
                dbUser)
              ag2annotations <- AnnotationDao.insertAnnotations(
                agc2Annotations.map(_.copy(annotationGroup = Some(agDb2.id))),
                dbProject.id,
                dbUser)
              deleteCount <- AnnotationGroupDao.deleteAnnotationGroup(
                dbProject.id,
                agDb1.id)
              projectAnnotations <- AnnotationDao.query
                .filter(fr"project_id=${dbProject.id}")
                .list
              projectAnnotationGroups <- AnnotationGroupDao
                .listForProject(dbProject.id)
            } yield
              (ag1annotations,
               ag2annotations,
               projectAnnotations,
               projectAnnotationGroups,
               deleteCount)

            val (_,
                 remainingAnnotations,
                 projectAnnotations,
                 projectAnnotationGroups,
                 deleteCount) =
              xa.use(t => annotationGroupIO.transact(t)).unsafeRunSync()

            assert(
              deleteCount == 1,
              "; annotation group delete query deleted an annotation group")
            assert(projectAnnotations.length == remainingAnnotations.length,
                   "; Deleting the annotation group also deletes annotations")
            assert(
              projectAnnotationGroups.length == 2,
              "; Project has default 'Annotations' group and remaining created annotation group")
            true
          }
      }
    }
  }

  test("retrieve annotation summary for a group") {
    check {
      forAll {
        (user: User.Create,
         org: Organization.Create,
         platform: Platform,
         project: Project.Create,
         ag: AnnotationGroup.Create,
         agAnnotations: List[Annotation.Create]) =>
          {
            val annotationGroupIO = for {
              (dbUser, _, _, dbProject) <- insertUserOrgPlatProject(user,
                                                                    org,
                                                                    platform,
                                                                    project)
              annotationGroupDB <- AnnotationGroupDao.createAnnotationGroup(
                dbProject.id,
                ag,
                dbUser)
              annotationsDB <- AnnotationDao.insertAnnotations(
                agAnnotations.map(
                  _.copy(annotationGroup = Some(annotationGroupDB.id))),
                dbProject.id,
                dbUser)
              projectAnnotations <- AnnotationDao.query
                .filter(fr"project_id=${dbProject.id}")
                .list
              projectAnnotationGroups <- AnnotationGroupDao
                .listForProject(dbProject.id)
              annotationGroupSummary <- AnnotationGroupDao
                .getAnnotationGroupSummary(dbProject.id, annotationGroupDB.id)
            } yield (annotationGroupSummary, annotationsDB)

            val (annotationGroupSummary, annotationsDB) =
              xa.use(t => annotationGroupIO.transact(t)).unsafeRunSync()

            assert(annotationGroupSummary.length > 0,
                   "; No summary produced for annotation group")

            val annotationLabelSet = annotationsDB.map(_.label).toSet

            annotationLabelSet.map { label =>
              val annotationCount =
                annotationGroupSummary
                  .find(_.label == label)
                  .get
                  .counts
                  .as[Map[String, Int]]
                  .right
                  .get
                  .map(_._2)
                  .foldLeft(0)(_ + _)
              assert(annotationCount === annotationsDB
                       .filter(_.label == label)
                       .length,
                     "; Count of car qualities did not equal number inserted")
            }

            true
          }
      }
    }
  }

  test("Creating annotations on a project with no annotation groups") {
    check {
      forAll {
        (user: User.Create,
         org: Organization.Create,
         platform: Platform,
         project1: Project.Create,
         annotations: List[Annotation.Create]) =>
          {
            val annotationGroupIO = for {
              (dbUser, _, _, dbProject) <- insertUserOrgPlatProject(user,
                                                                    org,
                                                                    platform,
                                                                    project1)
              dbAnnotations <- AnnotationDao.insertAnnotations(
                annotations,
                dbProject.id,
                dbUser
              )
              projectAnnotations <- AnnotationDao.query
                .filter(fr"project_id=${dbProject.id}")
                .list
              projectAnnotationGroups <- AnnotationGroupDao
                .listForProject(dbProject.id)
              updatedProject <- ProjectDao.unsafeGetProjectById(dbProject.id)
            } yield (projectAnnotationGroups, updatedProject)

            val (projectAnnotationGroups, project) =
              xa.use(t => annotationGroupIO.transact(t)).unsafeRunSync()

            assert(
              projectAnnotationGroups.length == 1,
              "; Project has an annotation group created on it automatically")
            val defaultAnnotationGroup = projectAnnotationGroups.head
            assert(
              Some(defaultAnnotationGroup.id) == project.defaultAnnotationGroup,
              "; Automatically created annotation group is set as project default")
            assert(defaultAnnotationGroup.name == "Annotations",
                   "; Default annotation group is named 'Annotations'")
            true
          }
      }
    }
  }
}
