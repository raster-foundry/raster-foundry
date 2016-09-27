package com.azavea.rf.migration.manager

import slick.model.Model
import com.liyaos.forklift.slick.SlickCodegen


trait RFCodegen extends SlickCodegen {
  // Override the package name
  override def pkgName(version: String) = "com.azavea.rf.datamodel." + version + ".schema"

  override val generatedDir =
    System.getProperty("user.dir") + "/datamodel/src/main/scala"

  // Set the models requiring code generation here
  override def tableNames = List(
    "organizations",
    "users",
    "users_to_organizations",
    "scenes",
    "buckets",
    "scenes_to_buckets",
    "thumbnails",
    "footprints",
    "images"
  )

  class RFSlickSourceCodeGenerator(m: Model, version: Int)
      extends SlickSourceCodeGenerator(m, version) {

    override def Table = new Table(_) { table =>
      override def Column = new Column(_) { column =>

        /** Override column types for custom columns
          *
          * This is a little tricky, but this uses a column's name to
          * set the column type correctly. Care will need to be taken
          * in order to avoid collisions.
          */
        override def rawType = {
          model.name match {
            case "tags" => "List[String]"
            case "bands" => "List[String]"
            case "scene_metadata" => "Map[String, Any]"
            case "image_metadata" => "Map[String, Any]"
            case "thumbnail_status" => "JobStatus"
            case "boundary_status" => "JobStatus"
            case "status" => "JobStatus"
            case "visibility" => "Visibility"
            case "multipolygon" => "Projected[Geometry]"
            case _ => super.rawType
          }
        }
      }
    }

    /** Override of packageCode to use custom driver for Raster Foundry
      *
      * The code generation template needs to use a custom driver to handle the custom types
      * for Postgres columns -- this diminishes flexibility, but in our case doesn't matter as
      * much
      */
    override def packageCode(profile: String, pkg: String, container: String,
      parentType: Option[String]): String = {
      s"""
package ${pkg}
// AUTO-GENERATED Slick data model
/** Stand-alone Slick data model for immediate use */
object ${container} extends {
  val profile = com.azavea.rf.datamodel.driver.ExtendedPostgresDriver
} with ${container}

import com.azavea.rf.datamodel.enums._

/** Slick data model trait for extension, choice of backend or usage in the cake pattern. (Make sure to initialize this late.) */
trait ${container}${parentType.map(t => s" extends $t").getOrElse("")} {
  val profile: com.azavea.rf.datamodel.driver.ExtendedPostgresDriver
  import geotrellis.vector.Geometry
  import geotrellis.slick.Projected
  import profile.api._
  ${indent(code)}
}

object Version{
  def version = $version
}
""".trim()
    }
  }

  override def getGenerator(m: Model, version: Int):RFSlickSourceCodeGenerator =
    new RFSlickSourceCodeGenerator(m, version)
}
