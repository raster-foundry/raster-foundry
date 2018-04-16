package com.azavea.rf.database

import com.azavea.rf.datamodel.Generators.Implicits._
import com.azavea.rf.datamodel.LayerAttribute
import com.azavea.rf.database.Implicits._

import doobie._, doobie.implicits._
import cats._, cats.data._, cats.effect.IO
import cats.implicits._
import cats.syntax.either._
import doobie.postgres._, doobie.postgres.implicits._
import io.circe.syntax._
import org.scalacheck.Prop.forAll
import org.scalatest._
import org.scalatest.prop.Checkers


class LayerAttributeDaoSpec extends FunSuite with Matchers with Checkers with DBTestConfig {

  test("list all layer ids") {
    LayerAttributeDao.layerIds.transact(xa).unsafeRunSync.length should be >= 0
  }

  test("get max zooms for layers") {
    LayerAttributeDao.maxZoomsForLayers(Set.empty[String]).transact(xa).unsafeRunSync.length should be >= 0
  }

  // insertLayerAttribute
  test("insert a layer attribute") {
    check {
      forAll {
        (layerAttribute: LayerAttribute) => {
          val layerId = layerAttribute.layerId
          val attributeIO = LayerAttributeDao.insertLayerAttribute(layerAttribute) flatMap {
            _ => LayerAttributeDao.unsafeGetAttribute(layerId, layerAttribute.name)
          }
          attributeIO.transact(xa).unsafeRunSync == layerAttribute
        }
      }
    }
  }

  // listAllAttributes
  test("list layers for an attribute name") {
    check {
      forAll {
        (layerAttributes: List[LayerAttribute]) => {
          val attributesIO = layerAttributes.traverse(
            (attr: LayerAttribute) => {
              LayerAttributeDao.insertLayerAttribute(attr)
            }
          ) flatMap {
            _ => LayerAttributeDao.listAllAttributes(layerAttributes.head.name)
          }

          attributesIO.transact(xa).unsafeRunSync.length ==
            layerAttributes.filter(_.name == layerAttributes.head.name).length
        }
      }
    }
  }

  // layerExists
  test("check layer existence") {
    check {
      forAll {
        (layerAttribute: LayerAttribute) => {
          val layerId = layerAttribute.layerId
          val attributesIO = LayerAttributeDao.insertLayerAttribute(layerAttribute) flatMap {
            _ => LayerAttributeDao.layerExists(layerId)
          }
          attributesIO.transact(xa).unsafeRunSync
        }
      }
    }
  }
}

