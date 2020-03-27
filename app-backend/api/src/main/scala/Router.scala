package com.rasterfoundry.api

import com.rasterfoundry.api.annotationProject.AnnotationProjectRoutes
import com.rasterfoundry.api.config.ConfigRoutes
import com.rasterfoundry.api.datasource.DatasourceRoutes
import com.rasterfoundry.api.exports.ExportRoutes
import com.rasterfoundry.api.featureflags.FeatureFlagRoutes
import com.rasterfoundry.api.healthcheck._
import com.rasterfoundry.api.license.LicenseRoutes
import com.rasterfoundry.api.maptoken.MapTokenRoutes
import com.rasterfoundry.api.organization.OrganizationRoutes
import com.rasterfoundry.api.platform.PlatformRoutes
import com.rasterfoundry.api.project.ProjectRoutes
import com.rasterfoundry.api.scene.SceneRoutes
import com.rasterfoundry.api.shape.ShapeRoutes
import com.rasterfoundry.api.stac.StacRoutes
import com.rasterfoundry.api.tasks.TaskRoutes
import com.rasterfoundry.api.team.TeamRoutes
import com.rasterfoundry.api.thumbnail.ThumbnailRoutes
import com.rasterfoundry.api.token.TokenRoutes
import com.rasterfoundry.api.tool.ToolRoutes
import com.rasterfoundry.api.toolrun.ToolRunRoutes
import com.rasterfoundry.api.uploads.UploadRoutes
import com.rasterfoundry.api.user.UserRoutes
import com.rasterfoundry.api.utils.Config

import akka.http.scaladsl.model.HttpMethods._
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
    with TokenRoutes
    with ThumbnailRoutes
    with ToolRoutes
    with ConfigRoutes
    with ToolRunRoutes
    with DatasourceRoutes
    with MapTokenRoutes
    with UploadRoutes
    with ExportRoutes
    with Config
    with FeatureFlagRoutes
    with ShapeRoutes
    with LicenseRoutes
    with TeamRoutes
    with PlatformRoutes
    with StacRoutes
    with AnnotationProjectRoutes
    with TaskRoutes {

  val settings = CorsSettings.defaultSettings.copy(
    allowedMethods = Seq(GET, POST, PUT, HEAD, OPTIONS, DELETE)
  )

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
          } ~
          pathPrefix("stac") {
            stacRoutes
          } ~
          pathPrefix("annotation-projects") {
            annotationProjectRoutes
          } ~
          pathPrefix("tasks") {
            taskRoutes
          }
      } ~
      pathPrefix("config") {
        configRoutes
      } ~
      pathPrefix("feature-flags") {
        featureFlagRoutes
      }
  }
}
