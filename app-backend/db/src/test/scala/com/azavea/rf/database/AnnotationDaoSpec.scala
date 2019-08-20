package com.rasterfoundry.database

import com.rasterfoundry.datamodel._
import com.rasterfoundry.common.Generators.Implicits._

import doobie.implicits._
import org.scalacheck.Prop.forAll
import org.scalatest._
import org.scalatestplus.scalacheck.Checkers
import com.rasterfoundry.datamodel.PageRequest
import java.util.UUID
import io.circe.syntax._

class AnnotationDaoSpec
    extends FunSuite
    with Matchers
    with Checkers
    with DBTestConfig
    with PropTestHelpers {

  test("insert annotations") {
    check {
      forAll {
        (
            user: User.Create,
            org: Organization.Create,
            platform: Platform,
            project: Project.Create,
            annotations: List[Annotation.Create]
        ) =>
          {
            val annotationsInsertIO = for {
              (dbUser, _, _, dbProject) <- insertUserOrgPlatProject(
                user,
                org,
                platform,
                project
              )
              annotations <- AnnotationDao.insertAnnotations(
                annotations,
                dbProject.id,
                dbUser
              )
            } yield annotations
            annotationsInsertIO
              .transact(xa)
              .unsafeRunSync
              .length == annotations.length
          }
      }
    }
  }

  test("insert annotations created by a labeler") {
    check {
      forAll {
        (
            user: User.Create,
            org: Organization.Create,
            platform: Platform,
            project: Project.Create,
            annotations: List[Annotation.Create],
            labelerC: User.Create
        ) =>
          {
            val annotationsInsertIO = for {
              (dbUser, _, _, dbProject) <- insertUserOrgPlatProject(
                user,
                org,
                platform,
                project
              )
              labeler <- UserDao.create(labelerC)
              insertedAnnotations <- AnnotationDao.insertAnnotations(
                annotations.map(
                  annotationCreate =>
                    annotationCreate.copy(labeledBy = Some(labeler.id))
                ),
                dbProject.id,
                dbUser
              )
            } yield (insertedAnnotations, labeler)

            val (insertedAnnotations, labeler) =
              annotationsInsertIO.transact(xa).unsafeRunSync

            insertedAnnotations.length == annotations.length &&
            insertedAnnotations.flatMap(_.labeledBy).distinct(0) === labeler.id
          }
      }
    }
  }

  test("list annotations") {
    AnnotationDao.query.list.transact(xa).unsafeRunSync.length should be >= 0
  }

  test("list annotations for project") {
    check {
      forAll {
        (
            user: User.Create,
            org: Organization.Create,
            platform: Platform,
            project: Project.Create,
            annotations: List[Annotation.Create]
        ) =>
          {
            val annotationsIO = for {
              (dbUser, _, _, dbProject) <- insertUserOrgPlatProject(
                user,
                org,
                platform,
                project
              )
              inserted <- AnnotationDao.insertAnnotations(
                annotations,
                dbProject.id,
                dbUser
              )
              forProject <- AnnotationDao.listAnnotationsForProject(
                dbProject.id
              )
            } yield { (inserted, forProject) }
            val (insertedAnnotations, annotationsForProject) =
              annotationsIO.transact(xa).unsafeRunSync

            insertedAnnotations.toSet == annotationsForProject.toSet
          }
      }
    }
  }

  test("update an annotation verified by a verifier") {
    check {
      forAll {
        (
            user: User.Create,
            org: Organization.Create,
            platform: Platform,
            project: Project.Create,
            annotationInsert: Annotation.Create,
            annotationUpdate: Annotation.Create,
            verifierCreate: User.Create
        ) =>
          {
            val annotationInsertWithUserAndProjectIO = for {
              (dbUser, _, _, dbProject) <- insertUserOrgPlatProject(
                user,
                org,
                platform,
                project
              )
              annotations <- AnnotationDao.insertAnnotations(
                List(annotationInsert),
                dbProject.id,
                dbUser
              )
              verifier <- UserDao.create(verifierCreate)
            } yield (annotations, dbUser, dbProject, verifier)

            val annotationsUpdateWithAnnotationIO = annotationInsertWithUserAndProjectIO flatMap {
              case (
                  annotations: List[Annotation],
                  dbUser: User,
                  dbProject: Project,
                  verifier: User
                  ) => {
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
                AnnotationDao.updateAnnotation(
                  dbProject.id,
                  newAnnotation
                ) flatMap { (affectedRows: Int) =>
                  {
                    AnnotationDao.getAnnotationById(dbProject.id, annotationId) map {
                      (affectedRows, _, verifier, dbUser)
                    }
                  }
                }
              }
            }

            val (affectedRows, updatedAnnotationO, verifier, dbUser) =
              annotationsUpdateWithAnnotationIO.transact(xa).unsafeRunSync

            affectedRows == 1 &&
            (
              updatedAnnotationO match {
                case Some(updatedAnnotation) =>
                  updatedAnnotation.label == annotationUpdate.label &&
                    updatedAnnotation.description == annotationUpdate.description &&
                    updatedAnnotation.machineGenerated == annotationUpdate.machineGenerated &&
                    updatedAnnotation.confidence == annotationUpdate.confidence &&
                    updatedAnnotation.quality == annotationUpdate.quality &&
                    updatedAnnotation.geometry == annotationUpdate.geometry &&
                    updatedAnnotation.verifiedBy == Some(verifier.id) &&
                    updatedAnnotation.ownerName == dbUser.name &&
                    updatedAnnotation.ownerProfileImageUri == dbUser.profileImageUri
                case _ => false
              }
            )
          }
      }
    }
  }

  test("list project labels") {
    check {
      forAll {

        (
            user: User.Create,
            org: Organization.Create,
            platform: Platform,
            project: Project.Create,
            annotations: List[Annotation.Create]
        ) =>
          {
            val annotationsLabelsIO = for {
              (dbUser, _, _, dbProject) <- insertUserOrgPlatProject(
                user,
                org,
                platform,
                project
              )
              _ <- AnnotationDao.insertAnnotations(
                annotations,
                dbProject.id,
                dbUser
              )
              labels <- AnnotationDao.listProjectLabels(dbProject.id)
            } yield labels

            annotationsLabelsIO.transact(xa).unsafeRunSync.toSet ==
              (annotations.toSet map { (annotation: Annotation.Create) =>
                annotation.label
              })
          }
      }
    }
  }

  test(
    "list annotations with owner info for project when withOwnerInfo QP is true"
  ) {
    check {
      forAll {
        (
            user: User.Create,
            org: Organization.Create,
            platform: Platform,
            project: Project.Create,
            annotations: List[Annotation.Create],
            queryParams: AnnotationQueryParameters,
            page: PageRequest
        ) =>
          {
            val annotationsIO = for {
              (dbUser, _, _, dbProject) <- insertUserOrgPlatProject(
                user,
                org,
                platform,
                project
              )
              inserted <- AnnotationDao.insertAnnotations(
                annotations,
                dbProject.id,
                dbUser
              )
              forProject <- AnnotationDao.listByLayerWithOwnerInfo(
                dbProject.id,
                page,
                queryParams.copy(withOwnerInfo = Some(true))
              )
            } yield { (inserted, forProject, dbUser) }

            val (insertedAnnotations, annotationsForProject, dbUser) =
              annotationsIO.transact(xa).unsafeRunSync

            insertedAnnotations
              .map(annotation => {
                AnnotationWithOwnerInfo(
                  annotation.id,
                  annotation.projectId,
                  annotation.createdAt,
                  annotation.createdBy,
                  annotation.modifiedAt,
                  annotation.owner,
                  annotation.label,
                  annotation.description,
                  annotation.machineGenerated,
                  annotation.confidence,
                  annotation.quality,
                  annotation.geometry,
                  annotation.annotationGroup,
                  annotation.labeledBy,
                  annotation.verifiedBy,
                  annotation.projectLayerId,
                  annotation.taskId,
                  dbUser.name,
                  dbUser.profileImageUri
                )
              })
              .toSet == annotationsForProject.results.toSet
          }
      }
    }
  }

  test("list detection annotations from a layer by task status") {
    check {
      forAll {
        (
            userCreate: User.Create,
            orgCreate: Organization.Create,
            platform: Platform,
            (projectAgGroupCreate): (Project.Create, AnnotationGroup.Create),
            annoAndTaskFeatureCreate: (Task.TaskFeatureCreate,
                                       List[Annotation.Create]),
            labelValidateTeamCreate: (Team.Create, Team.Create),
            labelValidateTeamUgrCreate: (UserGroupRole.Create,
                                         UserGroupRole.Create)
        ) =>
          {
            val (taskFeatureCreate, annotationsCreate) =
              annoAndTaskFeatureCreate
            val (projectCreate, annotationGroupCreate) = projectAgGroupCreate
            val labelName = "Car"
            val labelId = UUID.randomUUID()
            val labelGroupId = UUID.randomUUID()
            val connIO = for {
              (dbUser, dbOrg, dbPlatform, dbProject) <- insertUserOrgPlatProject(
                userCreate,
                orgCreate,
                platform,
                projectCreate
              )
              updatedDbProject <- fixupProjectExtrasUpdate(
                labelValidateTeamCreate,
                labelValidateTeamUgrCreate,
                dbOrg,
                dbUser,
                dbPlatform,
                dbProject,
                Some(List((labelId, labelName, labelGroupId))),
                Some(Map(labelGroupId -> "Car Group"))
              )
              collection <- TaskDao.insertTasks(
                Task.TaskFeatureCollectionCreate(
                  features = List(
                    fixupTaskFeatureCreate(taskFeatureCreate, updatedDbProject)
                      .withStatus(TaskStatus.Labeled)
                  )
                ),
                dbUser
              )
              feature = collection.features.head
              annotationGroup <- AnnotationGroupDao.createAnnotationGroup(
                dbProject.id,
                annotationGroupCreate.copy(name = "label"),
                dbUser)
              updatedAnnotationsCreate = annotationsCreate.map(annoCreate => {
                annoCreate.copy(
                  label = labelId.toString(),
                  geometry = Some(feature.geometry),
                  taskId = Some(feature.id),
                  annotationGroup = Some(annotationGroup.id)
                )
              })
              insertedAnnotations <- AnnotationDao.insertAnnotations(
                updatedAnnotationsCreate,
                dbProject.id,
                dbUser
              )
              listedAnnotations <- AnnotationDao
                .listDetectionLayerAnnotationsByTaskStatus(
                  dbProject.id,
                  dbProject.defaultLayerId,
                  List("LABELED")
                )
            } yield { (insertedAnnotations, listedAnnotations) }

            val (dbAnnotations, listed) = connIO.transact(xa).unsafeRunSync
            dbAnnotations.length == listed.length &&
            listed.map(_.label).toSet == Set(labelName) &&
            true
          }
      }
    }
  }

  test("list classification annotations from a layer by task status") {
    check {
      forAll {
        (
            userOrgPlat: (User.Create, Organization.Create, Platform),
            (projectAgGroupCreate): (Project.Create, AnnotationGroup.Create),
            annotationCreates: (Annotation.Create, Annotation.Create),
            taskFeatureCreates: (Task.TaskFeatureCreate, Task.TaskFeatureCreate),
            labelValidateTeamCreate: (Team.Create, Team.Create),
            labelValidateTeamUgrCreate: (UserGroupRole.Create,
                                         UserGroupRole.Create)
        ) =>
          {
            val (userCreate, orgCreate, platform) = userOrgPlat
            val (taskFeatureCreateOne, taskFeatureCreateTwo) =
              taskFeatureCreates
            val (annotationCreateOne, annotationCreateTwo) = annotationCreates
            val (projectCreate, annotationGroupCreate) = projectAgGroupCreate
            val labelNameOne = "Finished"
            val labelIdOne = UUID.randomUUID()
            val labelNameTwo = "Partial"
            val labelIdTwo = UUID.randomUUID()
            val labelGroupIdOne = UUID.randomUUID()
            val labelGroupNameOne = "Building"
            val labelGroupIdTwo = UUID.randomUUID()
            val labelGroupNameTwo = "Road"

            val connIO = for {
              (dbUser, dbOrg, dbPlatform, dbProject) <- insertUserOrgPlatProject(
                userCreate,
                orgCreate,
                platform,
                projectCreate
              )
              updatedDbProject <- fixupProjectExtrasUpdate(
                labelValidateTeamCreate,
                labelValidateTeamUgrCreate,
                dbOrg,
                dbUser,
                dbPlatform,
                dbProject,
                Some(
                  List(
                    (labelIdOne, labelNameOne, labelGroupIdOne),
                    (labelIdTwo, labelNameTwo, labelGroupIdTwo),
                  )),
                Some(
                  Map(
                    labelGroupIdOne -> labelGroupNameOne,
                    labelGroupIdTwo -> labelGroupNameTwo
                  ))
              )
              taskCollectionOne <- TaskDao.insertTasks(
                Task.TaskFeatureCollectionCreate(
                  features = List(
                    fixupTaskFeatureCreate(taskFeatureCreateOne,
                                           updatedDbProject)
                      .withStatus(TaskStatus.Labeled)
                  )
                ),
                dbUser
              )
              taskCollectionTwo <- TaskDao.insertTasks(
                Task.TaskFeatureCollectionCreate(
                  features = List(
                    fixupTaskFeatureCreate(taskFeatureCreateTwo,
                                           updatedDbProject)
                      .withStatus(TaskStatus.Validated)
                  )
                ),
                dbUser
              )
              taskOne = taskCollectionOne.features.head
              taskTwo = taskCollectionTwo.features.head
              annotationGroup <- AnnotationGroupDao.createAnnotationGroup(
                dbProject.id,
                annotationGroupCreate.copy(name = "label"),
                dbUser)
              _ <- AnnotationDao.insertAnnotations(
                List(
                  annotationCreateOne.copy(
                    label = s"${labelIdOne} ${labelIdTwo}",
                    geometry = Some(taskOne.geometry),
                    taskId = Some(taskOne.id),
                    annotationGroup = Some(annotationGroup.id)
                  ),
                  annotationCreateTwo.copy(
                    label = s"${labelIdOne} ${labelIdTwo}",
                    geometry = Some(taskTwo.geometry),
                    taskId = Some(taskTwo.id),
                    annotationGroup = Some(annotationGroup.id)
                  )
                ),
                dbProject.id,
                dbUser
              )
              listedAnnotationsFC <- AnnotationDao
                .listClassificationLayerAnnotationsByTaskStatus(
                  dbProject.id,
                  dbProject.defaultLayerId,
                  List("LABELED", "VALIDATED")
                )
            } yield { listedAnnotationsFC }

            val listed = connIO.transact(xa).unsafeRunSync

            listed.features.length == 2 &&
            listed.`type` == "FeatureCollection" &&
            listed.features.foldLeft(true)((acc, feature) => {
              acc &&
              feature.properties.asJson.hcursor
                .get[String](labelGroupNameOne)
                .toOption == Some(labelNameOne) &&
              feature.properties.asJson.hcursor
                .get[String](labelGroupNameTwo)
                .toOption == Some(labelNameTwo)
            }) &&
            true
          }
      }
    }
  }
}
