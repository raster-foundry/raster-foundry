package com.azavea.rf.api

import akka.http.scaladsl.model.HttpMethods._
import com.azavea.rf.api.aoi.AoiRoutes
import com.azavea.rf.api.config.ConfigRoutes
import com.azavea.rf.api.datasource.DatasourceRoutes
import com.azavea.rf.api.exports.ExportRoutes
import com.azavea.rf.api.featureflags.FeatureFlagRoutes
import com.azavea.rf.api.feed.FeedRoutes
import com.azavea.rf.api.healthcheck._
import com.azavea.rf.api.image.ImageRoutes
import com.azavea.rf.api.maptoken.MapTokenRoutes
import com.azavea.rf.api.organization.OrganizationRoutes
import com.azavea.rf.api.project.ProjectRoutes
import com.azavea.rf.api.scene.SceneRoutes
import com.azavea.rf.api.shape.ShapeRoutes
import com.azavea.rf.api.thumbnail.ThumbnailRoutes
import com.azavea.rf.api.token.TokenRoutes
import com.azavea.rf.api.tool.ToolRoutes
import com.azavea.rf.api.toolcategory.ToolCategoryRoutes
import com.azavea.rf.api.toolrun.ToolRunRoutes
import com.azavea.rf.api.tooltag.ToolTagRoutes
import com.azavea.rf.api.uploads.UploadRoutes
import com.azavea.rf.api.user.UserRoutes
import com.azavea.rf.api.utils.Config
import ch.megard.akka.http.cors.scaladsl.CorsDirectives._
import ch.megard.akka.http.cors.scaladsl.settings._
import com.azavea.rf.api.license.LicenseRoutes

import scala.collection.immutable.Seq

/**
  * Contains all routes for Raster Foundry API/Healthcheck endpoints.
  *
  * Actual routes should be written in the relevant feature as much as is feasible
  *
  */
trait Router extends HealthCheckRoutes
  with UserRoutes
  with OrganizationRoutes
  with SceneRoutes
  with ProjectRoutes
  with AoiRoutes
  with ImageRoutes
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
  with LicenseRoutes {

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
          pathPrefix("areas-of-interest") {
            aoiRoutes
          } ~
          pathPrefix("images") {
            imageRoutes
          } ~
          pathPrefix("organizations") {
            organizationRoutes
          } ~
          pathPrefix("scenes") {
            sceneRoutes
          } ~
          pathPrefix("thumbnails") {
            thumbnailRoutes
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
