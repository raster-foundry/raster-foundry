package com.azavea.rf.tool

import com.azavea.rf.tool.ast._

package object maml {
  implicit class MapAlgebraAstConversion(val rfmlAst: MapAlgebraAST)
      extends MamlAdapter
}
