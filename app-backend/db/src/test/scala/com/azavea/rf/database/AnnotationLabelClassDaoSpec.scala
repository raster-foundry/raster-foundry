package com.rasterfoundry.database

import com.rasterfoundry.common.Generators.Implicits._
import com.rasterfoundry.datamodel._

import cats.implicits._
import doobie._
import doobie.implicits._
import org.scalacheck.Prop.forAll
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.Checkers

import java.util.UUID

class AnnotationLabelClassDaoSpec
    extends AnyFunSuite
    with Matchers
    with Checkers
    with DBTestConfig
    with PropTestHelpers {

  test("list annotation classes for a label class group") {
    check {
      forAll(
        (
            userCreate: User.Create,
            annotationProjectCreate: AnnotationProject.Create
        ) => {

          val toInsert = annotationProjectCreate.copy(
            labelClassGroups = annotationProjectCreate.labelClassGroups.take(1)
          )

          val listIO = for {
            user <- UserDao.create(userCreate)
            inserted <- AnnotationProjectDao.insert(toInsert, user)
            labelClassGroup = inserted.labelClassGroups.head
            listedReal <-
              AnnotationLabelClassDao
                .listAnnotationLabelClassByGroupId(
                  labelClassGroup.id
                )
            listedBogus <-
              AnnotationLabelClassDao
                .listAnnotationLabelClassByGroupId(
                  UUID.randomUUID
                )
          } yield { (listedReal, listedBogus) }

          val (listedReal, listedBogus) =
            listIO.transact(xa).unsafeRunSync

          val expectedNames =
            (toInsert.labelClassGroups flatMap { group =>
              group.classes map { _.name }
            }).toSet

          val labelClassCreate = toInsert.labelClassGroups
            .map(_.classes)
            .flatten
            .toSet

          val insertedLabelClassCreate = listedReal
            .map(alc =>
              AnnotationLabelClass.Create(
                alc.name,
                alc.colorHexCode,
                alc.default,
                alc.determinant,
                alc.index,
                alc.geometryType,
                alc.description
              )
            )
            .toSet

          assert(
            labelClassCreate == insertedLabelClassCreate,
            "Label classes to be inserted are the same as those after inserting"
          )

          assert(
            expectedNames == (listedReal map { _.name }).toSet,
            "Class names to create match those listed for real label class group"
          )
          assert(
            expectedNames == (listedReal map { _.name }).toSet,
            "Class names to create match those listed for real label class group"
          )
          assert(
            Set.empty[String] == (listedBogus map { _.name }).toSet,
            "Bogus id lists no annotation label classes"
          )
          true
        }
      )
    }
  }

  test("get annotation class by id") {
    check {
      forAll(
        (
            userCreate: User.Create,
            annotationProjectCreate: AnnotationProject.Create
        ) => {
          val toInsert = annotationProjectCreate.copy(
            labelClassGroups = annotationProjectCreate.labelClassGroups.take(1)
          )
          val getIO: ConnectionIO[Option[AnnotationLabelClass]] = for {
            user <- UserDao.create(userCreate)
            inserted <- AnnotationProjectDao.insert(toInsert, user)
            labelClassOpt = inserted.labelClassGroups.headOption flatMap {
              group =>
                group.labelClasses.headOption
            }
            dbLabelClass <- labelClassOpt flatTraverse { labelClass =>
              AnnotationLabelClassDao.getById(labelClass.id)
            }
          } yield dbLabelClass

          val dbLabelClass: Option[AnnotationLabelClass] =
            getIO.transact(xa).unsafeRunSync

          val insertedLabelClassCreate: Option[AnnotationLabelClass.Create] =
            dbLabelClass
              .map(alc =>
                AnnotationLabelClass.Create(
                  alc.name,
                  alc.colorHexCode,
                  alc.default,
                  alc.determinant,
                  alc.index,
                  alc.geometryType,
                  alc.description
                )
              )

          val labelClassCreate: Set[Option[AnnotationLabelClass.Create]] =
            (annotationProjectCreate.labelClassGroups flatMap { group =>
              group.classes.map(cls => Option(cls))
            }).toSet

          assert(
            labelClassCreate contains insertedLabelClassCreate,
            "Getting a label class works"
          )

          true
        }
      )
    }
  }

  test("update annotation label class by id") {
    check {
      forAll(
        (
            userCreate: User.Create,
            annotationProjectCreate: AnnotationProject.Create,
            annotationLabelClassCreate: AnnotationLabelClass.Create,
            labelClassGroupCreate: AnnotationLabelClassGroup.Create
        ) => {
          val toInsert = annotationProjectCreate.copy(
            labelClassGroups = annotationProjectCreate.labelClassGroups.take(1)
          )
          val labelClassToUpdate = AnnotationLabelClass(
            id = UUID.randomUUID(),
            name = annotationLabelClassCreate.name,
            annotationLabelClassGroupId = UUID.randomUUID(),
            colorHexCode = annotationLabelClassCreate.colorHexCode,
            default = annotationLabelClassCreate.default,
            determinant = annotationLabelClassCreate.determinant,
            index = annotationLabelClassCreate.index,
            geometryType = annotationLabelClassCreate.geometryType,
            description = annotationLabelClassCreate.description
          )
          val updateIO = for {
            user <- UserDao.create(userCreate)
            inserted <- AnnotationProjectDao.insert(toInsert, user)
            dbLabelClassOpt = inserted.labelClassGroups.headOption flatMap {
              group =>
                group.labelClasses.headOption
            }
            groups <- AnnotationLabelClassGroupDao.listByProjectId(inserted.id)
            newGroup <-
              AnnotationLabelClassGroupDao.insertAnnotationLabelClassGroup(
                labelClassGroupCreate,
                Some(inserted.toProject),
                None,
                groups.size
              )
            _ <- dbLabelClassOpt traverse { labelClass =>
              AnnotationLabelClassDao.update(
                labelClass.id,
                labelClassToUpdate.copy(annotationLabelClassGroupId =
                  newGroup.id
                )
              )
            }
            dbLabelClassUpdatedOpt <- dbLabelClassOpt flatTraverse {
              labelClass => AnnotationLabelClassDao.getById(labelClass.id)
            }
          } yield { (newGroup, dbLabelClassOpt, dbLabelClassUpdatedOpt) }

          val (newLabelGroup, labelClassOpt, labelClassUpdatedOpt) =
            updateIO.transact(xa).unsafeRunSync

          assert(
            (labelClassOpt, labelClassUpdatedOpt).tupled match {
              case Some((labelClass, labelClassUpdated)) =>
                labelClassUpdated.isActive == labelClass.isActive &&
                  labelClassUpdated.colorHexCode == labelClassToUpdate.colorHexCode &&
                  labelClassUpdated.default == labelClassToUpdate.default &&
                  labelClassUpdated.description == labelClassToUpdate.description &&
                  labelClassUpdated.determinant == labelClassToUpdate.determinant &&
                  labelClassUpdated.geometryType == labelClassToUpdate.geometryType &&
                  labelClassUpdated.index == labelClassToUpdate.index &&
                  labelClassUpdated.name == labelClassToUpdate.name &&
                  labelClassUpdated.annotationLabelClassGroupId == newLabelGroup.id
              case None if annotationProjectCreate.labelClassGroups.size == 0 =>
                true
              case _ => false
            },
            "Updating a label class updates fields other than group ID, isActive and label group ID"
          )
          true
        }
      )
    }
  }

  test("label class deactivation and activation") {
    check {
      forAll(
        (
            userCreate: User.Create,
            annotationProjectCreate: AnnotationProject.Create
        ) => {
          val deactivateIO = for {
            user <- UserDao.create(userCreate)
            inserted <- AnnotationProjectDao.insert(
              annotationProjectCreate.copy(
                labelClassGroups =
                  annotationProjectCreate.labelClassGroups.take(1)
              ),
              user
            )
            dbLabelClassOpt = inserted.labelClassGroups.headOption flatMap {
              _.labelClasses.headOption
            }
            _ <- dbLabelClassOpt traverse { labelClass =>
              AnnotationLabelClassDao.deactivate(labelClass.id)
            }
            dbLabelClassDeactivatedOpt <- dbLabelClassOpt flatTraverse {
              labelClass => AnnotationLabelClassDao.getById(labelClass.id)
            }
            _ <- dbLabelClassOpt traverse { labelClass =>
              AnnotationLabelClassDao.activate(labelClass.id)
            }
            dbLabelClassActivatedOpt <- dbLabelClassOpt flatTraverse {
              labelClass => AnnotationLabelClassDao.getById(labelClass.id)
            }
          } yield { (dbLabelClassDeactivatedOpt, dbLabelClassActivatedOpt) }

          val (labelClassDeactivatedOpt, labelClassActivatedOpt) =
            deactivateIO.transact(xa).unsafeRunSync

          assert(
            (labelClassDeactivatedOpt, labelClassActivatedOpt).tupled match {
              case Some((deactivated, activated)) =>
                !deactivated.isActive && activated.isActive
              case None if annotationProjectCreate.labelClassGroups.size == 0 =>
                true
              case _ => false
            },
            "Label class activation and deactivation work"
          )
          true
        }
      )
    }
  }
}
