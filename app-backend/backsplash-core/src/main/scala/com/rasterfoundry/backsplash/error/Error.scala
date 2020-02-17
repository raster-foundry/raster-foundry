package com.rasterfoundry.backsplash.error

import com.rasterfoundry.backsplash._

import cats._
import cats.data._
import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import geotrellis.raster._
import org.http4s._
import org.http4s.dsl._
import org.http4s.headers._

sealed trait BacksplashException extends Exception

// When there's something wrong with stored metadata for the calculations we'd like to do
class MetadataException(val message: String) extends BacksplashException
object MetadataException {
  def unapply(m: MetadataException): Option[String] = Some(m.message)
}

case object NoScenesException
    extends MetadataException("Cannot construct a mosaic with no scenes")
case object NoFootprintException
    extends MetadataException("Scene does not have a footprint")
// When single band options are missing for a single band project or something else is wrong with them
final case class SingleBandOptionsException(message: String)
    extends BacksplashException
// When scenes don't have an ingest location but show as ingested
final case class UningestedScenesException(message: String)
    extends BacksplashException
// When scene type is something other than Some(COG) or Some(Avro)
final case class UnknownSceneTypeException(message: String)
    extends BacksplashException
// When an authenticated user is requesting access to something that they aren't allowed to see
final case class NotAuthorizedException(message: String = "")
    extends BacksplashException
// When the AST for an analysis is bad somehow
final case class BadAnalysisASTException(message: String)
    extends BacksplashException
// When requirements fail, or bboxes don't overlap things, or...
final case class RequirementFailedException(message: String)
    extends BacksplashException
// When we get any doobie error
final case class WrappedDoobieException(message: String)
    extends BacksplashException
// When an area is requested completely outside the extent of a project/scene/analysis
case object NoDataInRegionException extends BacksplashException

// When we get any S3 error
final case class WrappedS3Exception(message: String) extends BacksplashException
// Private so no one can deliberately throw an UnknownException elsewhere --
// only exists to catch the fall-through case in the foreign error handler
private[backsplash] final case class UnknownException(message: String)
    extends BacksplashException

class BacksplashHttpErrorHandler[F[_]](
    implicit M: MonadError[F, BacksplashException]
) extends HttpErrorHandler[F, BacksplashException]
    with Http4sDsl[F]
    with LazyLogging {
  val invisiCellType = IntUserDefinedNoDataCellType(0)
  val invisiTile = IntUserDefinedNoDataArrayTile(Array.fill(65536)(0),
                                                 256,
                                                 256,
                                                 invisiCellType)
  private val handler: (Request[F], BacksplashException) => F[Response[F]] = {
    case (_, NoScenesException) =>
      Ok(invisiTile.renderPng.bytes, `Content-Type`(MediaType.image.png))
    case (req, MetadataException(m)) =>
      logger.error(
        s"Encountered Metadata Exception for ${req.uri.path} with Trace Id ${req.traceID}: $m")
      InternalServerError(m)
    case (_, UningestedScenesException(m))  => NotFound(m)
    case (_, SingleBandOptionsException(m)) => BadRequest(m)
    case (_, UnknownSceneTypeException(m))  => BadRequest(m)
    case (_, BadAnalysisASTException(m))    => BadRequest(m)
    case (_, RequirementFailedException(m)) => BadRequest(m)
    case (_, NoDataInRegionException)       => BadRequest("No Data in Region")
    case (_, NotAuthorizedException(_)) =>
      Forbidden(
        "Resource does not exist or user is not authorized to access this resource"
      )
    case (_, WrappedDoobieException(m)) =>
      NotFound(m)
    case (_, WrappedS3Exception(_)) =>
      NotFound(
        "Underlying data to produce tiles for this project appears to have moved or is no longer available"
      )
    case (_, UnknownException(m)) =>
      InternalServerError(m)
  }

  override def handle(service: HttpRoutes[F]): HttpRoutes[F] =
    ServiceHttpErrorHandler(service)(handler)
}

object ServiceHttpErrorHandler {
  def apply[F[_], E, U](service: HttpRoutes[F])(
      handler: (Request[F], E) => F[Response[F]]
  )(implicit ev: ApplicativeError[F, E]): HttpRoutes[F] =
    Kleisli { req: Request[F] =>
      OptionT {
        service(req).value.handleErrorWith { e =>
          handler(req, e).map(Option(_))
        }
      }
    }
}

trait HttpErrorHandler[F[_], E <: Throwable] extends LazyLogging {
  def handle(service: HttpRoutes[F]): HttpRoutes[F]
}

object HttpErrorHandler {
  def apply[F[_], E <: Throwable](implicit ev: HttpErrorHandler[F, E]) =
    ev
}
