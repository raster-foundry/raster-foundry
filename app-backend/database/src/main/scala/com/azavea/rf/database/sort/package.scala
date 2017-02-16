package com.azavea.rf.database

import com.azavea.rf.database.ExtendedPostgresDriver.api._

package object sort {
  implicit class withQuerySortMethods[E, U, C[_]](query: Query[E, U, C])
    extends QuerySortMethods(query)
}