package com.rasterfoundry.database

import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.database.types._
import com.rasterfoundry.datamodel._

import cats.data.OptionT
import cats.implicits._
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import doobie.postgres.implicits._

import java.sql.Timestamp
import java.util.UUID

object CampaignDao extends Dao[Campaign] with ObjectPermissions[Campaign] {

  val tableName = "campaigns"

  override val fieldNames = List(
    "id",
    "created_at",
    "owner",
    "name",
    "campaign_type",
    "description",
    "video_link",
    "partner_name",
    "partner_logo",
    "parent_campaign_id",
    "continent",
    "tags",
    "children_count",
    "project_statuses",
    "is_active",
    "resource_link"
  )

  def selectF: Fragment = fr"SELECT " ++ selectFieldsF ++ fr" FROM " ++ tableF

  def authQuery(
      user: User,
      objectType: ObjectType,
      ownershipTypeO: Option[String],
      groupTypeO: Option[GroupType],
      groupIdO: Option[UUID]
  ): Dao.QueryBuilder[Campaign] =
    user.isSuperuser match {
      case true =>
        Dao.QueryBuilder[Campaign](selectF, tableF, List.empty)
      case false =>
        Dao.QueryBuilder[Campaign](
          selectF,
          tableF,
          List(
            queryObjectsF(
              user,
              objectType,
              ActionType.View,
              ownershipTypeO,
              groupTypeO,
              groupIdO
            )
          )
        )
    }

  def authorized(
      user: User,
      objectType: ObjectType,
      objectId: UUID,
      actionType: ActionType
  ): ConnectionIO[AuthResult[Campaign]] =
    this.query
      .filter(authorizedF(user, objectType, actionType))
      .filter(objectId)
      .selectOption
      .map(AuthResult.fromOption _)

  def listCampaigns(
      page: PageRequest,
      params: CampaignQueryParameters,
      user: User
  ): ConnectionIO[PaginatedResponse[Campaign]] =
    authQuery(
      user,
      ObjectType.Campaign,
      params.ownershipTypeParams.ownershipType,
      params.groupQueryParameters.groupType,
      params.groupQueryParameters.groupId
    ).filter(params)
      .page(page)

  def insertCampaign(
      campaignCreate: Campaign.Create,
      user: User
  ): ConnectionIO[Campaign] =
    (fr"INSERT INTO" ++ tableF ++ fr"""(
      id, created_at, owner, name, campaign_type, description,
      video_link, partner_name, partner_logo, parent_campaign_id,
      continent, tags, resource_link
    )""" ++
      fr"""VALUES
      (uuid_generate_v4(), now(), ${user.id}, ${campaignCreate.name},
       ${campaignCreate.campaignType}, ${campaignCreate.description},
       ${campaignCreate.videoLink}, ${campaignCreate.partnerName},
       ${campaignCreate.partnerLogo}, ${campaignCreate.parentCampaignId},
       ${campaignCreate.continent}, ${campaignCreate.tags}, ${campaignCreate.resourceLink}
       )
    """).update.withUniqueGeneratedKeys[Campaign](
      fieldNames: _*
    )

  def getCampaignById(id: UUID): ConnectionIO[Option[Campaign]] =
    query.filter(id).selectOption

  def unsafeGetCampaignById(id: UUID): ConnectionIO[Campaign] =
    query.filter(id).select

  def updateCampaign(campaign: Campaign, id: UUID): ConnectionIO[Int] =
    (fr"UPDATE " ++ tableF ++ fr"""SET
      name = ${campaign.name},
      description = ${campaign.description},
      video_link = ${campaign.videoLink},
      partner_name = ${campaign.partnerName},
      partner_logo = ${campaign.partnerLogo},
      continent = ${campaign.continent},
      tags = ${campaign.tags},
      is_active = ${campaign.isActive},
      resource_link = ${campaign.resourceLink}
    WHERE
      id = $id
    """).update.run;

  def deleteCampaign(id: UUID, user: User): ConnectionIO[Int] =
    for {
      projects <- AnnotationProjectDao.listByCampaign(id)
      _ <- projects traverse { p =>
        AnnotationProjectDao.deleteById(p.id, user)
      }
      n <- query.filter(fr"id = ${id}").delete
    } yield n

  def countUserCampaigns(user: User): ConnectionIO[Long] =
    query.filter(user).count

  def copyCampaign(
      id: UUID,
      user: User,
      tagsO: Option[List[String]] = None,
      copyResourceLink: Boolean = false
  ): ConnectionIO[Campaign] = {
    val tagCol = tagsO
      .map(
        tags =>
          Fragment
            .const(s"ARRAY[${tags.map(t => s"'${t}'").mkString(",")}]::text[]")
      )
      .getOrElse(Fragment.const("tags"))
    val resourceLinkF = if (copyResourceLink) fr"resource_link" else fr"null"
    val insertQuery = (fr"""
           INSERT INTO""" ++ tableF ++ fr"(" ++ insertFieldsF ++ fr")" ++
      fr"""SELECT
             uuid_generate_v4(), now(), ${user.id}, name, campaign_type, description, video_link,
             partner_name, partner_logo, ${id}, continent, ${tagCol}, ${0}, project_statuses, true, ${resourceLinkF}""" ++
      fr"""FROM """ ++ tableF ++ fr"""
           WHERE id = ${id}
        """)
    for {
      campaignCopy <- insertQuery.update
        .withUniqueGeneratedKeys[Campaign](
          fieldNames: _*
        )
      annotationProjects <- AnnotationProjectDao.listByCampaign(id)
      _ <- annotationProjects traverse { project =>
        AnnotationProjectDao.copyProject(
          project.id,
          user,
          Some(campaignCopy.id)
        )
      }
    } yield campaignCopy
  }

  def isActiveCampaign(id: UUID): ConnectionIO[Boolean] =
    query.filter(id).filter(fr"is_active = ${true}").exists

  def getChildren(campaignId: UUID): ConnectionIO[List[Campaign]] =
    query.filter(fr"parent_campaign_id = $campaignId").list

  def getCloneOwners(id: UUID): ConnectionIO[List[UserThin]] =
    for {
      campaigns <- getChildren(id)
      userIds = campaigns.map(_.owner)
      users <- UserDao.getUsersByIds(userIds)
      userThins = users.map(u => UserThin.fromUser(u))
    } yield userThins

  def randomReviewTask(
      campaignId: UUID,
      user: User
  ): ConnectionIO[Option[Task.TaskFeatureWithCampaign]] = {
    fr"""
      WITH parent_tasks (id) AS (
        SELECT t1.id
        FROM tasks t1
        WHERE t1.annotation_project_id IN (
          SELECT id
          FROM annotation_projects
          WHERE campaign_id IN (
            SELECT id
            FROM campaigns c
            WHERE c.parent_campaign_id = ${campaignId}
          )
        )
        AND t1.task_type = ${TaskType.Label.toString}::task_type
        AND t1.parent_task_id IS NULL
      ),
      -- ids of tasks that have at least 3 validated sub-tasks
      validated_ids (id) AS (
        SELECT t1.id
        FROM parent_tasks t1
        JOIN tasks t2
          ON t2.parent_task_id = t1.id
        WHERE t2.task_type = ${TaskType.Review.toString}::task_type
          AND t2.status <> ${TaskStatus.Labeled.toString}::task_status
        GROUP BY t1.id
        HAVING COUNT(t2.id) >= 3
      ),
      reviewed_by_user_counts (id, id2, reviewed) AS (
        SELECT t1.id, t2.id, COUNT(ta.*)
        FROM parent_tasks t1
        JOIN tasks t2
          ON t2.parent_task_id = t1.id
        JOIN task_actions ta
          ON ta.task_id = t2.id
          AND ta.user_id = ${user.id}
          AND ta.to_status = ${TaskStatus.Validated.toString}::task_status
        WHERE t2.task_type = ${TaskType.Review.toString}::task_type
        GROUP BY t1.id, t2.id
      ),
      reviewed_ids (id) AS (
        SELECT parent_tasks.id
        FROM parent_tasks
        JOIN reviewed_by_user_counts r
          ON r.id = parent_tasks.id
        GROUP BY parent_tasks.id
        HAVING SUM(r.reviewed) >= 1
      ),
      candidate_campaigns (id) AS (
        SELECT id from campaigns
        WHERE
          parent_campaign_id = ${campaignId}
        AND
          owner <> ${user.id}
      )
      SELECT tasks.*, annotation_projects.campaign_id
      FROM tasks
      JOIN annotation_projects
        ON tasks.annotation_project_id = annotation_projects.id
      WHERE
        parent_task_id NOT IN (SELECT id FROM validated_ids)
        AND parent_task_id NOT IN (SELECT id FROM reviewed_ids)
        AND parent_task_id IS NOT NULL
        AND task_type = ${TaskType.Review.toString}::task_type
        AND annotation_projects.campaign_id IN (select id from candidate_campaigns)
        AND (locked_by = ${user.id} OR locked_by IS NULL)
      ORDER BY RANDOM() LIMIT 1;
    """
      .query[Task.TaskWithCampaign]
      .to[List] flatMap { tasks =>
      tasks.toNel traverse { tasks =>
        (for {
          task <- OptionT(TaskDao.getTaskById(tasks.head.id))
          actions <- OptionT.liftF(TaskDao.getTaskActions(task.id))
        } yield {
          tasks.head.toGeoJSONFeature(actions)
        }).value
      }
    } map { _.flatten }
  }

  def getProjectMapping(
      childCampaignId: UUID,
      parentDateMap: Map[Timestamp, UUID]
  ): ConnectionIO[List[(ChildAnnotationProjectId, ParentAnnotationProjectId)]] =
    AnnotationProjectDao.listByCampaign(childCampaignId) map { childProjects =>
      childProjects flatMap { project =>
        project.capturedAt flatMap { parentDateMap.get(_) } map { parentId =>
          (
            ChildAnnotationProjectId(project.id),
            ParentAnnotationProjectId(parentId)
          )
        }
      }
    }

  def retrieveChildCampaignAnnotations(
      campaignId: UUID
  ): ConnectionIO[Unit] =
    for {
      childCampaigns <- getChildren(campaignId)
      parentProjects <- AnnotationProjectDao.listByCampaign(campaignId)
      // create a map from capture dates to parent projects. we're assuming for
      // now that capture date is unique within a campaign
      captureDates = Map((parentProjects flatMap { project =>
        project.capturedAt map { dt =>
          (dt, project.id)
        }
      }): _*)
      // for each campaign, build a map of its annotation projects to the parent's
      // annotation projects
      childParentMappings <- childCampaigns traverse { campaign =>
        getProjectMapping(campaign.id, captureDates)
      }
      // mash all those maps together -- there should be no collisions, since annotation
      // projects can only be in a single campaign
      childParentMapping = childParentMappings.combineAll
      // now we have a map of child annotation projects to parent annotation projects,
      // so we no longer care about campaigns
      _ <- childParentMapping traverse {
        case (childId, parentId) =>
          AnnotationLabelDao.copyProjectAnnotations(childId, parentId)
      }
    } yield ()
}
