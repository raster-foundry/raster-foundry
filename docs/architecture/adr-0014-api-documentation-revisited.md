# API documentation ADR
## Context

The goal isn't to make this super complicated, but give us a basis for building out more 
full-fledged documentation as the project goes -- beyond just listing API endpoints 
(see Auth0's docs section as inspiration https://auth0.com/docs).
There are javascript libraries that can assist us with parsing the swagger spec,
so a good amount of the work will be purely presentational.

Of significant import is that the framework that we choose here gives us an opportunity 
to test out design practices and frameworks before we apply them to our primary applications. 
Choosing a framework here that we don't plan on using elsewhere would have significant opportunity costs.
We also need to consider that Raster Foundry is almost certainly going to be transitioned
over to Angular 2, and this is the only chance we will have to learn about Angular 2 before
we do so. Additionally, other projects at Azavea that the Raster Foundry team work on, such as Hunchlab, are considering moving from Angular 1.X to Angular 2.X

### Frameworks to choose from

#### Spectacle

##### Pros
* Very flexible styling
* Actively maintained, and looks good
* Delivered assets are a static site
* Low maintenance

##### Cons
* Only supports generating API documentation - our requirements are along the lines of a CMS-lite type system, where code and api documentation can live side by side on the same site
* Forking Spectacle to support additional types of documentation could be possible, but it's written using raw javascript and handlebar templates

#### Angular 1.X

##### Pros
* We know it already, so it would be quick to get up and running
* Probably "good enough" for all intents and purposes. This is the quickest and easiest solution

##### Cons
* We are planning on eventually moving away from it
* Angular 1.X libraries are kind of in limbo right now - We've found that quite 
a few frameworks are no longer being actively maintained, so we have to bring the 
code into our own repository for maintenance purposes.  New angular libraries will
also likely only be written for angular 2+, possibly limiting our options down the road to
projects which only update with bugfixes.
* Apps show greatly degraded performance as they grow more complex, and debugging to figure out what causes it is usually a nightmare
* Not auto-generated - this is a full fledged framework, so we will be maintaining a second frontend application

#### Angular 2

##### Pros
* Lets us experiment with Angular 2 before we move decide to move one of our larger projects over
* Aligns with team objectives
* Angular CLI seems pretty solid.  I haven't run into any of the usual webpack issues using it.
  There is now an RC.0, which means that no project code changes should be required in order to 
  upgrade to v1
* Html templates with javascript controllers
* Maintained by Google: The direction that a central team provides can be valuable

##### Cons
* We need to learn the framework while we develop
  * Ecosystem is still a bit young compared to Angular 1.X and Ember
  * Need to learn typescript (see next point)
  * Documentation has fallen behind a bit, similar to how angular 1.X documentation has been a 
   problem area for years. Additionally, it can be hard to find the correct information 
   from tutorials etc because search results are polluted with results for Angular 1.X 
   that will never be updated. Additionally, while the typescript documentation seems fine,
   the ES6 documentation is sorely lacking at this time.
  * Many core concepts have changed from Angular 1.X (services, scope, etc). 
* Maintained by Google: Its success leans heavily on Google's continued support, and Google has a history of discontinuing popular products (Reader, FeedBurner, Blogger, Picnik, Buzz, Wave, etc). It is open-source though, so I'd rate this as being roughly even
* Not auto-generated - this is a full fledged framework, so we will be maintaining a second frontend application


#### Ember

##### Pros
* Existing prototype
* An upgrade roadmap focused on maintainability going forward.
  LTS releases seem like they would be a boon, and there's excellent tooling and 
  documentation for upgrading applications
* Good CLI - Angular 2 based theirs off of Embers, but is still working out the kinks.
* Good ecosystem - well tested modules
* Handlebars-style html templating.

##### Cons
* Learning ember - one more framework to keep track of. A mitigating factor for this
  is that Alex has significant experience working with Ember and could help with onboarding.
  Probably a better situation than Angular 2.
* A classic open source project that is maintained by the community. 
* Not auto-generated - this is a full fledged framework, so we will be maintaining a second frontend application

## Proposed Solution
We will write the api documentation as a traditional SPA using Angular 2, using either
Webpack or Angular CLI as a build tool. This should allow us to accomplish our flexibility
goals while also allowing us to test Angular 2 before we transition the main Raster Foundry
app over to it.

## Consequences / Tradeoffs
The biggest cost of the Angular 2 approach is that we have to maintain a second application. 
None of the static site generator meet our needs, so we'd have to either maintain a fork
of a static generator, or maintain a standalone website. Given an option between the two, it
makes more sense to do the latter because maintaining two similar applications is less expensive.

Angular 2 solves a lot of issues that Angular 1.X has, and allows for higher performance and 
more maintainable code.  On the other hand, Angular 2 is a younger framework, so some 
libraries that we rely on in Angular 1.X may not exist, or exist in a less stable form.

Examples that come to mind are component frameworks - many of them exist for Angular 1.X, 
but the selection seems a bit fragmented or less fully featured for Angular 2. 
With that said, writing custom components isn't the end of the world when your application 
is structured around creating composable components.  We rarely need all of the features 
which component libraries offer, and frequently have to hack our way around their limitations.

One of our team goals is to test new frameworks on smaller projects before we commit to 
converting our larger ones. We've established that for Hunchlab and Raster Foundry, 
we will eventually want to move from Angular 1.X to Angular 2.X. This means that we will
incur a spin-up cost for this application that we wouldn't otherwise. Many of the angular 
design practices we've learned and used will carry forward because they were 
backported from angular 2 -> 1 over the last 6 months.
