package com.rasterfoundry.database

import com.rasterfoundry.common.Generators.Implicits._
import com.rasterfoundry.datamodel._

import cats.implicits._
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
                alc.description,
                alc.isActive
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
          val getIO = for {
            user <- UserDao.create(userCreate)
            inserted <- AnnotationProjectDao.insert(toInsert, user)
            labelClassOpt =
              inserted.labelClassGroups.head.labelClasses.headOption
            dbLabelClass <- labelClassOpt flatTraverse { labelClass =>
              AnnotationLabelClassDao.getById(labelClass.id)
            }
          } yield dbLabelClass

          val dbLabelClass = getIO.transact(xa).unsafeRunSync

          val insertedLabelClassCreate = dbLabelClass
            .map(alc =>
              AnnotationLabelClass.Create(
                alc.name,
                alc.colorHexCode,
                alc.default,
                alc.determinant,
                alc.index,
                alc.geometryType,
                alc.description,
                alc.isActive
              )
            )

          val labelClassCreate =
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
            annotationLabelClassCreate: AnnotationLabelClass.Create
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
            description = annotationLabelClassCreate.description,
            isActive = annotationLabelClassCreate.isActive
          )
          val updateIO = for {
            user <- UserDao.create(userCreate)
            inserted <- AnnotationProjectDao.insert(toInsert, user)
            dbLabelClassOpt =
              inserted.labelClassGroups.head.labelClasses.headOption
            _ <- dbLabelClassOpt traverse { labelClass =>
              AnnotationLabelClassDao.update(labelClass.id, labelClassToUpdate)
            }
            dbLabelClassUpdatedOpt <- dbLabelClassOpt flatTraverse {
              labelClass => AnnotationLabelClassDao.getById(labelClass.id)
            }
          } yield { (dbLabelClassOpt, dbLabelClassUpdatedOpt) }

          val (labelClassOpt, labelClassUpdatedOpt) =
            updateIO.transact(xa).unsafeRunSync

          assert(
            (labelClassOpt, labelClassUpdatedOpt).tupled match {
              case Some((labelClass, labelClassUpdated)) =>
                labelClass.annotationLabelClassGroupId == labelClassUpdated.annotationLabelClassGroupId &&
                  labelClassUpdated.isActive == labelClass.isActive &&
                  labelClassUpdated.colorHexCode == labelClassToUpdate.colorHexCode &&
                  labelClassUpdated.default == labelClassToUpdate.default &&
                  labelClassUpdated.description == labelClassToUpdate.description &&
                  labelClassUpdated.determinant == labelClassToUpdate.determinant &&
                  labelClassUpdated.geometryType == labelClassToUpdate.geometryType &&
                  labelClassUpdated.index == labelClassToUpdate.index &&
                  labelClassUpdated.name == labelClassToUpdate.name
              case _ => true
            },
            "Updating a label class updates fields other than group ID and isActive status"
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
            dbLabelClassOpt =
              inserted.labelClassGroups.head.labelClasses.headOption
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
                deactivated.isActive == false && activated.isActive == true
              case _ => true
            },
            "Updating a label class updates fields other than group ID and isActive status"
          )
          true
        }
      )
    }
  }
}
