# ADR 0024 -- Metrics Scripting
## Context

We'd like to be able to script metrics reporting to third party consumers of 
Raster Foundry. Currently we manually assemble metrics when people ask for 
them. However, enough time has passed and we have consistent enough obligations 
that we know a few that would be scriptable. This ADR seeks to answer the 
question of how we ought to script production of a particular subset of 
metrics, specifically, tile server requests by user.

## Options

This ADR considers three possible solutions and weighs them on extensibility, 
queryability, platform dependence, and difficulty.

One option is to take our existing infrastructure for metrics collection, move 
it into a collection of AWS Lambda functions, and orchestrate those Lambda 
functions with a Step Function. We have a plan for how this would work already 
(see issues [#684](https://github.com/azavea/raster-foundry-platform/issues/684)
through [#688](https://github.com/azavea/raster-foundry-platform/issues/688)
for the beginnings of how this would work). 
Athena queries initiated via AWS Lambda would process the aggregate data and 
insert the aggregated metrics into the Raster Foundry database where 
information could be exposed to users or reports could be generated regularly 
with little intervention. This solution is heavily dependent on AWS, and would 
require any future hypothetical bare-metal Raster Foundry installations to come 
up with their own metrics aggregation strategy. However, because it’s 
dependent on AWS, it also doesn’t depend on any particular backend 
technology, which improves transferability between the tile server and API 
server. While individual components of this strategy aren't difficult, this 
strategy as a whole adds to infrastructure complexity by branching into a new 
AWS service for the application for the first time and by adding a collection 
of new Lambda functions.

A second option is to add a middleware that uses the database to store metrics 
we care about. In this strategy, we'd extract metadata from incoming requests 
and either update counts in the database or append rows to what would 
effectively become an audit trail of tile requests. Depending on how we create 
the database table, adding more metrics would require one of:

- appending rows with a new metric type with some sort of polymorphic "value" 
field in the database table
- adding new metrics tables for specific configurations
- adding nullable columns, then writing the relevant data from new incoming 
requests

Retrieving data means querying Postgres, and we're fairly good at that. This 
strategy is also to some extent platform dependent, in the sense that it 
depends on Raster Foundry being deployed alongside a Postgres database; 
however, that is not a new piece of vendor lock-in, since the whole API and 
tile server already depend heavily on the pre-configured transactor that 
depends on Postgres. It does require us either to invest in unifying our 
backend technologies or writing the same functionality once as an http4s 
middleware and once as an akka-http directive. This strategy is fairly 
straightforward from the tile server side but relies on us to do a good job 
designing our tables so we don't overwhelm the database when we retrieve 
information.

A third option is to define some sort of metrics sink typeclass and provide 
typeclass evidence for our database. This option is like the second option, 
except instead of implementing the counter middleware for our database, we 
would implement the counter middleware for any type that had evidence that it 
satisfy the typeclass requirement. Querying data in this option would depend on 
what sort of sink we chose. If we choose Postgres, it's the same as the second 
option. Adding new metrics would require forcing the new metric into the types 
required by the metrics sink typeclass or adding methods to the typeclass. This 
option also provides extensibility in its metrics destinations, since we could 
start with a Postgres metrics sink for example, and later add an S3 or GCS or 
Grafana or any other sort of metrics sink, provided we can construct the 
typeclass evidence required.

## Decision

We should start with the second option, being mindful of the potential later 
need to target configurable metrics sinks, but not designing for that 
particular problem from the outset. We’ll start with tile metrics and a 
database table that holds just enough to get us the metrics we want.

The reasons the second option is likely to be safer are that it avoids 
increasing infrastructure complexity and that the marginal cost of its lock-in 
is lower than the marginal cost of additional vendor lock-in. We bear the 
burden of someday migrating everything from http4s and Postgres if we need to, 
while additional AWS lock-in is a cost that either makes it more difficult for 
anyone to deploy a bare metal solution or makes the cost of paying us to do so 
more expensive.

The second option is also more extensible, since storing this information in 
the database allows us to expose it via an API and to use the same information 
for authorization that we use elsewhere.

The downside of choosing the second option is that it couples metrics updates 
to our release cycle, while we could have avoided that coupling with the AWS 
option by throwing every piece of information we have into a data lake. This 
cost is acceptable given the other benefits and is in line with a strategy of 
not collecting data for the sake of collecting data. 

## Consequences

We’ll need to create a middleware that takes a partial function for request 
matching and updates counts in the database based on extracted metadata from 
those requests. This should start out as just another middleware in the 
backsplash-server subproject but should be designed such that we can pull it 
out into a separate subproject once it starts to be configurable.

We’ll also need to create a database table to hold the metrics, and, although 
we could query the database directly, a DAO and endpoints that require platform 
admin privileges for using the API to view metrics on demand. These endpoints 
will allow us eventually to shift metrics from a push model (we send metrics 
when clients request them) to a pull model (clients fire up their dashboard, 
log in with Raster Foundry, and see the metrics they’re allowed to see).

Dependency on http4s for the middleware should be further impetus to switch the 
server library in the API server from akka-http to http4s. Doing so would allow 
us to dump metrics from other services using the same middleware.

