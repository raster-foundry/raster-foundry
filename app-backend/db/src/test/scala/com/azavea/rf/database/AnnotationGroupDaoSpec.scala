package com.azavea.rf.database

import com.azavea.rf.datamodel._
import com.azavea.rf.datamodel.Generators.Implicits._
import com.azavea.rf.database.Implicits._

import doobie._, doobie.implicits._
import doobie.postgres._, doobie.postgres.implicits._
import doobie.scalatest.imports._
import cats._, cats.data._, cats.effect.IO
import cats.syntax.either._
import org.scalacheck.Prop.forAll
import org.scalatest._
import org.scalatest.prop.Checkers

import java.util.UUID

class AnnotationGroupDaoSpec extends FunSuite with Matchers with Checkers with DBTestConfig with PropTestHelpers {

  test("insert annotation group") {
    check {
      forAll {
        (user: User.Create, org: Organization.Create, project: Project.Create,
         annotationGroupCreate: AnnotationGroup.Create) => {
          val annotationGroupInsertWithUserAndProjectIO = insertUserOrgProject(user, org, project) flatMap {
            case (dbOrg: Organization, dbUser: User, dbProject: Project) => {
              AnnotationGroupDao.createAnnotationGroup(dbProject.id, annotationGroupCreate, dbUser) map {
                (_, dbUser, dbProject)
              }
            }
          }
          val (annotationGroup, dbUser, dbProject) = annotationGroupInsertWithUserAndProjectIO.transact(xa).unsafeRunSync()
          assert(annotationGroup.name == annotationGroupCreate.name, "; Annotation group should be inserted")
          true
        }
      }
    }
  }

  test("list project annotation groups") {
    check {
      forAll {
        (user: User.Create, org: Organization.Create,
         project1: Project.Create, project2: Project.Create,
         agc1: AnnotationGroup.Create, agc2: AnnotationGroup.Create, agc3: AnnotationGroup.Create) => {
          val annotationGroupIO = for {
            orgUserProject <- insertUserOrgProject(user, org, project1)
            (dbOrg, dbUser, dbProject1) = orgUserProject
            dbProject2 <- ProjectDao.insertProject(project2, dbUser)
            agDb1 <- AnnotationGroupDao.createAnnotationGroup(dbProject1.id, agc1, dbUser)
            agDb2 <- AnnotationGroupDao.createAnnotationGroup(dbProject1.id, agc2, dbUser)
            agDb3 <- AnnotationGroupDao.createAnnotationGroup(dbProject2.id, agc3, dbUser)
            p1AnnotationGroups <- AnnotationGroupDao.listAnnotationGroupsForProject(dbProject1.id)
            p2AnnotationGroups <- AnnotationGroupDao.listAnnotationGroupsForProject(dbProject2.id)
          } yield (agDb1, agDb2, agDb3, p1AnnotationGroups, p2AnnotationGroups)

          val (ag1, ag2, ag3, p1ag, p2ag) = annotationGroupIO.transact(xa).unsafeRunSync()

          assert(p1ag.length == 2 && p2ag.length == 1, "; annotation groups are filters to project")
          true
        }
      }
    }
  }

  test ("delete annotation group and associated annotations") {
    check {
      forAll {
        (user: User.Create, org: Organization.Create,
         project1: Project.Create,
         agc1: AnnotationGroup.Create, agc2: AnnotationGroup.Create,
         agc1Annotations: List[Annotation.Create], agc2Annotations: List[Annotation.Create]
        ) => {
          val annotationGroupIO = for {
            orgUserProject <- insertUserOrgProject(user, org, project1)
            (dbOrg, dbUser, dbProject) = orgUserProject
            agDb1 <- AnnotationGroupDao.createAnnotationGroup(dbProject.id, agc1, dbUser)
            agDb2 <- AnnotationGroupDao.createAnnotationGroup(dbProject.id, agc2, dbUser)
            ag1annotations <- AnnotationDao.insertAnnotations(
              agc1Annotations.map(_.copy(annotationGroup = Some(agDb1.id))), dbProject.id, dbUser)
            ag2annotations <- AnnotationDao.insertAnnotations(
              agc2Annotations.map(_.copy(annotationGroup = Some(agDb2.id))), dbProject.id, dbUser)
            deleteCount <- AnnotationGroupDao.deleteAnnotationGroup(dbProject.id, agDb1.id)
            projectAnnotations <- AnnotationDao.query.filter(fr"project_id=${dbProject.id}").list
            projectAnnotationGroups <- AnnotationGroupDao.listAnnotationGroupsForProject(dbProject.id)
          } yield (ag1annotations, ag2annotations, projectAnnotations, projectAnnotationGroups, deleteCount)

          val (deletedAnnotations, remainingAnnotations, projectAnnotations, projectAnnotationGroups, deleteCount) = annotationGroupIO.transact(xa).unsafeRunSync()

          assert(deleteCount == 1, "; annotation group delete query deleted an annotation group")
          assert(projectAnnotations.length == remainingAnnotations.length, "; Deleting the annotation group also deletes annotations")
          assert(projectAnnotationGroups.length == 2, "; Project has default 'Annotations' group and remaining created annotation group")
          true
        }
      }
    }
  }

  test ("Creating annotations on a project with no annotation groups") {
    check {
      forAll {
        (user: User.Create, org: Organization.Create,
         project1: Project.Create,
         annotations: List[Annotation.Create]
        ) => {
          val annotationGroupIO = for {
            orgUserProject <- insertUserOrgProject(user, org, project1)
            (dbOrg, dbUser, dbProject) = orgUserProject
            dbAnnotations <- AnnotationDao.insertAnnotations(
              annotations, dbProject.id, dbUser
            )
            projectAnnotations <- AnnotationDao.query.filter(fr"project_id=${dbProject.id}").list
            projectAnnotationGroups <- AnnotationGroupDao.listAnnotationGroupsForProject(dbProject.id)
            updatedProject <- ProjectDao.unsafeGetProjectById(dbProject.id)
          } yield (projectAnnotationGroups, updatedProject)

          val (projectAnnotationGroups, project) = annotationGroupIO.transact(xa).unsafeRunSync()

          assert(projectAnnotationGroups.length == 1, "; Project has an annotation group created on it automatically")
          val defaultAnnotationGroup = projectAnnotationGroups.head
          assert(Some(defaultAnnotationGroup.id) == project.defaultAnnotationGroup, "; Automatically created annotation group is set as project default")
          assert(defaultAnnotationGroup.name == "Annotations", "; Default annotation group is named 'Annotations'")
          true
        }
      }
    }
  }
}
