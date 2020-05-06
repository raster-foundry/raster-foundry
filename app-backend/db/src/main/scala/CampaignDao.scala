package com.rasterfoundry.database

import com.rasterfoundry.database.Implicits._
import com.rasterfoundry.datamodel._

import cats.implicits._
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import doobie.postgres.implicits._

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
    "partner_logo"
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
    (fr"INSERT INTO" ++ tableF ++ fr"(" ++ insertFieldsF ++ fr")" ++
      fr"""VALUES
      (uuid_generate_v4(), now(), ${user.id}, ${campaignCreate.name},
       ${campaignCreate.campaignType}, ${campaignCreate.description},
       ${campaignCreate.videoLink}, ${campaignCreate.partnerName},
       ${campaignCreate.partnerLogo}
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
      partner_logo = ${campaign.partnerLogo}
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

}
