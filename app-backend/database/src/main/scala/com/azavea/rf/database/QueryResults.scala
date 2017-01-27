package com.azavea.rf.database.query

import slick.dbio.DBIO

case class ListQueryResult[T](
  records: DBIO[Seq[T]],
  nRecords: DBIO[Int]
)
