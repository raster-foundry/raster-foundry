package com.rasterfoundry.api

import akka.http.scaladsl.model.HttpMethods._
import com.rasterfoundry.api.aoi.AoiRoutes
import com.rasterfoundry.api.config.ConfigRoutes
import com.rasterfoundry.api.datasource.DatasourceRoutes
import com.rasterfoundry.api.exports.ExportRoutes
import com.rasterfoundry.api.featureflags.FeatureFlagRoutes
import com.rasterfoundry.api.feed.FeedRoutes
import com.rasterfoundry.api.healthcheck._
import com.rasterfoundry.api.maptoken.MapTokenRoutes
import com.rasterfoundry.api.organization.OrganizationRoutes
import com.rasterfoundry.api.platform.PlatformRoutes
import com.rasterfoundry.api.project.ProjectRoutes
import com.rasterfoundry.api.scene.SceneRoutes
import com.rasterfoundry.api.shape.ShapeRoutes
import com.rasterfoundry.api.thumbnail.ThumbnailRoutes
import com.rasterfoundry.api.token.TokenRoutes
import com.rasterfoundry.api.tool.ToolRoutes
import com.rasterfoundry.api.toolcategory.ToolCategoryRoutes
import com.rasterfoundry.api.toolrun.ToolRunRoutes
import com.rasterfoundry.api.tooltag.ToolTagRoutes
import com.rasterfoundry.api.uploads.UploadRoutes
import com.rasterfoundry.api.user.UserRoutes
import com.rasterfoundry.api.utils.Config
import com.rasterfoundry.api.license.LicenseRoutes
import com.rasterfoundry.api.team.TeamRoutes
import ch.megard.akka.http.cors.scaladsl.CorsDirectives._
import ch.megard.akka.http.cors.scaladsl.settings._

import scala.collection.immutable.Seq

/**
  * Contains all routes for Raster Foundry API/Healthcheck endpoints.
  *
  * Actual routes should be written in the relevant feature as much as is feasible
  *
  */
trait Router
    extends HealthCheckRoutes
    with UserRoutes
    with OrganizationRoutes
    with SceneRoutes
    with ProjectRoutes
    with AoiRoutes
    with TokenRoutes
    with ThumbnailRoutes
    with ToolRoutes
    with ToolTagRoutes
    with ConfigRoutes
    with ToolCategoryRoutes
    with ToolRunRoutes
    with DatasourceRoutes
    with MapTokenRoutes
    with FeedRoutes
    with UploadRoutes
    with ExportRoutes
    with Config
    with FeatureFlagRoutes
    with ShapeRoutes
    with LicenseRoutes
    with TeamRoutes
    with PlatformRoutes {

  val settings = CorsSettings.defaultSettings.copy(
    allowedMethods = Seq(GET, POST, PUT, HEAD, OPTIONS, DELETE))

  val routes = cors(settings) {
    pathPrefix("healthcheck") {
      healthCheckRoutes
    } ~
      pathPrefix("api") {
        pathPrefix("projects") {
          projectRoutes
        } ~
          pathPrefix("platforms") {
            platformRoutes
          } ~
          pathPrefix("areas-of-interest") {
            aoiRoutes
          } ~
          pathPrefix("organizations") {
            organizationRoutes
          } ~
          pathPrefix("scenes") {
            sceneRoutes
          } ~
          pathPrefix("tokens") {
            tokenRoutes
          } ~
          pathPrefix("users") {
            userRoutes
          } ~
          pathPrefix("tools") {
            toolRoutes
          } ~
          pathPrefix("tool-tags") {
            toolTagRoutes
          } ~
          pathPrefix("tool-categories") {
            toolCategoryRoutes
          } ~
          pathPrefix("tool-runs") {
            toolRunRoutes
          } ~
          pathPrefix("datasources") {
            datasourceRoutes
          } ~
          pathPrefix("thumbnails") {
            thumbnailImageRoutes
          } ~
          pathPrefix("map-tokens") {
            mapTokenRoutes
          } ~
          pathPrefix("feed") {
            feedRoutes
          } ~
          pathPrefix("uploads") {
            uploadRoutes
          } ~
          pathPrefix("exports") {
            exportRoutes
          } ~
          pathPrefix("shapes") {
            shapeRoutes
          } ~
          pathPrefix("licenses") {
            licenseRoutes
          } ~
          pathPrefix("teams") {
            teamRoutes
          }
      } ~
      pathPrefix("config") {
        configRoutes
      } ~
      pathPrefix("feature-flags") {
        featureFlagRoutes
      } ~
      pathPrefix("thumbnails") {
        thumbnailImageRoutes
      }
  }
}
