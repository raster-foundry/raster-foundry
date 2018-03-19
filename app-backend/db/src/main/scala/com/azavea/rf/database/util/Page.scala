package com.azavea.rf.database.util

import doobie._, doobie.implicits._
import doobie.postgres._, doobie.postgres.implicits._
import doobie.util.fragment.Fragment
import cats.Reducible
import cats.implicits._
import com.lonelyplanet.akka.http.extensions.{PageRequest, Order}
import doobie.Fragments


object Page {

  def sortExprConvertor(s: String): Option[String] = {
    // image
    s match {
      case "name" => Some("name")
      case "filename" => Some("filename")
      case "sourceuri" => Some("sourceuri")
      case "organization" => Some("organization")
      case "slugLabel" => Some("slug")
      case "datasource" => Some("datasource")
      // custom column for sorting by acquisition date for scenes
      case "acquisitionDatetime" => Some("acquisition_date_sort")
      case "sunAzimuth" => Some("sun_azimuth")
      case "sunElevation" => Some("sun_elevation")
      case "cloudCover" => Some("cloud_cover")
      case "createdAt" => Some("created_at")
      case "modifiedAt" => Some("modified_at")
      case "title" => Some("title")
      case "id" => Some("id")
      case "role" => Some("role")
      case "visibility" => Some("visibility")
      case _ => None
    }
  }

  /** Turn a page request into the appropriate SQL fragment */
  def apply(pageRequest: PageRequest, defaultOrderBy: Fragment = fr"id ASC"): Fragment = {
    val offset: Int = pageRequest.offset * pageRequest.limit
    val limit: Int = pageRequest.limit
    val orderBy =
      pageRequest.sort
        .toList
        .map({ case (sortExpr, ord) =>
          val parsedExpr = sortExprConvertor(sortExpr)
          (parsedExpr, ord) match {
            case (Some(expr), Order.Asc) => {
              println(s"ASC: ${expr}")
              Fragment.const(s"$expr ASC")
            }
            case (Some(expr), Order.Desc) => {
              println(s"DESC: ${expr}")
              Fragment.const(s"$expr DESC")
            }
            case (_, _) => Fragment.empty
          }
        }).toNel match {
          case Some(orderStrings) => fr"ORDER BY" ++ (orderStrings ++ List(defaultOrderBy)).intercalate(fr",")
          case None => Fragment.empty
        }

    orderBy ++ fr"LIMIT $limit OFFSET $offset"
  }

  def apply(pageRequest: Option[PageRequest]): Fragment = pageRequest match {
    case Some(pr) => apply(pr)
    case None => fr""
  }
}

