package com.azavea.rf.api


import ch.megard.akka.http.cors.CorsDirectives._
import ch.megard.akka.http.cors.CorsSettings
import com.azavea.rf.api.project.ProjectRoutes
import com.azavea.rf.api.healthcheck._
import com.azavea.rf.api.organization.OrganizationRoutes
import com.azavea.rf.api.scene.SceneRoutes
import com.azavea.rf.api.thumbnail.ThumbnailRoutes
import com.azavea.rf.api.user.UserRoutes
import com.azavea.rf.api.image.ImageRoutes
import com.azavea.rf.api.config.ConfigRoutes
import com.azavea.rf.api.tool.ToolRoutes
import com.azavea.rf.api.tooltag.ToolTagRoutes
import com.azavea.rf.api.token.TokenRoutes
import com.azavea.rf.api.toolcategory.ToolCategoryRoutes
import com.azavea.rf.api.toolrun.ToolRunRoutes
import com.azavea.rf.api.grid.GridRoutes
import com.azavea.rf.api.datasource.DatasourceRoutes
import com.azavea.rf.api.utils.Config

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
    with ToolCategoryRoutes
    with ToolRunRoutes
    with GridRoutes
    with DatasourceRoutes
    with Config {

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
      pathPrefix("tool-categories") { toolCategoryRoutes } ~
      pathPrefix("tool-runs") { toolRunRoutes } ~
      pathPrefix("scene-grid") { gridRoutes } ~
      pathPrefix("datasources") { datasourceRoutes }
    } ~
    pathPrefix("config") {
      configRoutes
    } ~
    pathPrefix("thumbnails") {
      thumbnailImageRoutes
    }
  }
}
