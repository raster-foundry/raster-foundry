So You Wanna Extend the AST?
============================

MAML is being factored out into its own library, but in the meantime I hear
you saying you want to extend what's already established in the RF backend.
Great! Grab yourself a clipboard, 'cause there are a handfull of places
you'll need to account for things, or else you invite firey death upon
Raster Foundry and all the orphans who depend on it for survival.

Now then...

"I wanna add a new leaf type"
-----------------------------

You probably don't. Talk to someone from GeoTrellis to make sure that what
you're attempting isn't already possible with existing primitives.

"I wanna add a new `Operation`"
-------------------------------

I like the cut of your jib.

**Task 1:** Decide if your Operation is also a `Unary` op. Will it only ever
take one child argument? (All focal operations are, for example, unary)

*If no*, your new op can extend the `Operation` type in
[MapAlgebaAST.scala](https://github.com/azavea/raster-foundry/blob/develop/app-backend/tool/src/main/scala/ast/MapAlgebraAST.scala):

```scala
case class BestOp(args: List[MapAlgebraAST], id: UUID, metadata: Option[NodeMetadata]) extends Operation {
  val symbol = "bestop"
}
```

*If yes*, you must extend `UnaryOperation` or `FocalOperation` instead.

```scala
case class BestOp(args: List[MapAlgebraAST], id: UUID, metadata: Option[NodeMetadata]) extends UnaryOperation {
  val symbol = "bestop"
}
```

Either way, your compiler should now explode with pattern match exhaustion
warnings. This is good! When there aren't any more, you'll know you're done.

**Task 2:** JSON codecs

Visit
[MapAlgebraOperationCodecs.scala](https://github.com/azavea/raster-foundry/blob/develop/app-backend/tool/src/main/scala/ast/codec/MapAlgebraOperationCodecs.scala)
and follow the examples to allow your new Operation to be serializable.

**Task 3:** Account for overridable parameters

Does your new op have extra parameters, like `Classification` or `Masking`?

*If yes*, do you want those extra params to be overridable? *If yes*, visit
[EvalParams.scala](https://github.com/azavea/raster-foundry/blob/develop/app-backend/tool/src/main/scala/params/EvalParams.scala).
Here you will need to extend the `ParamOverride` type hierarchy, and then
account for your addition in the method
[`Interpreter.overrideParams`](https://github.com/azavea/raster-foundry/blob/develop/app-backend/tool/src/main/scala/eval/Interpreter.scala#L31).

**Task 4:** Extend the `LazyTile` hierarchy

Visit
[LazyTile.scala](https://github.com/azavea/raster-foundry/blob/develop/app-backend/tool/src/main/scala/eval/LazyTile.scala)
and add a new type to the `LazyTile` hierarchy that corresponds to your
operation, and an easy-to-call method for it like `max`, `min`, `classify`,
`mask`, etc., near the top of the `LazyTile` trait definition.

`LazyTile`s work by providing a `get` method which performs the given
Operation over a single pixel. `LazyTile` subtypes are composed together to
form a sort of conglomerate of delayed function applications. When
`evaluate` is finally ran, each layered function is called on each pixel to
get a direct `Tile => Tile`  transformation that skips all the intermediate
allocations that are normally present with chained GeoTrellis `Tile`
operations.

**Task 5:** Use your new `LazyTile` type in the TMS AST Interpreter

Visit the method
[Interpreter.interpretGlobal.eval](https://github.com/azavea/raster-foundry/blob/develop/app-backend/tool/src/main/scala/eval/Interpreter.scala#L138)
and add a new pattern match case for your `Operation`. Note that `eval`
returns a `LazyTile`, so following the pattern in the other cases, once you
call `eval` on your Operation's child nodes, you can call out to the
convenience method that you wrote for `LazyTile` in Task 4.

**Task 6:** Leverage GeoTrellis for your Operation in the Export AST Interpreter

Visit [this package
file](https://github.com/azavea/raster-foundry/blob/develop/app-backend/batch/src/main/scala/com/azavea/rf/batch/ast/package.scala)
and find the `eval` function. The logic here is more complex than what we
had in Task 5 in the TMS Interpreter, but this is to navigate issues brought
in by the `Constant` type. The comments in the code can explain more.

So, is your `Operation` a `UnaryOp`?

*If yes*, adding a case for it under the
section `/* --- UNARY OPERATIONS --- */` should be easy. It's a matter of
making sure to [call the right GeoTrellis
method](https://geotrellis.github.io/scaladocs/latest/#geotrellis.package).

*If no*, you will need to use the `reduce` function. This takes four
lambdas, one for each of the combinations of `Constant` or non-constant that
can appear when folding all of an Operation's child nodes together.

**Task 7:** Celebrate

Great, that should be it! When compiling, you shouldn't see any more
warnings about missing pattern matches. The backend is sound again, and your
new `Operation` should be usable with both the TMS server and Exports.

If you're feeling keen, you could also add some tests to
[InterpreterSpec.scala](https://github.com/azavea/raster-foundry/blob/develop/app-backend/tool/src/test/scala/eval/InterpreterSpec.scala).
