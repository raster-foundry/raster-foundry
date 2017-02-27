package com.azavea.rf.datamodel

trait CanRelate[Related <: {type RelatedResultType; type RelatedType}] {
  val inner: Related
  type RelatedResultType = inner.RelatedResultType
  type RelatedType = inner.RelatedType
  def relate(x: Seq[inner.RelatedType]): inner.RelatedResultType
}

abstract class WithRelated2Constructor {
  type T1
  type RelatedType
  type Related[T1]

  def withRelatedFromComponents(s: Seq[RelatedType]): Related[T1]
}
