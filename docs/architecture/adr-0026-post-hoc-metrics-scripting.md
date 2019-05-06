# ADR 0026 - Post-hoc Metrics Scripting

[ADR 24](./adr-0024-metrics-scripting.md) answered the question of how to
track metrics for events in Raster Foundry servers. It doesn't answer how to
track events for requests elsewhere, for example, total logins by user. Since
we'd also like metrics from third party services in the database for querying
and eventual visualization, we need to determine a strategy for either pushing
or regularly importing those events as well.

## Options

### Option 1 -- webhooks

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

### Option 2 -- Scheduled metric aggregation with Lambda and Step Functions

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

# Decision

Since the first option isn't possible and we don't have access to these events until after they've
occurred elsewhere, we should do the second option. Since the first option is maybe impossible currently
and might not work for any metrics except logins, we should abandon it and make the most robust
post-hoc aggregator we can manage.

# Consequences

We'll need to create issues for each of the items that the Lambda / Step Function strategy will require.
We'll also need to make sure that we have some way to verify that we're not inserting the same metric
information twice. We should consider where and how to enforce this once the scaffolding is in place.
