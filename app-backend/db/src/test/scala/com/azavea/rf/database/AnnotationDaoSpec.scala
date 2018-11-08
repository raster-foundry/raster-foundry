package com.rasterfoundry.database

import com.rasterfoundry.datamodel._
import com.rasterfoundry.datamodel.Generators.Implicits._
import com.rasterfoundry.database.Implicits._

import doobie._, doobie.implicits._
import doobie.postgres._, doobie.postgres.implicits._
import doobie.scalatest.imports._
import cats._, cats.data._, cats.effect.IO
import cats.syntax.either._
import org.scalacheck.Prop.forAll
import org.scalatest._
import org.scalatest.prop.Checkers

import java.util.UUID

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
         project: Project.Create,
         annotations: List[Annotation.Create]) =>
          {
            val annotationsInsertIO = insertUserOrgProject(user, org, project) flatMap {
              case (dbOrg: Organization, dbUser: User, dbProject: Project) => {
                AnnotationDao.insertAnnotations(
                  annotations,
                  dbProject.id,
                  dbUser
                )
              }
            }
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
         project: Project.Create,
         annotations: List[Annotation.Create],
         labelerC: User.Create) =>
          {
            val annotationsInsertIO = for {
              oupInsert <- insertUserOrgProject(user, org, project)
              (dbOrg, dbUser, dbProject) = oupInsert
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
         project: Project.Create,
         annotations: List[Annotation.Create]) =>
          {
            val annotationsInsertWithUserAndProjectIO = insertUserOrgProject(
              user,
              org,
              project) flatMap {
              case (dbOrg: Organization, dbUser: User, dbProject: Project) => {
                AnnotationDao.insertAnnotations(
                  annotations,
                  dbProject.id,
                  dbUser
                ) map {
                  (dbUser, dbProject, _)
                }
              }
            }
            val annotationsListForProjectIO = annotationsInsertWithUserAndProjectIO flatMap {
              case (dbUser: User,
                    dbProject: Project,
                    annotations: List[Annotation]) => {
                AnnotationDao.listAnnotationsForProject(dbProject.id) map {
                  (annotations, _)
                }
              }
            }
            val (insertedAnnotations, annotationsForProject) =
              xa.use(t => annotationsListForProjectIO.transact(t)).unsafeRunSync

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
         project: Project.Create,
         annotationInsert: Annotation.Create,
         annotationUpdate: Annotation.Create,
         verifierCreate: User.Create) =>
          {
            val annotationInsertWithUserAndProjectIO = for {
              oupInsert <- insertUserOrgProject(user, org, project)
              (dbOrg, dbUser, dbProject) = oupInsert
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
                    firstAnnotation.annotationGroup
                  )
                  .copy(id = annotationId)
                AnnotationDao.updateAnnotation(newAnnotation, dbUser) flatMap {
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
         project: Project.Create,
         annotations: List[Annotation.Create]) =>
          {
            val annotationsLabelsIO = insertUserOrgProject(user, org, project) flatMap {
              case (dbOrg: Organization, dbUser: User, dbProject: Project) => {
                AnnotationDao.insertAnnotations(
                  annotations,
                  dbProject.id,
                  dbUser
                ) flatMap { _ =>
                  AnnotationDao.listProjectLabels(dbProject.id)
                }
              }
            }

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
