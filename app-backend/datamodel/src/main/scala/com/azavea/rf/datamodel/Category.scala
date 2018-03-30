package com.azavea.rf.datamodel

import java.sql.Timestamp

import io.circe.generic.JsonCodec

/** A user generate category to track analyses in the Raster Foundry lab
  *
  * @param id UUID Unique identifier for Category
  * @param createdAt Timestamp Creation time for category
  * @param modifiedAt Timestamp Modification time for category
  * @param createdBy String User ID that owns/created category
  * @param modifiedBy String User ID that last modified category
  * @param category String Category that is displayed to user
  */
@JsonCodec
case class Category(
    createdAt: Timestamp,
    modifiedAt: Timestamp,
    createdBy: String,
    modifiedBy: String,
    category: String,
    slugLabel: String
)

object Category {
  @JsonCodec
  case class Create(
      category: String
  ) {

    def toCategory(userId: String): Category = {
      def toSlugLabel(category: String): String = {
        def decompose(s: String): String = java.text.Normalizer.normalize(
          s, java.text.Normalizer.Form.NFD
        ).replaceAll("\\p{InCombiningDiacriticalMarks}+", "").trim.toLowerCase()
        val replaceWhitespace = (s: String) => "[\\s]+".r.replaceAllIn(s, "-")
        val removeUnallowed = (s: String) => "[^\\w-]+".r.replaceAllIn(s, "")
        val collapseDashes = (s: String) => "[-]+".r.replaceAllIn(s, "-")

        val slugified = collapseDashes(removeUnallowed(replaceWhitespace(decompose(category))))
        if (slugified.length() > 0) {
          slugified
        } else {
          throw new IllegalArgumentException(s"Invalid category: $category. Cannot slugify")
        }
      }

      val now = new Timestamp((new java.util.Date()).getTime())
      Category(
        now,
        now,
        userId,
        userId,
        category,
        toSlugLabel(category)
      )
    }
  }
}
