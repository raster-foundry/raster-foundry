package com.azavea.rf.database.util

import doobie._, doobie.implicits._
import doobie.postgres._, doobie.postgres.implicits._
import doobie.util.fragment.Fragment
import cats.Reducible
import cats.implicits._
import com.lonelyplanet.akka.http.extensions.{PageRequest, Order}


object Page {
  /** Turn a page request into the appropriate SQL fragment */
  def apply(pageRequest: PageRequest): Fragment = {
    val offset: Int = pageRequest.offset * pageRequest.limit
    val limit: Int = pageRequest.limit
    val orderBy =
      pageRequest.sort
        .toList
        .map({ case (sortExpr, ord) =>
          ord match {
            case Order.Asc => fr"$sortExpr ASC"
            case Order.Desc => fr"$sortExpr DESC"
          }
        }).toNel match {
          case Some(orderStrings) => fr"ORDER BY" ++ orderStrings.intercalate(fr",")
          case None => Fragment.empty
        }

    orderBy ++ fr"LIMIT $limit OFFSET $offset"
  }

  def apply(pageRequest: Option[PageRequest]): Fragment = pageRequest match {
    case Some(pr) => apply(pr)
    case None => fr""
  }
}

