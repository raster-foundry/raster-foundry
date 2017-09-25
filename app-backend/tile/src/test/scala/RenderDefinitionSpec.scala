package com.azavea.rf.tile

import com.azavea.rf.tool._
import com.azavea.rf.tile.image._

import cats.syntax.either._
import io.circe._
import io.circe.syntax._
import io.circe.generic.semiauto._
import io.circe.parser.decode
import org.scalatest._
import geotrellis.raster._
import geotrellis.raster.render._

import scala.util.Try


class RenderDefinitionSpec extends FunSpec with Matchers {

  ignore("should clip both sides (double)") {
    val renderDef = RenderDefinition(
      Map(0.1 -> RGBA(0, 255, 0, 255), 0.2 -> RGBA(255, 0, 0, 255), 0.325 -> RGBA(0, 0, 255, 255)),
      Continuous,
      ClipLeft
    )
    val tile = DoubleArrayTile((0 to 9999 by 1 toArray).map { num => (num.toDouble / 1000.0) % .5}, 500, 20)
    DisplayTile(tile.renderPng(renderDef).bytes, tile.cols, tile.rows)
  }

  ignore("should clip both sides (integer)") {
    val renderDef = RenderDefinition(
      Map(100.0 -> RGBA(0, 255, 0, 255), 200.0 -> RGBA(255, 0, 0, 255), 325.0 -> RGBA(0, 0, 255, 255)),
      Continuous,
      ClipLeft
    )
    val tile = IntArrayTile((0 to 9999 by 1 toArray).map { _ % 500}, 500, 20)
    DisplayTile(tile.renderPng(renderDef).bytes, tile.cols, tile.rows)
  }
}

/* A helper method to display a tile */
object DisplayTile {
  import java.awt.FlowLayout;
  import java.awt.image.BufferedImage;
  import java.io.File;
  import java.io.IOException;
  import javax.imageio.ImageIO;
  import javax.imageio.stream.MemoryCacheImageInputStream
  import java.io.ByteArrayInputStream
  import javax.swing.ImageIcon;
  import javax.swing.JFrame;
  import javax.swing.JLabel;

  def apply(bytes: Array[Byte], cols: Int, rows: Int) = {
    val icon: ImageIcon = new ImageIcon(bytes);
    val frame: JFrame =new JFrame();
    frame.setLayout(new FlowLayout());
    frame.setSize(cols + 20, rows + 40);

    val lbl: JLabel = new JLabel();
    lbl.setIcon(icon);

    frame.add(lbl);
    frame.setVisible(true);
  }
}

