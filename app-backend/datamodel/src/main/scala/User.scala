package com.rasterfoundry.datamodel

import cats.syntax.either._
import eu.timepit.refined._
import eu.timepit.refined.api._
import eu.timepit.refined.numeric._
import io.circe._
import io.circe.generic.JsonCodec
import io.circe.generic.semiauto._
import io.circe.refined._

import java.net.{URI, URLEncoder}
import java.security.InvalidParameterException
import java.sql.Timestamp
import java.util.UUID

sealed abstract class UserRole(val repr: String)
    extends Product
    with Serializable
case object UserRoleRole extends UserRole("USER")
case object Viewer extends UserRole("VIEWER")
case object Admin extends UserRole("ADMIN")

object UserRole {
  implicit val userRoleEncoder: Encoder[UserRole] =
    Encoder.encodeString.contramap[UserRole](_.toString)
  implicit val userRoleDecoder: Decoder[UserRole] =
    Decoder.decodeString.emap { str =>
      Either.catchNonFatal(UserRole.fromString(str)).leftMap(_ => "UserRole")
    }

  def fromString(s: String): UserRole =
    s.toUpperCase match {
      case "USER" =>
        UserRoleRole // TODO Think of a better name than UserRoleRole
      case "VIEWER" => Viewer
      case "ADMIN"  => Admin
      case _        => throw new Exception(s"Unsupported user role string: $s")
    }

  def toString(ur: UserRole): String = {
    ur match {
      case UserRoleRole => "USER"
      case Viewer       => "VIEWER"
      case Admin        => "ADMIN"
    }
  }
}

final case class UserOptionAndRoles(
    user: Option[User],
    roles: List[UserGroupRole]
)

final case class Credential(token: Option[String])

object Credential {
  implicit val credentialEncoder: Encoder[Credential] =
    Encoder.encodeString.contramap[Credential] { _.token.getOrElse("") }
  implicit val credentialDecoder: Decoder[Credential] =
    Decoder.decodeString.emap[Credential] { str =>
      Either
        .catchNonFatal(Credential.fromString(str))
        .leftMap(_ => "Credential")
    }

  def fromString(s: String): Credential = {
    Credential.apply(Some(s))
  }

  def fromStringO(s: Option[String]): Credential = {
    Credential.apply(s)
  }
}

sealed abstract class OrganizationType(val repr: String) {
  override def toString: String = repr
}

object OrganizationType {
  case object Commercial extends OrganizationType("COMMERCIAL")
  case object Government extends OrganizationType("GOVERNMENT")
  case object NonProfit extends OrganizationType("NON-PROFIT")
  case object Academic extends OrganizationType("ACADEMIC")
  case object Military extends OrganizationType("MILITARY")
  case object Other extends OrganizationType("OTHER")

  def fromString(s: String): OrganizationType =
    s.toUpperCase match {
      case "COMMERCIAL" => Commercial
      case "GOVERNMENT" => Government
      case "NON-PROFIT" => NonProfit
      case "ACADEMIC"   => Academic
      case "MILITARY"   => Military
      case "OTHER"      => Other
      case _ =>
        throw new InvalidParameterException(s"Invalid membership status: $s")
    }

  implicit val organizationTypeEncoder: Encoder[OrganizationType] =
    Encoder.encodeString.contramap[OrganizationType](_.toString)

  implicit val organizationTypeDecoder: Decoder[OrganizationType] =
    Decoder.decodeString.emap { str =>
      Either.catchNonFatal(fromString(str)).leftMap(_ => "OrganizationType")
    }
}

@JsonCodec
final case class User(
    id: String,
    role: UserRole,
    createdAt: Timestamp,
    modifiedAt: Timestamp,
    dropboxCredential: Credential,
    planetCredential: Credential,
    emailNotifications: Boolean,
    email: String,
    name: String,
    profileImageUri: String,
    isSuperuser: Boolean,
    isActive: Boolean,
    visibility: UserVisibility,
    personalInfo: User.PersonalInfo,
    scope: Scope
) {

  lazy val cacheKey: String = s"user:$id"

  def getDefaultExportSource(export: Export, dataBucket: String): URI =
    new URI(
      s"s3://$dataBucket/user-exports/${URLEncoder.encode(id, "UTF-8")}/${export.id}"
    )

  def getEmail: String =
    (emailNotifications, personalInfo.emailNotifications) match {
      case (true, true) | (false, true) => personalInfo.email
      case (true, false)                => email
      case (false, false)               => ""
    }

  def withScrubbedName: User = {
    val scrubbedName =
      s"${personalInfo.firstName} ${personalInfo.lastName}" match {
        case " "      => "Anonymous"
        case fullName => fullName
      }
    this.copy(name = scrubbedName)
  }

}

object User {

  def cacheKey(id: String): String = s"user:$id"

  def tupled = (User.apply _).tupled

  def create = Create.apply _

  @JsonCodec
  final case class PersonalInfo(
      firstName: String = "",
      lastName: String = "",
      email: String = "",
      emailNotifications: Boolean = false,
      phoneNumber: String = "",
      organizationName: String = "",
      organizationType: OrganizationType = OrganizationType.Other,
      organizationWebsite: String = "",
      profileWebsite: String = "",
      profileBio: String = "",
      profileUrl: String = ""
  )

  @JsonCodec
  final case class WithGroupRole(
      id: String,
      role: UserRole,
      createdAt: Timestamp,
      modifiedAt: Timestamp,
      dropboxCredential: Credential,
      planetCredential: Credential,
      emailNotifications: Boolean,
      email: String,
      name: String,
      profileImageUri: String,
      isSuperuser: Boolean,
      isActive: Boolean,
      visibility: UserVisibility,
      groupRole: GroupRole,
      membershipStatus: MembershipStatus
  )

  @JsonCodec
  final case class Create(
      id: String,
      role: UserRole = Viewer,
      email: String = "",
      name: String = "",
      profileImageUri: String = "",
      scope: Scope = Scopes.RasterFoundryUser
  ) {
    def toUser: User = {
      val now = new Timestamp(new java.util.Date().getTime)
      User(
        id,
        role,
        now,
        now,
        Credential(None),
        Credential(None),
        emailNotifications = false,
        email,
        name,
        profileImageUri,
        isSuperuser = false,
        isActive = true,
        UserVisibility.Private,
        User.PersonalInfo(),
        scope
      )
    }
  }

  final case class JwtFields(
      id: String,
      email: String,
      name: String,
      picture: String,
      platformId: UUID,
      organizationId: UUID,
      scope: Scope = Scopes.RasterFoundryUser
  ) {
    def toUser: User = {
      val now = new Timestamp(new java.util.Date().getTime)
      User(
        id,
        Viewer,
        now,
        now,
        Credential(None),
        Credential(None),
        emailNotifications = false,
        email,
        name,
        picture,
        isSuperuser = false,
        isActive = true,
        UserVisibility.Private,
        User.PersonalInfo(),
        scope
      )
    }
  }
}

@JsonCodec final case class UserShareInfo(
    email: String,
    actionType: Option[ActionType] = None,
    silent: Option[Boolean] = None
)

@JsonCodec final case class UserThin(
    id: String,
    email: String,
    profileImageUri: String
)

object UserThin {
  def fromUser(user: User) = UserThin(user.id, user.email, user.profileImageUri)
}

@JsonCodec final case class UserThinWithActionType(
    id: String,
    email: String,
    profileImageUri: String,
    actionType: ActionType
)

object UserThinWithActionType {
  def fromUser(user: User, actionType: ActionType) =
    UserThinWithActionType(
      user.id,
      user.email,
      user.profileImageUri,
      actionType
    )
}

case class UserBulkCreate(
    count: Int Refined Interval.Closed[W.`1`.T, W.`40`.T],
    peudoUserNameType: PseudoUsernameType,
    organizationId: UUID,
    platformId: UUID,
    campaignId: Option[UUID] = None,
    grantAccessToParentCampaignOwner: Boolean = false,
    grantAccessToChildrenCampaignOwner: Boolean = false,
    copyResourceLink: Boolean = false,
    isAltConnection: Option[Boolean] = None
)

object UserBulkCreate {
  implicit val decUserBulkCreate: Decoder[UserBulkCreate] = deriveDecoder
}

case class UserInfo(
    id: String,
    email: String,
    name: String
)

case class UserWithCampaign(user: User, campaignO: Option[Campaign])

@JsonCodec
final case class UserWithPlatform(
    id: String,
    role: UserRole,
    createdAt: Timestamp,
    modifiedAt: Timestamp,
    dropboxCredential: Credential,
    planetCredential: Credential,
    emailNotifications: Boolean,
    email: String,
    name: String,
    profileImageUri: String,
    isSuperuser: Boolean,
    isActive: Boolean,
    visibility: UserVisibility,
    personalInfo: User.PersonalInfo,
    scope: Scope,
    platformNameOpt: Option[String],
    platformIdOpt: Option[UUID]
) {
  lazy val cacheKey: String = s"user:platform:$id"

  def toUser =
    User(
      id,
      role,
      createdAt,
      modifiedAt,
      dropboxCredential,
      planetCredential,
      emailNotifications,
      email,
      name,
      profileImageUri,
      isSuperuser,
      isActive,
      visibility,
      personalInfo,
      scope
    )
}

object UserWithPlatform {
  def cacheKey(id: String) = s"user:platform:$id"
}
