package com.azavea.rf.datamodel.stac

import io.circe.generic.JsonCodec

@JsonCodec
final case class Link(`type`: String, // "self" or "thumbnail"
                      href: String)

object Link {
  def validate(link: Link): Either[String, Link] = {
    if (link.`type`.length < 1) {
      Left("Invalid link type: type must be a string with length > 0")
    } else if (link.href.length < 1) {
      Left("Invalid link type: href must be a string with length > 0")
    } else {
      Right(link)
    }
  }
}
