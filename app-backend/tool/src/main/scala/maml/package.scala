package com.rasterfoundry.tool

import com.rasterfoundry.tool.ast._

package object maml {
  implicit class MapAlgebraAstConversion(val rfmlAst: MapAlgebraAST)
      extends MamlAdapter
}
