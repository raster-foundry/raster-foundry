package com.azavea.rf.database

import doobie._, doobie.implicits._

import annotation.implicitNotFound

/**
 * This case class is provided to allow the production of rules for transforming datatypes to doobie fragments
 */
@implicitNotFound("No instance of Filterable[${Model}, ${T}] in scope, check imports and make sure one is defined")
case class Filterable[-Model, T](toFilters: T => List[Option[Fragment]])

