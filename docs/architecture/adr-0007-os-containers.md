Operating System and Containers
===============================
Context
-------
We have used Docker containers, in various ways, on previous projects and our experience has been mixed but overall positive. Containers help to speed up CI builds and deployments, at the cost of added complexity and usage frustrations, especially for non-Linux users (most notably designers). We've decided that the best way to use Docker containers for local development is inside of a Vagrant VM, which lessens some of their benefits for local development but also improves the experience for non-Linux users.

For the past three years or so, we have used Ubuntu as the basis for every new project which required an operating system.

There are other container specifications, such as Rocket, and obviously, other Linux distributions. However, we do not have major complaints about either Docker containers or Ubuntu.

Decision
--------
We will continue to use Docker containers to encapsulate running applications, and to use Ubuntu as our host operating system. We have developed quite a bit of experience with both Ubuntu and Docker, and we're generally satisfied with both of them, so there are not compelling reasons to make changes to such foundational components of our software stack at this point. In theory, the isolation introduced by containers should allow us to swap out either the host OS or the container technology without (much) fear of unforeseen consequences, so this decision can be revised later if necessary.

It is possible that some deployment decisions may necessitate or encourage the use of other Linux distributions, either for host machines or as base Docker images. For example, some Linux distributions have base images which are significantly smaller than Ubuntu's base image. Similarly, AWS provides Elastic-Container-Service-optimized AMIs that we may choose to use over Ubuntu base AMIs. We will make decisions on a case-by-case basis whether to use these other distributions but we anticipate continuing to use Ubuntu locally for development.

Consequences
------------
We will need to develop a Docker-centric system architecture and development environment, and determine how best to integrate Docker and Spark. We will need to continue to be vigilant about maintaining a positive development experience for non-Linux users.
