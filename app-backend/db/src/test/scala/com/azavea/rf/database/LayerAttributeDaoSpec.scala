package com.rasterfoundry.database

import com.rasterfoundry.common.Generators.Implicits._
import com.rasterfoundry.common.LayerAttribute

import cats.implicits._
import doobie.implicits._
import org.scalacheck.Prop.forAll
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.Checkers

class LayerAttributeDaoSpec
    extends AnyFunSuite
    with Matchers
    with Checkers
    with DBTestConfig {

  test("list all layer ids") {
    LayerAttributeDao.layerIds.transact(xa).unsafeRunSync.length should be >= 0
  }

  test("get max zooms for layers") {
    LayerAttributeDao
      .maxZoomsForLayers(Set.empty[String])
      .transact(xa)
      .unsafeRunSync
      .length should be >= 0
  }

  // insertLayerAttribute
  test("insert a layer attribute") {
    check {
      forAll { (layerAttribute: LayerAttribute) =>
        {
          val layerId = layerAttribute.layerId
          val attributeIO = LayerAttributeDao.insertLayerAttribute(
            layerAttribute) flatMap { _ =>
            LayerAttributeDao.unsafeGetAttribute(layerId, layerAttribute.name)
          }
          attributeIO.transact(xa).unsafeRunSync == layerAttribute
        }
      }
    }
  }

  // listAllAttributes
  test("list layers for an attribute name") {
    check {
      forAll { (layerAttributes: List[LayerAttribute]) =>
        {
          val attributesIO = layerAttributes.traverse(
            (attr: LayerAttribute) => {
              LayerAttributeDao.insertLayerAttribute(attr)
            }
          ) flatMap { _ =>
            LayerAttributeDao.listAllAttributes(layerAttributes.head.name)
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
      forAll { (layerAttribute: LayerAttribute) =>
        {
          val layerId = layerAttribute.layerId
          val attributesIO = LayerAttributeDao.insertLayerAttribute(
            layerAttribute) flatMap { _ =>
            LayerAttributeDao.layerExists(layerId)
          }
          attributesIO.transact(xa).unsafeRunSync
        }
      }
    }
  }
}
