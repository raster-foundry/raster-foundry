package com.azavea.rf.backsplash.error

sealed trait BacksplashError extends Exception

case class MetadataError(message: String) extends BacksplashError
case class SingleBandOptionsError(message: String) extends BacksplashError
case class UningestedScenes(message: String) extends BacksplashError
case class UnknownSceneType(message: String) extends BacksplashError
case class NotAuthorized(message: String = "") extends BacksplashError
