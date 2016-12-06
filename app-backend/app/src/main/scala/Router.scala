package com.azavea.rf


import scala.concurrent.ExecutionContext
import ch.megard.akka.http.cors.CorsDirectives._
import ch.megard.akka.http.cors.CorsSettings
import com.azavea.rf.project.ProjectRoutes
import com.azavea.rf.healthcheck._
import com.azavea.rf.organization.OrganizationRoutes
import com.azavea.rf.scene.SceneRoutes
import com.azavea.rf.thumbnail.ThumbnailRoutes
import com.azavea.rf.user.UserRoutes
import com.azavea.rf.image.ImageRoutes
import com.azavea.rf.config.ConfigRoutes
import com.azavea.rf.database.Database
import com.azavea.rf.tool.ToolRoutes
import com.azavea.rf.tooltag.ToolTagRoutes
import com.azavea.rf.token.TokenRoutes
import com.azavea.rf.toolcategory.ToolCategoryRoutes


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
    with ImageRoutes
    with TokenRoutes
    with ThumbnailRoutes
    with ToolRoutes
    with ToolTagRoutes
    with ConfigRoutes
    with ToolCategoryRoutes {

  implicit def database: Database

  val corsSettings = CorsSettings.defaultSettings

  val routes = cors() {
    pathPrefix("healthcheck") {
      healthCheckRoutes
    } ~
    pathPrefix("api") {
      pathPrefix("projects") { projectRoutes } ~
      pathPrefix("images") { imageRoutes } ~
      pathPrefix("organizations") { organizationRoutes } ~
      pathPrefix("scenes") { sceneRoutes } ~
      pathPrefix("thumbnails") { thumbnailRoutes } ~
      pathPrefix("tokens") { tokenRoutes } ~
      pathPrefix("users") { userRoutes } ~
      pathPrefix("tools") { toolRoutes } ~
      pathPrefix("tool-tags") { toolTagRoutes } ~
      pathPrefix("tool-categories") { toolCategoryRoutes }
    } ~
    pathPrefix("config") {
      configRoutes
    }
  }
}
