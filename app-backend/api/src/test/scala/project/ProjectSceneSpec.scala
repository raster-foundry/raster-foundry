package com.azavea.rf.api.project

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.{HttpEntity, ContentTypes}
import akka.http.scaladsl.testkit.{ScalatestRouteTest, RouteTestTimeout}
import com.azavea.rf.datamodel._
import com.azavea.rf.database.query._
import concurrent.duration._
import org.scalatest.{Matchers, WordSpec}
import spray.json._

import com.azavea.rf.api.scene._
import com.azavea.rf.api.utils.Config
import com.azavea.rf.api.{AuthUtils, DBSpec, Router}

import java.util.UUID

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
  implicit def default(implicit system: ActorSystem) = RouteTestTimeout(DurationInt(5).second)

  // Alias to baseRoutes to be explicit
  val baseRoutes = routes
  val authHeader = AuthUtils.generateAuthHeader("Default")
  val alternateAuthHeader = AuthUtils.generateAuthHeader("Other")

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

    "not be able to attach a scene to an un-owned project via post" in {
      // Get projects to get ID
      Post("/api/projects/").withHeadersAndEntity(
        List(alternateAuthHeader),
        HttpEntity(
          ContentTypes.`application/json`,
          newProject4.toJson.toString()
        )
      ) ~> baseRoutes ~> check {
        val unauthedProject = responseAs[Project]

        // Get scenes to get ID
        Get("/api/scenes/").withHeaders(
          List(authHeader)
        ) ~> baseRoutes ~> check {
          val scenes = responseAs[PaginatedResponse[Scene.WithRelated]]
          val sceneId = scenes.results(0).id

          Post(s"/api/projects/${unauthedProject.id}/scenes/").withHeadersAndEntity(
            List(authHeader),
            HttpEntity(
              ContentTypes.`application/json`,
              List(sceneId).toJson.toString()
            )
          ) ~> baseRoutes ~> check {
            reject
          }
        }
      }
    }

    "not be able to attach a private and unowned scene to a project via post" in {
      // Get projects to get ID
      Get("/api/projects/").withHeaders(
        List(authHeader)
      ) ~> baseRoutes ~> check {
        val projects = responseAs[PaginatedResponse[Project]]
        val projectId = projects.results.head.id

        // Get scenes to get ID
        Post("/api/scenes/").withHeadersAndEntity(
          List(alternateAuthHeader),
          HttpEntity(
            ContentTypes.`application/json`,
            newPrivateScene("third test scene - private, unowned").toJson.toString()
          )
        ) ~> baseRoutes ~> check {
          val privateScene = responseAs[Scene.WithRelated]

          Post(s"/api/projects/${projectId}/scenes/").withHeadersAndEntity(
            List(authHeader),
            HttpEntity(
              ContentTypes.`application/json`,
              List(privateScene.id).toJson.toString()
            )
          ) ~> baseRoutes ~> check {
            reject
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

    "attach a second scene with a project" in {
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
        val wrongUUID = UUID.fromString("3bf183cd-cf47-4e4c-9f13-78728cbca519")
        Get(s"/api/projects/${projectId}/scenes/?datasource=$wrongUUID").withHeaders(
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
        Get(s"/api/projects/${projectId}/order/").withHeaders(
          List(authHeader)
        ) ~> baseRoutes ~> check {
          val sceneIds1 = responseAs[PaginatedResponse[UUID]].results

          Put(s"/api/projects/${projectId}/order/").withHeadersAndEntity(
            List(authHeader),
            HttpEntity(
              ContentTypes.`application/json`,
              sceneIds1.reverse.toJson.toString()
            )
          ) ~> baseRoutes ~> check {
            status shouldEqual StatusCodes.NoContent

            Get(s"/api/projects/${projectId}/order/").withHeaders(
              List(authHeader)
            ) ~> baseRoutes ~> check {
              val sceneIds2 = responseAs[PaginatedResponse[UUID]].results

              sceneIds1(0) shouldEqual sceneIds2(1)
              sceneIds1(1) shouldEqual sceneIds2(0)
            }
          }
        }
      }
    }

    "associate color correction parameters with a scene/project pairing and get that project's mosaic definition" in {
      val colorCorrectParams = ColorCorrect.Params(
        1, 2, 3,                           // Band Order (R, G, B)
        Some(0.53), Some(0.8), Some(0.32), // Gamma levels
        Some(10), Some(20),                // Contrast, Brightness
        None, None,                        // Alpha, Beta
        None, None,                        // Min, Max
        false                              // Equalize?
      )

      Get("/api/projects/").withHeaders(
        List(authHeader)
      ) ~> baseRoutes ~> check {
        val projects = responseAs[PaginatedResponse[Project]]
        val projectId = projects.results.head.id

        // Get scenes to get an ID
        Get(s"/api/projects/${projectId}/order/").withHeaders(
          List(authHeader)
        ) ~> baseRoutes ~> check {
          val sceneId = responseAs[PaginatedResponse[UUID]].results.head

          Put(s"/api/projects/${projectId}/mosaic/${sceneId}/").withHeadersAndEntity(
            List(authHeader),
            HttpEntity(
              ContentTypes.`application/json`,
              colorCorrectParams.toJson.toString()
            )
          ) ~> baseRoutes ~> check {
            status shouldEqual StatusCodes.NoContent

            Get(s"/api/projects/${projectId}/mosaic/${sceneId}/").withHeaders(
              List(authHeader)
            ) ~> baseRoutes ~> check {
              val responseColorCorrectParams = responseAs[ColorCorrect.Params]

              responseColorCorrectParams shouldEqual colorCorrectParams
            }

            Get(s"/api/projects/${projectId}/mosaic/").withHeaders(
              List(authHeader)
            ) ~> baseRoutes ~> check {
              val mosaicDef = responseAs[Seq[MosaicDefinition]]
              status shouldEqual StatusCodes.OK

              // TODO: Add additional tests once #880 is resolved
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

  "/api/projects/{project}/scenes/bulk-add-from-query/" should {
    val scene1 = newScene("fs1", Some(150.toFloat))
    val scene2 = newScene("fs2", Some(150.toFloat))
    val scene3 = newScene("fs3", Some(0.toFloat))
    val queryParams = CombinedSceneQueryParams(
      sceneParams = SceneQueryParameters(
        minCloudCover = Some(101.toFloat)
      )
    )

    "create a scene" in {
      Post("/api/scenes/").withHeadersAndEntity(
        List(authHeader),
        HttpEntity(
          ContentTypes.`application/json`,
          scene1.toJson.toString
        )
      ) ~> baseRoutes ~> check {
        responseAs[Scene.WithRelated]
      }
    }

    "attach a scene to a project from query params" in {
      val project = Post("/api/projects/").withHeadersAndEntity(
        List(authHeader),
        HttpEntity(
          ContentTypes.`application/json`,
          newProject3.toJson.toString()
        )
      ) ~> baseRoutes ~> check {
        responseAs[Project]
      }

      Post(s"/api/projects/${project.id}/scenes/bulk-add-from-query/").withHeadersAndEntity(
        List(authHeader),
        HttpEntity(
          ContentTypes.`application/json`,
          queryParams.toJson.toString
        )
      ) ~> baseRoutes ~> check {
        responseAs[Iterable[Scene.WithRelated]]
      }

      Get(s"/api/projects/${project.id}/scenes/").withHeaders(
        List(authHeader)
      ) ~> baseRoutes ~> check {
        responseAs[PaginatedResponse[Scene.WithRelated]].count shouldEqual 1
      }

      Post("/api/scenes/").withHeadersAndEntity(
        List(authHeader),
        HttpEntity(
          ContentTypes.`application/json`,
          scene2.toJson.toString()
        )
      ) ~> baseRoutes ~> check {
        responseAs[Scene.WithRelated]
      }

      Post("/api/scenes/").withHeadersAndEntity(
        List(authHeader),
        HttpEntity(
          ContentTypes.`application/json`,
          scene3.toJson.toString()
        )
      ) ~> baseRoutes ~> check {
        responseAs[Scene.WithRelated]
      }

      Post(s"/api/projects/${project.id}/scenes/bulk-add-from-query/").withHeadersAndEntity(
        List(authHeader),
        HttpEntity(
          ContentTypes.`application/json`,
          queryParams.toJson.toString()
        )
      ) ~> baseRoutes ~> check {
        responseAs[Iterable[Scene.WithRelated]]
      }

      Get(s"/api/projects/${project.id}/scenes/").withHeaders(
        List(authHeader)
      ) ~> baseRoutes ~> check {
        responseAs[PaginatedResponse[Scene.WithRelated]].count shouldEqual 2
      }
    }
  }
}
