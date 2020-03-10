package com.rasterfoundry.common.ast

import com.azavea.maml.ast._
import com.azavea.maml.util.{NeighborhoodConversion, ClassMap => MamlClassMap}
import geotrellis.vector.io.json.Implicits._

object MamlConversion {
  def fromDeprecatedAST(ast: MapAlgebraAST): Expression = {

    def eval(ast: MapAlgebraAST): Expression = {

      val args: List[Expression] = ast.args.map(eval)
      ast match {
        case MapAlgebraAST.ProjectRaster(_, projId, band, _, _) => {
          val bandActual = band.getOrElse(1)
          RasterVar(s"${projId.toString}_${bandActual}")
        }
        case MapAlgebraAST.LayerRaster(_, layerId, band, _, _) => {
          val bandActual = band.getOrElse(1)
          RasterVar(s"${layerId.toString}_${bandActual}")
        }

        case MapAlgebraAST.Constant(_, const, _) => DblLit(const)
        case MapAlgebraAST.LiteralTile(_, _, _) =>
          throw new Exception(
            "No literal tiles should appear on pre-MAML RFML tools")
        case MapAlgebraAST.ToolReference(_, _) =>
          throw new Exception("Tool references not yet supported via MAML")
        /* --- LOCAL OPERATIONS --- */
        case MapAlgebraAST.Addition(_, _, _)       => Addition(args)
        case MapAlgebraAST.Subtraction(_, _, _)    => Subtraction(args)
        case MapAlgebraAST.Multiplication(_, _, _) => Multiplication(args)
        case MapAlgebraAST.Division(_, _, _)       => Division(args)
        case MapAlgebraAST.Max(_, _, _)            => Max(args)
        case MapAlgebraAST.Min(_, _, _)            => Min(args)
        case MapAlgebraAST.Classification(_, _, _, classmap) =>
          Classification(args, MamlClassMap(classmap.classifications))
        case MapAlgebraAST.Masking(_, _, _, mask) =>
          Masking(GeomLit(mask.toGeoJson) +: args)
        case MapAlgebraAST.Equality(_, _, _)       => Equal(args)
        case MapAlgebraAST.Inequality(_, _, _)     => Unequal(args)
        case MapAlgebraAST.Greater(_, _, _)        => Greater(args)
        case MapAlgebraAST.GreaterOrEqual(_, _, _) => GreaterOrEqual(args)
        case MapAlgebraAST.Less(_, _, _)           => Lesser(args)
        case MapAlgebraAST.LessOrEqual(_, _, _)    => LesserOrEqual(args)
        case MapAlgebraAST.And(_, _, _)            => And(args)
        case MapAlgebraAST.Or(_, _, _)             => Or(args)
        case MapAlgebraAST.Xor(_, _, _)            => Xor(args)
        case MapAlgebraAST.Pow(_, _, _)            => Pow(args)
        case MapAlgebraAST.Atan2(_, _, _)          => Atan2(args)

        /* --- Unary Operations --- */
        case MapAlgebraAST.IsDefined(_, _, _)       => Defined(args)
        case MapAlgebraAST.IsUndefined(_, _, _)     => Undefined(args)
        case MapAlgebraAST.SquareRoot(_, _, _)      => SquareRoot(args)
        case MapAlgebraAST.Log(_, _, _)             => LogE(args)
        case MapAlgebraAST.Log10(_, _, _)           => Log10(args)
        case MapAlgebraAST.Round(_, _, _)           => Round(args)
        case MapAlgebraAST.Floor(_, _, _)           => Floor(args)
        case MapAlgebraAST.Ceil(_, _, _)            => Ceil(args)
        case MapAlgebraAST.NumericNegation(_, _, _) => NumericNegation(args)
        case MapAlgebraAST.LogicalNegation(_, _, _) => LogicalNegation(args)
        case MapAlgebraAST.Abs(_, _, _)             => Abs(args)
        case MapAlgebraAST.Sin(_, _, _)             => Sin(args)
        case MapAlgebraAST.Cos(_, _, _)             => Cos(args)
        case MapAlgebraAST.Tan(_, _, _)             => Tan(args)
        case MapAlgebraAST.Sinh(_, _, _)            => Sinh(args)
        case MapAlgebraAST.Cosh(_, _, _)            => Cosh(args)
        case MapAlgebraAST.Tanh(_, _, _)            => Tanh(args)
        case MapAlgebraAST.Asin(_, _, _)            => Asin(args)
        case MapAlgebraAST.Acos(_, _, _)            => Acos(args)
        case MapAlgebraAST.Atan(_, _, _)            => Atan(args)

        /* --- FOCAL OPERATIONS --- */
        case MapAlgebraAST.FocalMax(_, _, _, n) =>
          FocalMax(args, NeighborhoodConversion(n))
        case MapAlgebraAST.FocalMin(_, _, _, n) =>
          FocalMin(args, NeighborhoodConversion(n))
        case MapAlgebraAST.FocalMean(_, _, _, n) =>
          FocalMean(args, NeighborhoodConversion(n))
        case MapAlgebraAST.FocalMedian(_, _, _, n) =>
          FocalMedian(args, NeighborhoodConversion(n))
        case MapAlgebraAST.FocalMode(_, _, _, n) =>
          FocalMode(args, NeighborhoodConversion(n))
        case MapAlgebraAST.FocalSum(_, _, _, n) =>
          FocalSum(args, NeighborhoodConversion(n))
        case MapAlgebraAST.FocalStdDev(_, _, _, n) =>
          FocalStdDev(args, NeighborhoodConversion(n))
      }
    }

    eval(ast)
  }
}
