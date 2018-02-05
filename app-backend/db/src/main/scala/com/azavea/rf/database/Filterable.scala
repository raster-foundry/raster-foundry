package com.azavea.rf.database

import doobie._, doobie.implicits._


case class Filterable[Model, T](toFilters: T => List[Option[Fragment]])

