package com.azavea.rf.backsplash.nodes

import com.azavea.maml.ast.{Literal, MamlKind, RasterLit}

import cats.effect.{IO, Timer}
import cats.implicits._

import geotrellis.raster.{CellSize, CellType, Raster}
import geotrellis.server.core.cog.CogUtils
import geotrellis.server.core.maml.CogNode
import geotrellis.server.core.maml.persistence._
import geotrellis.server.core.maml.metadata._
import geotrellis.server.core.maml.reification._
import geotrellis.vector.Extent

import io.circe._
import io.circe.generic.semiauto._

import java.net.URI

case class MultibandCogNode(
  uri: URI, redBand: Int, greenBand: Int, blueBand: Int, celltype: Option[CellType]
)

object MultibandCogNode {
  implicit val cellTypeEncoder: Encoder[CellType] = Encoder.encodeString.contramap[CellType](CellType.toName)
  implicit val cellTypeDecoder: Decoder[CellType] = Decoder[String].emap { name => Right(CellType.fromName(name)) }

  implicit val uriEncoder: Encoder[URI] = Encoder.encodeString.contramap[URI](_.toString)
  implicit val uriDecoder: Decoder[URI] = Decoder[String].emap { str => Right(URI.create(str)) }

  implicit val cogNodeEncoder: Encoder[MultibandCogNode] = deriveEncoder[MultibandCogNode]
  implicit val cogNodeDecoder: Decoder[MultibandCogNode] = deriveDecoder[MultibandCogNode]

  implicit val multibandCogNodeRasterExtents = CogNode.cogNodeRasterExtents

  implicit val multibandCogNodeTmsReification: MamlTmsReification[MultibandCogNode] =
    new MamlTmsReification[MultibandCogNode] {
      def kind(self: MultibandCogNode): MamlKind = MamlKind.Tile

      def tmsReification(self: MultibandCogNode, buffer: Int)(implicit t: Timer[IO]): (Int, Int, Int) => IO[Literal] =
        (z: Int, x: Int, y: Int) => {
          def fetch(xCoord: Int, yCoord: Int) =
            CogUtils.fetch(self.uri.toString, z, xCoord, yCoord)
              .map(_.tile)
              .map(_.subsetBands(self.redBand, self.greenBand, self.blueBand))
          fetch(x, y) map { RasterLit(_) }
        }
    }

  implicit val multibandCogNodeExtentReification: MamlExtentReification[MultibandCogNode] = new MamlExtentReification[MultibandCogNode] {
    def kind(self: MultibandCogNode): MamlKind = MamlKind.Tile
    def extentReification(self: MultibandCogNode)(implicit t: Timer[IO]): (Extent, CellSize) => IO[Literal] = (extent: Extent, cs: CellSize) => {
      CogUtils.getTiff(self.uri.toString)
        .map { CogUtils.cropGeoTiff(_, extent) }
        .map { RasterLit(_) }
    }
  }
}
