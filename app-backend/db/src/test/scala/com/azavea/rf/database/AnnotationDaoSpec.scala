package com.rasterfoundry.database

import com.rasterfoundry.common.datamodel._
import com.rasterfoundry.common.datamodel.Generators.Implicits._

import doobie._, doobie.implicits._
import cats.effect.IO
import org.scalacheck.Prop.forAll
import org.scalatest._
import org.scalatest.prop.Checkers

class AnnotationDaoSpec
    extends FunSuite
    with Matchers
    with Checkers
    with DBTestConfig
    with PropTestHelpers {

  test("insert annotations") {
    check {
      forAll {
        (user: User.Create,
         org: Organization.Create,
         platform: Platform,
         project: Project.Create,
         annotations: List[Annotation.Create]) =>
          {
            val annotationsInsertIO = for {
              (dbUser, _, _, dbProject) <- insertUserOrgPlatProject(user,
                                                                    org,
                                                                    platform,
                                                                    project)
              annotations <- AnnotationDao.insertAnnotations(annotations,
                                                             dbProject.id,
                                                             dbUser)
            } yield annotations

            xa.use((t: Transactor[IO]) =>
                annotationsInsertIO
                  .transact(t))
              .unsafeRunSync
              .length == annotations.length
          }
      }
    }
  }

  test("insert annotations created by a labeler") {
    check {
      forAll {
        (user: User.Create,
         org: Organization.Create,
         platform: Platform,
         project: Project.Create,
         annotations: List[Annotation.Create],
         labelerC: User.Create) =>
          {
            val annotationsInsertIO = for {
              (dbUser, _, _, dbProject) <- insertUserOrgPlatProject(user,
                                                                    org,
                                                                    platform,
                                                                    project)
              labeler <- UserDao.create(labelerC)
              insertedAnnotations <- AnnotationDao.insertAnnotations(
                annotations.map(annotationCreate =>
                  annotationCreate.copy(labeledBy = Some(labeler.id))),
                dbProject.id,
                dbUser)
            } yield (insertedAnnotations, labeler)

            val (insertedAnnotations, labeler) =
              xa.use(t => annotationsInsertIO.transact(t)).unsafeRunSync

            insertedAnnotations.length == annotations.length &&
            insertedAnnotations.flatMap(_.labeledBy).distinct(0) === labeler.id
          }
      }
    }
  }

  test("list annotations") {
    xa.use(t => AnnotationDao.query.list.transact(t))
      .unsafeRunSync
      .length should be >= 0
  }

  test("list annotations for project") {
    check {
      forAll {
        (user: User.Create,
         org: Organization.Create,
         platform: Platform,
         project: Project.Create,
         annotations: List[Annotation.Create]) =>
          {
            val annotationsIO = for {
              (dbUser, _, _, dbProject) <- insertUserOrgPlatProject(user,
                                                                    org,
                                                                    platform,
                                                                    project)
              inserted <- AnnotationDao.insertAnnotations(
                annotations,
                dbProject.id,
                dbUser
              )
              forProject <- AnnotationDao.listAnnotationsForProject(
                dbProject.id)
            } yield { (inserted, forProject) }
            val (insertedAnnotations, annotationsForProject) =
              xa.use(t => annotationsIO.transact(t)).unsafeRunSync

            insertedAnnotations.toSet == annotationsForProject.toSet
          }
      }
    }
  }

  test("update an annotation verified by a verifier") {
    check {
      forAll {
        (user: User.Create,
         org: Organization.Create,
         platform: Platform,
         project: Project.Create,
         annotationInsert: Annotation.Create,
         annotationUpdate: Annotation.Create,
         verifierCreate: User.Create) =>
          {
            val annotationInsertWithUserAndProjectIO = for {
              (dbUser, _, _, dbProject) <- insertUserOrgPlatProject(user,
                                                                    org,
                                                                    platform,
                                                                    project)
              annotations <- AnnotationDao.insertAnnotations(
                List(annotationInsert),
                dbProject.id,
                dbUser)
              verifier <- UserDao.create(verifierCreate)
            } yield (annotations, dbUser, dbProject, verifier)

            val annotationsUpdateWithAnnotationIO = annotationInsertWithUserAndProjectIO flatMap {
              case (annotations: List[Annotation],
                    dbUser: User,
                    dbProject: Project,
                    verifier: User) => {
                // safe because it's coming from inserting an annotation above
                val firstAnnotation = annotations.head
                val annotationId = firstAnnotation.id
                val newAnnotation = annotationUpdate
                  .copy(verifiedBy = Some(verifier.id))
                  .toAnnotation(
                    dbProject.id,
                    dbUser,
                    firstAnnotation.annotationGroup,
                    dbProject.defaultLayerId
                  )
                  .copy(id = annotationId)
                AnnotationDao.updateAnnotation(dbProject.id,
                                               newAnnotation,
                                               dbUser) flatMap {
                  (affectedRows: Int) =>
                    {
                      AnnotationDao.unsafeGetAnnotationById(annotationId) map {
                        (affectedRows, _, verifier)
                      }
                    }
                }
              }
            }

            val (affectedRows, updatedAnnotation, verifier) = xa
              .use(t => annotationsUpdateWithAnnotationIO.transact(t))
              .unsafeRunSync

            affectedRows == 1 &&
            updatedAnnotation.label == annotationUpdate.label &&
            updatedAnnotation.description == annotationUpdate.description &&
            updatedAnnotation.machineGenerated == annotationUpdate.machineGenerated &&
            updatedAnnotation.confidence == annotationUpdate.confidence &&
            updatedAnnotation.quality == annotationUpdate.quality &&
            updatedAnnotation.geometry == annotationUpdate.geometry &&
            updatedAnnotation.verifiedBy == Some(verifier.id)
          }
      }
    }
  }

  test("list project labels") {
    check {
      forAll {

        (user: User.Create,
         org: Organization.Create,
         platform: Platform,
         project: Project.Create,
         annotations: List[Annotation.Create]) =>
          {
            val annotationsLabelsIO = for {
              (dbUser, _, _, dbProject) <- insertUserOrgPlatProject(user,
                                                                    org,
                                                                    platform,
                                                                    project)
              _ <- AnnotationDao.insertAnnotations(
                annotations,
                dbProject.id,
                dbUser
              )
              labels <- AnnotationDao.listProjectLabels(dbProject.id)
            } yield labels

            xa.use(
                t => annotationsLabelsIO.transact(t)
              )
              .unsafeRunSync
              .toSet ==
              (annotations.toSet map { (annotation: Annotation.Create) =>
                annotation.label
              })
          }
      }
    }
  }
}
