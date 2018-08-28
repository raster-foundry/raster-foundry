package com.azavea.rf.datamodel

import slick.dbio.DBIO

final case class ListQueryResult[T](records: DBIO[Seq[T]], nRecords: DBIO[Int])
