# ADR 0004 - Static Asset Pipeline
## Context
In selecting a static asset pipeline, there are several factors that are important to us. 
We don’t want to lose features that speed up developer feedback, such as a dev server with linting, auto-gen, and browser reload.
 The solution needs to support tests run with a single command similar to what we’ve had in previous projects.
The solution needs to be fast. 
Some team members have expressed dissatisfaction with the stability of the NPM ecosystem, but pretty much everything relies on it to some extent, so the issue is probably best addressed elsewhere (possibly using a lazy NPM cache).

In the past, our team has used Grunt to manage and build static assets. Various team members / designers have expressed discontent with the idea of going forward using Grunt, primarily due to inherent factors such as long compile times and configuration complexity.   

Other teams in Azavea have had good experiences with Webpack. Webpack is not a complete replacement for Grunt, but of the difference in functionality, it is highly likely that NPM tasks will cover it if used in conjunction. Despite scattered complaints of complexity and poor documentation found while researching, I was able to set up pipeline using npm and webpack overnight without too many issues. Significant features which are built in include a dev server capable of supporting hot reloading, very fast feedback on changes, and support for all the tools we commonly use in our applications. In addition to quality of life features, Webpack also gives us a dependency graph which includes static assets and bundle splitting. Combined with NPM, it appears to be a good solution.

A final option that we have to consider is Gulp, which has been used on a couple of projects by the Urban Apps team. Where grunt uses configuration files, Gulp uses code. Gulp’s main advantage is speed: streaming operations mean that files never touch disk between chained tasks. Other than the method of configuration and speed of operating, the basic functionality of Gulp and Grunt seem to be fairly aligned.  

## Decision
Given the good experiences that others in the company have had with Webpack, we’ve decided to run the static asset pipeline using a combination of npm and webpack. Npm will be used to manage dependencies and initiating tasks, while webpack will be used to run the dev server and build static assets. 

## Consequences
Our team has no experience working with webpack, which means that there will be a learning curve. Additionally, any functionality that we need which isn’t offered by Webpack will be written using scripts through NPM or Bash, which has the potential to create more work depending on the functions required.
