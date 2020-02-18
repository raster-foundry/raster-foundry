package com.rasterfoundry.database

import com.rasterfoundry.common.Generators.Implicits._
import com.rasterfoundry.datamodel._

import doobie._
import doobie.implicits._
import org.scalacheck.Prop.forAll
import org.scalatest._
import org.scalatestplus.scalacheck.Checkers

import java.util.UUID

class AnnotationLabelClassGroupDaoSpec
    extends FunSuite
    with Matchers
    with Checkers
    with DBTestConfig
    with PropTestHelpers {

  test("list annotations class groups for a project") {
    check {
      forAll(
        (
            userCreate: User.Create,
            annotationProjectCreate: AnnotationProject.Create
        ) =>{
          val insertIO: ConnectionIO[
            (List[AnnotationLabelClassGroup], List[AnnotationLabelClassGroup])
          ] = for {
            user <- UserDao.create(userCreate)
            inserted <- AnnotationProjectDao
              .insertAnnotationProject(annotationProjectCreate, user)
            listedReal <- AnnotationLabelClassGroupDao
              .listByProjectId(inserted.id)
            listedBogus <- AnnotationLabelClassGroupDao
              .listByProjectId(
                UUID.randomUUID
              )
          } yield { (listedReal, listedBogus) }

          val (listedReal, listedBogus) = insertIO.transact(xa).unsafeRunSync

          val expectedNames = (annotationProjectCreate.labelClassGroups map { _.name }).toSet

          assert(expectedNames === (listedReal map { _.name }).toSet,
            "Listed names for project id match names of groups to create")
          assert(Set.empty[String] === (listedBogus map { _.name }).toSet,
            "Bogus id lists no annotation label class groups")
          true
        }
      )
    }
  }

  test("delete annotations class groups for a project") {
    check {
      forAll(
        (
            userCreate: User.Create,
            annotationProjectCreate: AnnotationProject.Create
        ) => {
          val insertIO: ConnectionIO[(Int, Int)] = for {
            user <- UserDao.create(userCreate)
            inserted <- AnnotationProjectDao
              .insertAnnotationProject(annotationProjectCreate, user)
            deletedReal <- AnnotationLabelClassGroupDao
              .deleteByProjectId(inserted.id)
            deletedBogus <- AnnotationLabelClassGroupDao
              .deleteByProjectId(
                UUID.randomUUID
              )
          } yield { (deletedReal, deletedBogus) }

          val (deletedReal, deletedBogus) = insertIO.transact(xa).unsafeRunSync

          assert(deletedReal === annotationProjectCreate.labelClassGroups.length,
            "Deleted all annotation label class groups for real project id")
          assert(deletedBogus === 0,
            "Bogus id deletes no annotation label class groups")

          true
        }
      )
    }
  }
}
