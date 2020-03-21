package com.rasterfoundry.database.util

import com.rasterfoundry.datamodel.{Order, PageRequest}

import cats.implicits._
import doobie._, doobie.implicits._
import doobie.util.fragment.Fragment

object Page {

  def sortExprConvertor(s: String): Option[String] = {
    // image
    s match {
      case "name"         => Some("name")
      case "filename"     => Some("filename")
      case "sourceuri"    => Some("sourceuri")
      case "organization" => Some("organization")
      case "slugLabel"    => Some("slug")
      case "datasource"   => Some("datasource")
      // custom column for sorting by acquisition date for scenes --
      // the COALESCE of these two columns is indexed already
      case "acquisitionDatetime" =>
        Some("COALESCE(acquisition_date, created_at)")
      case "sunAzimuth"   => Some("sun_azimuth")
      case "sunElevation" => Some("sun_elevation")
      case "cloudCover"   => Some("cloud_cover")
      case "createdAt"    => Some("created_at")
      case "modifiedAt"   => Some("modified_at")
      case "title"        => Some("title")
      case "id"           => Some("id")
      case "role"         => Some("role")
      case "visibility"   => Some("visibility")
      case _              => None
    }
  }

  /** Turn a page request into the appropriate SQL fragment */
  @SuppressWarnings((Array("TraversableHead")))
  def apply(
      pageRequest: PageRequest,
      defaultOrderBy: Fragment = fr"id ASC"
  ): Fragment = {
    val offset: Int = pageRequest.offset * pageRequest.limit
    val limit: Int = pageRequest.limit

    val orderBy: Fragment = createOrderClause(pageRequest, defaultOrderBy)
    orderBy ++ fr"LIMIT $limit OFFSET $offset"
  }

  def apply(pageRequest: Option[PageRequest]): Fragment = pageRequest match {
    case Some(pr) => apply(pr)
    case None     => fr""
  }

  def createOrderClause(
      pageRequest: PageRequest,
      defaultOrderBy: Fragment = fr"id ASC"
  ): Fragment = {
    // Choose a default sort corresponding to the sort order in the first value in the
    // pageRequest -- if there's a combined id + that column index, they need to be sorted
    // the same for postgres to choose to use the index
    val defaultSort: Fragment = pageRequest.sort.isEmpty match {
      case true => defaultOrderBy
      case false => {
        pageRequest.sort.values.head match {
          case Order.Asc  => fr"id ASC"
          case Order.Desc => fr"id DESC"
        }
      }
    }
    pageRequest.sort.toList
      .map({
        case (sortExpr, ord) =>
          val parsedExpr = sortExprConvertor(sortExpr)
          (parsedExpr, ord) match {
            case (Some(expr), Order.Asc) => {
              Fragment.const(s"$expr ASC")
            }
            case (Some(expr), Order.Desc) => {
              Fragment.const(s"$expr DESC")
            }
            case (_, _) => Fragment.empty
          }
      })
      // If we don't filter out empty fragments, we'll intercalate commas with spaces
      .filter(_ != Fragment.empty)
      .toNel match {
      case Some(orderStrings) =>
        fr"ORDER BY" ++ (orderStrings ++ List(defaultSort)).intercalate(fr",")
      case None => Fragment.empty
    }
  }
}
