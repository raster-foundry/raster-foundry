# Context

# The Contenders

I considered the following tracing libraries, presented in the order in which I
tried them out. Each of them is seemingly maintained by one person, so there's
considerable risk that development will stop someday at random on each of them.

### `natchez`

Natchez promises nice integration with things that we already use from the
TypeLevel ecosystem, or at least it would, if I could figure out how to provide
a `ConcurrentEffect` for `Kleisli[F, Span[F], ?]`. The reasons that's hard are:

- I'm having trouble figuring out how to construct a `new ConcurrentEffect[F]`,
not just for this particular evidence, but in general. When I try to implement
a `runCancelable`, I get a warning about `runCancelable` being unused,
despite that appearing to be the method definition that [I'm supposed to care
about](https://github.com/typelevel/cats-effect/blob/master/core/shared/src/main/scala/cats/effect/ConcurrentEffect.scala#L42-L61).
- The most obvious workaround is suggested by
the `EitherT` concurrent (or `WriterT`, or take your
pick), but the trait I'd need (`KleisliConcurrent`) is [private to
`cats-effect`](https://github.com/typelevel/cats-effect/blob/master/core/shared/src/main/scala/cats/effect/Concurrent.scala#L749).
- Moreover, the [`natchez`
example](https://github.com/tpolecat/natchez/blob/master/modules/examples/src/main/scala/Example.scala)
has no idea about being a webservice -- it only knows how to ask a database for
some information. The extra typeclass requirements from `BlazeServerBuilder`
are a bit challenging to resolve.

Rob Norris currently recommends not to use `natchez` in
anything you need to work, and I think we should take his
[advice](https://github.com/tpolecat/natchez/blame/efcefbf28c88ab977206df0f394d4141b3c5b2ca/README.md#L60).

The (broken) implementation I wound up with is on
[`feature/js/natchez`](https://github.com/jisantuc/tracing-demos/tree/feature/js/natchez).

### `scala-opentracing`

`scala-opentracing` ties its language very closely to the [Open Tracing
specification](https://opentracing.io/specification/). It's a library used by
Colisweb in production according to its readme. In summary, wherever you want
to trace, you'll need a `TracingContextBuilder[F]` for your effect type. The
only off-the-shelf non-trivial one is for DataDog. I don't know if there's a
way to get a local copy of the DataDog service running (I don't think there
is), so I pretended to have a fancier implementation that was really just
wrapping a `new TracingContextBuilder[IO]` around `LoggingTracingContext[IO]`,
since the `LoggingTracingContext` doesn't provide the implicit that I need. It
was fairly easy to set up the dependency, with the exception of adding
a repository to my resolvers (so we'd need to add that to Nexus in a few
places as well). We could start with the `LoggingTracingContext` very easily.

The out-of-the-box logging tracer [doesn't print or store tags
anywhere](https://github.com/Colisweb/scala-opentracing/blob/e9563d6da8d921e1d4c25178ffb5131e047bd9b0/src/main/scala/com/colisweb/tracing/LoggingTracingContext.scala#L18),
which is unfortunate, but I don't think it would be hard
to roll our own, since it's an 86-line Scala file. If
we want integration with a non-DataDog sink, I _think_
it's the case that we can provide a `Tracer` from `io.opentracing` to the
[`OpenTracingContext`](https://github.com/Colisweb/scala-opentracing/blob/e9563d6da8d921e1d4c25178ffb5131e047bd9b0/src/main/scala/com/colisweb/tracing/OpenTracingContext.scala)
class and wrap it the same way I wrapped the `LoggingTracingContext`.

DEPENDING ON TIME I MAY COME BACK AND TRY THAT but also http4s-tracer might
Just Plain Rule :tm: so we'll see

The implementation I wound up with is on
[`feature/js/scala-opentracing`](https://github.com/jisantuc/tracing-demos/tree/feature/js/scala-opentracing).

### `http4s-tracer`

`http4s-tracer` is oriented around atagless final
implementation of tracing. It requires a tracing algebra to
be present in a lot of different places. In examples at least,
it requires you to include the tracing activity everywhere.  What
that means in practice is that you don't have as much tracing logic [_around_
things](https://github.com/jisantuc/tracing-demos/blob/feature/js/scala-opentracing/app-backend/api/src/main/scala/com/jisantuc/tracingdemos/InterpreterService.scala#L27-L29)
as you do [within
things](https://http4s-tracer.profunktor.dev/guide.html#traced-programs),
so your tracing has to be less of an afterthought. It's probable that that
encourages better design and thinking about monitoring and instrumentation
when the code is written. It makes showing a toy example with with only a
single place to do computation sort of challenging.

Something that appears to be missing is the notion of a "span", which was
central in `natchez` and `scala-opentracing` and also in the Open Tracing spec.
There also doesn't seem to be any notion of tagging the traces with key-value
pairs, which is also central in the Open Tracing spec. While it would probably
meet our current needs to have a log-only trace ID, we've already done a less
fancy version of that with the incoming trace IDs from AWS. Given that "we
will want to be able to dump traces", I think the absence of out-of-the-box
support is concerning, especially given the comparatively high degree of
difficulty for implementing new tracing behaviors.

I didn't implement tracing in the demos repository for `http4s-tracer`.

# Decision

# Consequences
