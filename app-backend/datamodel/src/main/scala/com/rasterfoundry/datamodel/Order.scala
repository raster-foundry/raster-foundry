package com.rasterfoundry.datamodel

sealed trait Order

object Order {

  case object Asc extends Order

  case object Desc extends Order

}
