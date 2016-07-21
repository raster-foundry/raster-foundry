# Context

Raster Foundry needs to use a continuous integration server or service to build pull requests, run tests, and automate deployments. Because we expect to use a single repository that may have multiple applications we also need the CI server to support running multiple tests in parallel, sometimes based on different conditions, then executing a deployment. 

We have experience using the following CI servers:
 - Travis CI
 - Buildkite
 - Jenkins

Travis CI satisfies the minimum requirement of being able to run tests on branches. However, wiring up deployments and other complicated workflows would be difficult to do so given Travis' set-up.
 
Buildkite is nice, though not free. At $15 per user their billing context does not make sense for us despite its ease of use. Buildkite does support the concept of _pipelines_ which more closely aligns with how PR building and deployments would be handled. However, it does not support branching workflows that we expect to need at some point with Raster Foundry without writing glue code ourselves that would run during CI.
 
Azavea maintains its own internal Jenkins cluster in addition to having experience deploying ad hoc workers when needed. A recent Jenkins update has added support for pipelines which seems to satisfy our requirements in that area, with the added benefit that the Jenkins configuration itself can be checked into the repository. Jenkins is also open source and has a large set of plugins that we may find useful, including integration with git, GitHub, and slack.

# Decision

Raster Foundry will use Jenkins to build pull requests and automate deployments. Rather than use our existing internal cluster, our Jenkins build server for Raster Foundry will be deployed in Raster Foundry's AWS account. This will allow us to expose its build status publicly on GitHub or other external services for integration. Additionally, deploying it on an EC2 instance will let us create an AMI and use Ansible for provisioning to scale out or replace the Jenkins server. 

# Consequences
The primary consequence for this is that we will need to write roles and other code to provision the server. If possible, this code should be kept to a minimum since it is not a critical part of the infrastructure.
 
The largest risk is that maintenance costs become prohibitive for deploying the Jenkins server. We can do our best to keep this at a minimum by ensuring that we back up the instance by baking an AMI. Additionally, once we find an appropriate sized EC2 instance we can purchase a reserved instance to save additionally money. One other additional risk is that Jenkins does not end up satisfying Raster Foundry's needs for a build server. Though unlikely, if this does happen the effect will be mitigated since we check-in our test-running scripts which are usually just bash.

