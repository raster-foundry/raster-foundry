# ADR 0026 - Post-hoc Metrics Scripting

## Context

[ADR 24](./adr-0024-metrics-scripting.md) answered the question of how to
track metrics for events in Raster Foundry servers. It doesn't answer how to
track events for requests elsewhere, for example, total logins by user, or non-events,
like storage used for uploads. Since
we'd also like metrics from third party services and ongoing usage data in the database
for querying and eventual visualization, we need to determine a strategy for either
pushing or regularly importing those events as well.

## Options

### Events

#### Option 1 -- webhooks

In this option, basically there's no such thing as post-hoc metrics, since
we use a hook to send an event to our API whenever something occurs that we're
interested in. This strategy seemed like it might be possible for Auth0 logins,
which are where I focused experimentation.

We can execute almost arbitrary javascript after credential exchange via the
"Client Credential Exchange" hook.  This means we could fire off a request to
increment the login count for a user as part of the login hook. See the 
`make-bogus-request` hook in the staging tenant for an example of what this looks
like, and [Papertrail logs](https://papertrailapp.com/groups/4082183/events?q=minCloudCover%3D9999)
for evidence that it works.

This strategy would require:

- an API endpoint to POST metrics with superuser auth only
- a hook in Auth0 that fired off a POST request to this endpoint after the
  client credential exchange
- investigation into similar integration opportunities with other third party
  services, if we ever want to track metrics from them

What would be nice about this strategy is that it would prevent us from having to
do anything in S3/Athena/Lambda/Step Functions at all. No vendor lock-in would be
nice from the perspective of someday supporting fully featured local deployments.
However, I can't figure out how to get the user info out of Auth0 after the credential 
exchange workflow and there doesn't seem to be another hook to latch onto. The two problems
of not knowing whether this is viable for future metrics we'd need to track and
not being certain that this strategy is possible make it not worth additional investment.

Additionally, this doesn't help us with the problem of things that aren't metrics. It
shrinks the space of metrics we'll need special handling for (or would if it were
possible), but doesn't help us with the special handling 

#### Option 2 -- Scheduled metric event aggregation with Lambda and Step Functions

The same things are good and bad about this option as in the first option in
[ADR 24](./https://github.com/raster-foundry/raster-foundry/blob/develop/docs/architecture/adr-0024-metrics-scripting.md#options).
However, since that ADR, two things have changed that will make this strategy easier to
accomplish:

- We have a Lambda Scala layer that allows us to ship a smaller jar and include more Raster
Foundry dependencies like the datamodel.
- The datamodel now includes `MetricEvent`s, which constrain the shape of what can be sent
to the database or API as metrics.

Choosing this strategy will require:

- an API endpoint to POST metrics with superuser auth only (unless we can ship the Dao with
  Lambda functions)
- A Lambda function to kick off an Athena query for each metric we want to calculate
- A Lambda function to check whether an Athena query (by ID) is complete
- A Step Function to organize the function executions
- A Lambda function to parse Athena query results into Metric events and ship them to the
  API

### Non-events

For things that aren't events, we have a choice between extending the database schema
to support querying values we care about (e.g., add a "file size" attribute to `Upload`s)
and calculating usage from objects in S3 (e.g., check the combined sizes of a bunch of
keys, based on some database query).

#### Option 1: Extending the database

Extensions we know would be necessary are adding `ingest_size_bytes` back to the scenes
table and adding `file_size` to `uploads`.  Adding these columns would be just a migration.
However, we'd need a one-off asynchronous process to backfill the file size information for
existing scenes and uploads. Additionally, since uploads and scenes can come from anywhere,
we'd need to add special handling to check and fill in upload file size and scene ingest size
only when the files / scenes are stored in Raster Foundry S3 buckets. Finally, for
consistency, we should also delete objects in S3 on scene and upload deletion, if we own the
bucket location, since otherwise our reported usage numbers will underestimate actual usage.
Finally, we should  update database records when we cleanup unused objects, since if we don't,
we'll overestimate actual usage.

Complexity in this example in general depends on trying to maintain consistent state between
the database and what lives in S3. This is going to be hard, and we're probably going to miss
sometimes. That's especially costly if we're measuring usage for billing purposes.

#### Option 2: Just get the information from s3

We have a few options here, but the general theme here is "S3 knows how big things are, so
let it tell us about it". We've been consistent enough in where ingests and uploads go that
it won't be difficult for us to tell where to look for things or who owns them. The workflow
in this case can either be reactive, with Lambda functions responding to S3 events based on
a bucket filter and updating database records accordingly, or periodic and the result of some scheduled roll-up operation.

In either case, these events need to go somewhere. Since in this case we're not extending the
database to include this information on the objects themselves, we should keep a separate table
of usage information. This table could look something like:

```sql
CREATE TYPE metered_object_type AS ENUM ('SCENE', 'UPLOAD');

CREATE TABLE usage_data (
  metered_object_type metered_object_type NOT NULL,
  object_id uuid NOT NULL,
  object_location uri NOT NULL,
  usage_amount bigint NOT NULL,
  created_at timestamp with time zone NOT NULL
);
```

If we want to track other types of usage than S3 object storage later, we can easily add
a `usage_type` enum with members like `STORAGE`, `COMPUTE_SECONDS`, etc.

# Decision

## For external events

Since the first option isn't possible and we don't have access to these events
until after they've occurred elsewhere, we should do the second option. Since the first option is maybe impossible currently and might not work for any metrics except logins, we should abandon it and make the most robust post-hoc aggregator we can manage.

## For non-events

We should just get the information from S3. The cost of being wrong in consistency is
over- or undercharging someone in the future, which is a really bad outcome regardless of which
direction it goes. The cost of keeping eventual metrics in mind in every interaction with external
services in the future is too high to make the ease of just adding two columns worth it.

# Consequences

## For external events

We'll need to create issues for each of the items that the Lambda / Step Function strategy will require.
We'll also need to make sure that we have some way to verify that we're not inserting the same metric
information twice. We should consider where and how to enforce this once the scaffolding is in place.

## For non-events

We'll need to create:

- a migration to add the `usage_data` table
- an asynchronous job to backfill usage data for uploads and ingested scenes stored in Raster
  Foundry buckets
- a lambda function to respond to S3 SNS events -- this Lambda function should increment
  and decrement the usage data table, depending on what the `eventName` is in the
  [SNS event](https://docs.aws.amazon.com/AmazonS3/latest/dev/notification-content-structure.html)
