package com.azavea.rf.batch

import com.azavea.rf.common.RollbarNotifier
import com.azavea.rf.batch.util.conf.Config

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

trait Job extends Config with RollbarNotifier {
  val name: String

  implicit lazy val system           = ActorSystem(s"$name-system")
  implicit lazy val materializer     = ActorMaterializer()
  implicit lazy val executionContext = materializer.executionContext

  /** ActorSystem needs to be closed manually. */
  def stop: Unit = system.terminate()

  /** Run function should be defined for all Jobs */
  def run: Unit
}
