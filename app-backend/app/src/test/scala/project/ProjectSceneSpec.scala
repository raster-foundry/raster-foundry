package com.azavea.rf.project

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.{HttpEntity, ContentTypes}
import akka.http.scaladsl.testkit.{ScalatestRouteTest, RouteTestTimeout}
import com.azavea.rf.datamodel._
import concurrent.duration._
import org.scalatest.{Matchers, WordSpec}
import spray.json._

import com.azavea.rf.scene._
import com.azavea.rf.utils.Config
import com.azavea.rf.{AuthUtils, DBSpec, Router}


/** Tests to exercise adding/deleting scenes to/from a project */
class ProjectSceneSpec extends WordSpec
    with ProjectSpecHelper
    with Matchers
    with ScalatestRouteTest
    with Config
    with Router
    with DBSpec {
  implicit val ec = system.dispatcher

  implicit def database = db
  implicit def default(implicit system: ActorSystem) = RouteTestTimeout(DurationInt(20).second)

  // Alias to baseRoutes to be explicit
  val baseRoutes = routes
  val authHeader = AuthUtils.generateAuthHeader("Default")


  "/api/projects/{project}/scenes/" should {
    "allow creating projects and scenes" in {
      Post("/api/projects/").withHeadersAndEntity(
        List(authHeader),
        HttpEntity(
          ContentTypes.`application/json`,
          newProject1.toJson.toString()
        )
      ) ~> baseRoutes ~> check {
        responseAs[Project]
      }

      Post("/api/scenes/").withHeadersAndEntity(
        List(authHeader),
        HttpEntity(
          ContentTypes.`application/json`,
          newScene("first test scene").toJson.toString()
        )
      ) ~> baseRoutes ~> check {
        responseAs[Scene.WithRelated]
      }

      Post("/api/scenes/").withHeadersAndEntity(
        List(authHeader),
        HttpEntity(
          ContentTypes.`application/json`,
          newScene("second test scene").toJson.toString()
        )
      ) ~> baseRoutes ~> check {
        responseAs[Scene.WithRelated]
      }
    }

    "not have any scenes attached to initial project" in {
      Get("/api/projects/").withHeaders(
        List(authHeader)
      ) ~> baseRoutes ~> check {
        val projects = responseAs[PaginatedResponse[Project]]
        val projectId = projects.results.head.id
        Get(s"/api/projects/${projectId}/scenes/").withHeaders(
          List(authHeader)
        ) ~> baseRoutes ~> check {
          responseAs[PaginatedResponse[Scene.WithRelated]].count shouldEqual 0
        }
      }
    }

    "be able to attach scene to project via post" in {
      // Get projects to get ID
      Get("/api/projects/").withHeaders(
        List(authHeader)
      ) ~> baseRoutes ~> check {
        val projects = responseAs[PaginatedResponse[Project]]
        val projectId = projects.results.head.id

        // Get scenes to get ID
        Get("/api/scenes/").withHeaders(
          List(authHeader)
        ) ~> baseRoutes ~> check {
          val scenes = responseAs[PaginatedResponse[Scene.WithRelated]]
          val sceneId = scenes.results(0).id

          Post(s"/api/projects/${projectId}/scenes/").withHeadersAndEntity(
            List(authHeader),
            HttpEntity(
              ContentTypes.`application/json`,
              List(sceneId).toJson.toString()
            )
          ) ~> baseRoutes ~> check {
            status shouldEqual StatusCodes.OK
          }
        }
      }
    }

    "have one scene attached to project" in {
      Get("/api/projects/").withHeaders(
        List(authHeader)
      ) ~> baseRoutes ~> check {
        val projects = responseAs[PaginatedResponse[Project]]
        val projectId = projects.results.head.id
        Get(s"/api/projects/${projectId}/scenes/").withHeaders(
          List(authHeader)
        ) ~> baseRoutes ~> check {
          responseAs[PaginatedResponse[Scene.WithRelated]].count shouldEqual 1
        }
      }
    }

    "be able to attach a second scene to project via post" in {
      // Get projects to get ID
      Get("/api/projects/").withHeaders(
        List(authHeader)
      ) ~> baseRoutes ~> check {
        val projects = responseAs[PaginatedResponse[Project]]
        val projectId = projects.results.head.id

        // Get scenes to get ID
        Get("/api/scenes/").withHeaders(
          List(authHeader)
        ) ~> baseRoutes ~> check {
          val scenes = responseAs[PaginatedResponse[Scene.WithRelated]]
          val sceneId = scenes.results(1).id

          Post(s"/api/projects/${projectId}/scenes/").withHeadersAndEntity(
            List(authHeader),
            HttpEntity(
              ContentTypes.`application/json`,
              List(sceneId).toJson.toString()
            )
          ) ~> baseRoutes ~> check {
            status shouldEqual StatusCodes.OK
          }
        }
      }
    }

    "have two scenes attached to project" in {
      Get("/api/projects/").withHeaders(
        List(authHeader)
      ) ~> baseRoutes ~> check {
        val projects = responseAs[PaginatedResponse[Project]]
        val projectId = projects.results.head.id
        Get(s"/api/projects/${projectId}/scenes/").withHeaders(
          List(authHeader)
        ) ~> baseRoutes ~> check {
          responseAs[PaginatedResponse[Scene.WithRelated]].count shouldEqual 2
        }
      }
    }

    "be able to apply filters for scenes on project" in {
      Get("/api/projects/").withHeaders(
        List(authHeader)
      ) ~> baseRoutes ~> check {
        val projects = responseAs[PaginatedResponse[Project]]
        val projectId = projects.results.head.id
        Get(s"/api/projects/${projectId}/scenes/?datasource=DoesNotExist").withHeaders(
          List(authHeader)
        ) ~> baseRoutes ~> check {
          responseAs[PaginatedResponse[Scene.WithRelated]].count shouldEqual 0
        }
      }
    }

    "be able apply a user defined ordering for scenes on project" in {
      // Get projects to get ID
      Get("/api/projects/").withHeaders(
        List(authHeader)
      ) ~> baseRoutes ~> check {
        val projects = responseAs[PaginatedResponse[Project]]
        val projectId = projects.results.head.id

        // Get scenes to get ID
        Get(s"/api/projects/${projectId}/scenes/ordered/").withHeaders(
          List(authHeader)
        ) ~> baseRoutes ~> check {
          val sceneIds1 = responseAs[Seq[java.util.UUID]]

          Post(s"/api/projects/${projectId}/scenes/ordered").withHeadersAndEntity(
            List(authHeader),
            HttpEntity(
              ContentTypes.`application/json`,
              sceneIds1.reverse.toJson.toString()
            )
          ) ~> baseRoutes ~> check {
            status shouldEqual StatusCodes.OK

            Get(s"/api/projects/${projectId}/scenes/ordered/").withHeaders(
              List(authHeader)
            ) ~> baseRoutes ~> check {
              val sceneIds2 = responseAs[Seq[java.util.UUID]]
              println(s"IDs here: $sceneIds1, $sceneIds2")

              sceneIds1(0) shouldEqual sceneIds2(1)
              sceneIds1(1) shouldEqual sceneIds2(0)
            }
          }
        }
      }
    }

    "be able to remove scene from project via delete" in {
      Get("/api/projects/").withHeaders(
        List(authHeader)
      ) ~> baseRoutes ~> check {
        val projects = responseAs[PaginatedResponse[Project]]
        val projectId = projects.results.head.id

        // Get scenes to get ID
        Get("/api/scenes/").withHeaders(
          List(authHeader)
        ) ~> baseRoutes ~> check {
          val scenes = responseAs[PaginatedResponse[Scene.WithRelated]]
          val sceneId = scenes.results.head.id

          Delete(s"/api/projects/${projectId}/scenes/").withHeadersAndEntity(
            List(authHeader),
            HttpEntity(
              ContentTypes.`application/json`,
              List(sceneId).toJson.toString()
            )
          ) ~> baseRoutes ~> check {
            status shouldEqual StatusCodes.NoContent
          }
        }
      }
    }

    "not have two scenes attached to project after deleting one" in {
      Get("/api/projects/").withHeaders(
        List(authHeader)
      ) ~> baseRoutes ~> check {
        val projects = responseAs[PaginatedResponse[Project]]
        val projectId = projects.results.head.id
        Get(s"/api/projects/${projectId}/scenes/").withHeaders(
          List(authHeader)
        ) ~> baseRoutes ~> check {
          responseAs[PaginatedResponse[Scene.WithRelated]].count shouldEqual 1
        }
      }
    }
  }
}
