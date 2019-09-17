package com.rasterfoundry.http4s.xray

import java.net._

import cats.effect.Sync
import io.circe.syntax._
import io.circe.Printer

class UdpClient[F[_]: Sync] {

  val printer = Printer(
    preserveOrder = true,
    dropNullValues = true,
    indent = ""
  )

  val address = new InetSocketAddress("xray.service.internal", 2000)
  val socket = new DatagramSocket()

  def write(segment: Segment[F]): F[Unit] = Sync[F].delay {
    val segmentJson = printer.pretty(segment.asJson)
    val versionString = """{"format": "json", "version": 1}"""
    val payload = s"$versionString\n$segmentJson".getBytes
    val packet = new DatagramPacket(payload, payload.length, address)
    socket.send(packet)
  }

}
