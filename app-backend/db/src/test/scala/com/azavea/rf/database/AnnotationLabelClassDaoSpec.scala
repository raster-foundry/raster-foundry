package com.rasterfoundry.database

import com.rasterfoundry.common.Generators.Implicits._
import com.rasterfoundry.datamodel._

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
            listedReal <- AnnotationLabelClassDao
              .listAnnotationLabelClassByGroupId(
                labelClassGroup.id
              )
            listedBogus <- AnnotationLabelClassDao
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
            .map(
              alc =>
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
            labelClassCreate === insertedLabelClassCreate,
            "Label classes to be inserted are the same as those after inserting"
          )

          assert(
            expectedNames === (listedReal map { _.name }).toSet,
            "Class names to create match those listed for real label class group"
          )
          assert(
            expectedNames === (listedReal map { _.name }).toSet,
            "Class names to create match those listed for real label class group"
          )
          assert(
            Set.empty[String] === (listedBogus map { _.name }).toSet,
            "Bogus id lists no annotation label classes"
          )
          true
        }
      )
    }
  }
}
