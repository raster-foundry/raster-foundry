package com.rasterfoundry.http4s.xray

import com.rasterfoundry.http4s.Config

import cats.effect.Sync
import io.circe.Printer
import io.circe.syntax._

import java.net._

object UdpClient {

  val printer = Printer(
    dropNullValues = true,
    indent = ""
  )

  val address = new InetSocketAddress(Config.xray.host, 2000)
  val socket = new DatagramSocket()

  def write[F[_]](segment: Segment[F])(implicit sf: Sync[F]): F[Unit] =
    Sync[F].delay {
      val segmentJson = printer.print(segment.asJson)
      val versionString = """{"format": "json", "version": 1}"""
      val payload = s"$versionString\n$segmentJson".getBytes
      val packet = new DatagramPacket(payload, payload.length, address)
      socket.send(packet)
    }

}
