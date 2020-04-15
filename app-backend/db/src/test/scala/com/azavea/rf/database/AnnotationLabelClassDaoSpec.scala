package com.rasterfoundry.database

import com.rasterfoundry.common.Generators.Implicits._
import com.rasterfoundry.datamodel._

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

          val listIO: ConnectionIO[
            (List[AnnotationLabelClass], List[AnnotationLabelClass])
          ] = for {
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

          val (listedReal, listedBogus) = listIO.transact(xa).unsafeRunSync

          val expectedNames =
            (toInsert.labelClassGroups flatMap { group =>
              group.classes map { _.name }
            }).toSet

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
