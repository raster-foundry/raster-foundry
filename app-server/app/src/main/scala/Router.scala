package com.azavea.rf

import scala.concurrent.ExecutionContext

import ch.megard.akka.http.cors.CorsDirectives._
import ch.megard.akka.http.cors.CorsSettings

import com.azavea.rf.bucket.BucketRoutes
import com.azavea.rf.healthcheck._
import com.azavea.rf.organization.OrganizationRoutes
import com.azavea.rf.scene.SceneRoutes
import com.azavea.rf.user.UserRoutes
import com.azavea.rf.utils.Database


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
    with BucketRoutes {

  implicit def database: Database
  implicit val ec: ExecutionContext

  val corsSettings = CorsSettings.defaultSettings

  val routes = cors() {
    healthCheckRoutes ~
    userRoutes ~
    organizationRoutes ~
    sceneRoutes ~
    bucketRoutes
  }
}
