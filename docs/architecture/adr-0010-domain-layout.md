# 0010 - Domain Layout

## Context

Raster Foundry's web presence needs a landing page for customer acquisition and a blog for curating content relevant to the project's objectives. Given that we are still early in the product development process, there is a desire to keep solutions to the landing page and blog simple, but also flexible so that they can easily be iterated on by all parties involved.

## Decision

Building upon the experience gained by maintaining products like Cicero, HunchLab, and OpenTreeMap, the following decisions have been made regarding Raster Foundry’s domain layout:

- Canonical domain is `www.rasterfoundry.com`
- Web application resides on `app.rasterfoundry.com`
- Blog resides on `blog.rasterfoundry.com`

As far as the platforms that drive the landing page and blog, the former will be a static website built using [Jekyll](https://jekyllrb.com/) and hosted via [GitHub Pages](https://pages.github.com/) (with support from Amazon CloudFront for HTTPS) and the latter will be based on [Medium](https://medium.com/).

## Consequences

Many successful products with single-page applications follow the `app.domain.com` naming convention to keep their landing pages and applications separate, including [Datadog](https://app.datadoghq.com/), [Librato](https://metrics.librato.com/), and [Fastly](https://manage.fastly.com/). Still, this could add additional overhead for individuals interacting with Raster Foundry. Users will have to bookmark, navigate to, or otherwise recall the application domain.

With regard to the landing page, building it with Jekyll increases the barrier to entry for non-technical users. Even though everyone currently involved with the Raster Foundry project is familiar with the Markdown markup language, Jekyll brings along additional concepts (front matter, layouts, variables, and collections) that need to be mastered in order to make meaningful changes.

Hosting of the Jekyll website is being deferred to GitHub Pages, which alleviates the burden of assembling a deployment pipeline, but relinquishes some control the build process to GitHub. Also, to support HTTPS we need to create an Amazon CloudFront distribution with GitHub Pages as its origin.

Lastly, the Medium content creation platform is very accommodating to content creators and consumers, but comes with several limitations. Layout and content organization is restricted, and third-party solutions for web analytics are not allowed. We are trading this off for access to the growing content discoverability engine.
