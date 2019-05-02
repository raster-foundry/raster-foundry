package com.rasterfoundry.lambda.overviews
import java.net.URI
import java.util.UUID

import com.rasterfoundry.datamodel._
import com.rasterfoundry.datamodel.auth.{AuthorizedToken, RefreshToken}
import com.softwaremill.sttp._
import com.softwaremill.sttp.circe._

import scala.annotation.tailrec

/**
  * HTTP client for interacting with Raster Foundry
  */
object HttpClient {

  private val rfApiServer =
    scala.util.Properties.envOrElse("RF_API_SERVER", "http://localhost:9091")

  implicit val sttpBackend: SttpBackend[Id, Nothing] =
    HttpURLConnectionBackend()
  val client: RequestT[Empty, String, Nothing] =
    sttp.contentType("application/json")

  def getProjectLayerScenes(authToken: String,
                            projectId: UUID,
                            projectLayer: UUID): List[String] = {

    @tailrec
    def run(hasNext: Boolean,
            page: Int,
            pageSize: Int,
            sceneLocations: List[String]): List[String] = {
      if (hasNext) {
        val sceneLayerUrl =
          uri"$rfApiServer/api/projects/$projectId/layers/$projectLayer/scenes?page=$page&pageSize=$pageSize&pending=false"

        val responseIO = client
          .headers(Map("Authorization" -> s"Bearer $authToken"))
          .get(sceneLayerUrl)
          .response(asJson[PaginatedResponse[Scene.ProjectScene]])
          .send()
        responseIO.unsafeBody match {
          case Left(e) => throw e.error
          case Right(
              paginatedResponse: PaginatedResponse[Scene.ProjectScene]) =>
            run(paginatedResponse.hasNext,
                page + 1,
                pageSize,
                sceneLocations ++ paginatedResponse.results.flatMap(
                  _.ingestLocation))
        }
      } else sceneLocations
    }
    run(hasNext = true, page = 0, pageSize = 20, sceneLocations = List())
  }

  def updateProjectWithOverview(authToken: String,
                                projectId: UUID,
                                layerId: UUID,
                                overviewLocation: URI): ProjectLayer = {
    val projectLayerUri =
      uri"$rfApiServer/api/projects/$projectId/layers/$layerId"

    val projectLayerResponse = client
      .headers(Map("Authorization" -> s"Bearer $authToken"))
      .get(projectLayerUri)
      .response(asJson[ProjectLayer])
      .send()
    val projectLayer = projectLayerResponse.unsafeBody match {
      case Left(e)   => throw e.error
      case Right(pl) => pl
    }

    val updatedProjectLayer = client
      .headers(Map("Authorization" -> s"Bearer $authToken"))
      .put(projectLayerUri)
      .body(
        projectLayer.copy(overviewsLocation = Some(overviewLocation.toString)))
      .response(asJson[ProjectLayer])
      .send()
      .unsafeBody match {
      case Left(e)    => throw e.error
      case Right(upl) => upl
    }
    updatedProjectLayer
  }

  def getSystemToken(refreshToken: String): String = {
    val url = uri"$rfApiServer/api/tokens"

    client
      .body(RefreshToken(refreshToken))
      .post(url)
      .response(asJson[AuthorizedToken])
      .send()
      .unsafeBody match {
      case Left(e)          => throw e.error
      case Right(authToken) => authToken.id_token
    }
  }
}
