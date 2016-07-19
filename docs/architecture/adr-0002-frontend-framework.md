Frontend Frameworks
===================
Context
-------
### Background
Based on the advice of the Phase I team, we will be starting largely from scratch for the frontend code for this project. Many design elements are expected to be carried over from Phase I, but they will largely be rewritten to be more maintainable and extensible going forward. Therefore, we need to make a decision about which technologies to use for the frontend code.

The overwhelming majority of our team's frontend experience is with Angular 1.x. However, there are strong indications that we will need to migrate away from Angular 1.x eventually. Angular 2 is currently in RC, and represents essentially a complete rewrite of the framework. Once Angular 2 is released, we expect the Angular 1.x ecosystem to gradually decline in size and quality as libraries and developers shift to Angular 2. If we wrote this project's frontend in Angular 1.5 to start, we would almost certainly need to migrate away from Angular 1.5 at some point in the lifetime of the project, although whether that point would be in one year or five years is currently unclear. We expect that migration to be a complex and time-consuming task for anything other than a trivial application. This adds significant expected maintenance costs to an Angular-1-based approach, which has been our default for the past two years or so. In addition, our experience with Angular has revealed pain points that have led many team members to express a desire to try something new.

Given that migrating to a new framework seems inevitable within the lifespan of this project, it makes sense to consider alternatives to Angular 1.x now. There seem to be four possible paths which we could take:
- Write Angular 1.5, upgrade later (the default if we change nothing now)
- Switch to Angular 2 now for new development
- Switch to React
- Switch to Ember

*Implicit in this is a rejection of more "exotic" approaches such as Elm, ScalaJS, and Aurelia, which are immature, have small ecosystems, and/or add significant complexity to our development pipelines at a time when we already expect to be learning some kind of new framework.*

### Convergence
Despite the multiplicity of frontend web frameworks, the story over the last year or so among the big three web frameworks (Angular, React, and Ember) has been one of convergence. All three are moving toward use of a "next-generation" syntax plus transpilation, with Angular defaulting to TypeScript, Ember selecting ES2015, and a variety of approaches, including Clojurescript, used with React.
Similarly, Angular 2 will introduce a new Angular-CLI which mimics many of the scaffolding features available from Ember's Ember-CLI. Angular 2 will bring significant improvements in page update speed over Angular 1 by adopting an approach similar to React's diffing algorithm, and Ember recently rolled out a new update engine, called Glimmer, which takes a similar approach. Ember has also adopted a strong component-based convention and one-way data flow by default, mimicking React, which Angular 2 will also adopt.

Therefore, the differences between these frameworks are rapidly becoming less about the availability or lack of certain built-in features, and more about ecosystem quality and maturity, project goals and governance, and focus on a pleasant development experience.

### Assessment
#### 1. Angular 1.5 -> 2
This approach would have the lowest startup cost because we would be able to get started right away. In order to make the eventual transition to Angular 2 as easy as possible, we would have to adhere to the recommended Angular style guide during the course of Angular 1 development, although this largely matches our preferred Angular style already. The recommended way to migrate is then to introduce a 1-to-2 interoperability module into the project which upgrades or downgrades Angular 1 and 2 components so that they can talk to each other, and then to rewrite components piece by piece while using Angular 1 and 2 side-by-side.

The continuing support situation for Angular 1.5 after the release of Angular 2 is currently unclear. Google has provided assurances that they will not cease development for Angular 1.x until the "vast majority" of users have switched to Angular 2, but it's unclear when this will happen or what their definition of the "vast" majority is. We do not want to be left as members of the Angular 1.x-using minority when Google stops development for it.

#### 2. Angular 2 now
A way to avoid the problems with migrating to Angular 2 later in the life of the project is to begin using Angular 2 immediately, even in its RC state. This would gain us all the benefits of Angular 2 right from the start, and would allow us to learn the framework while the project is still small. It would also give us first-class usage of TypeScript, something which the other frameworks in this list lack.

The primary downside to Angular 2 is that it is still a young project; it can be expected to have bugs, usability and documentation issues, and a lack of libraries and community literature for the near future. There's also an unlikely but real possibility that the framework suffers from unknown design flaws that won't be exposed until it has received more real-world usage. Given Angular's popularity and backing by Google, we can assume that these issues will be ironed out eventually, but they will place a cost on development for at least the next year or two.

#### 3. React
The React ecosystem is mature, and other teams at Azavea use it. One key difference between React and Angular / Ember is that React itself is not a full-featured framework: it is a library for generating components that communicate. Other modules, such as Flux or Redux, are required (or at least almost universally used) to build up a full frontend architecture.

This modular architecture runs somewhat counter to this team's framework preferences. Historically, we have preferred full-featured, opinionated frameworks over assembling collections of components, because our experience is that reducing the number of components in our application stack usually improves reliability and eliminates development headaches.

That said, opinionated frameworks can sometimes get in the way when we try to do something unusual, but our experience has been that as long as this is a rare occurrence, it is a price worth paying.

Structural issues aside, React has a healthy ecosystem with numerous libraries and has been used by other teams at Azavea before, so we would be able to leverage some of that knowledge if we run into issues.

#### 4. Ember
Ember is a full-featured, opinionated MVC web framework that is quite mature in terms of features. Indeed, as mentioned above, Angular 2's Angular-CLI is heavily inspired by Ember's Ember-CLI. Similar to Angular 2, Ember has been working to incorporate more React-inspired features, including one-way dataflows by default and a faster differential page update engine. However, in contrast with Angular, Ember has already completed and rolled out these updates in Ember 2, which was released about a year ago. In addition, Ember did this while largely maintaining backwards compatibility, rather than rewriting the entire framework. The current release is 2.6.

The Ember-CLI offers impressive scaffolding capabilities -- it provides scaffolding for all common application components, including routes and tests, and the default project comes with a testing environment already set up and working. Ember-CLI also includes a package manager for adding community modules to one's project.

Ember uses ES2015, which is a big step up from ES5, but which lacks the type safety possible with TypeScript in Angular 2. There is a plugin for using TypeScript with Ember but this would likely be imperfect.

Ember is less well known than React and Angular, which results in a smaller ecosystem of literature and libraries. However, it is not a niche framework by any means. StackOverflow has about 19,000 questions tagged with "ember.js", as compared to 18,800 for "reactjs". Angular, however, has 185,600 (!) questions tagged  for "angularjs".

Decision
--------
This project carries with it a large number of complex technical issues and new technologies related to raster processing. At the initial stages of the project, we will be more productive if we do not have to learn a new frontend framework alongside development of a complex backend architecture. This weighs in favor of sticking with what we know to start out. However, the long-term support situation for Angular 1 is currently unclear; it seems to be at least partially tied to community usage of Angular 1, which is tough to predict. Additionally, Angular 2 and Ember (and potentially more exotic frameworks such as Elm) appear to offer significant benefits over Angular 1.x such as improved page rendering speed and better developer ergonomics.

Raster Foundry will initially use Angular 1.5, following the style recommended by the [upgrade guide](https://angular.io/docs/ts/latest/guide/upgrade.html). Additionally, we will invest in our long-term productivity and hedge against decay in the Angular 1.x ecosystem by devoting resources toward an incremental upgrade process starting around the beginning of the second quarter of development work (roughly, mid-November, 2016). This process is expected to initially consume no more than 10% of per-sprint development points. Although the exact first steps in an incremental upgrade process will need to be decided, some possibilities might include:
- Introduce a TypeScript or ES2015 transpilation layer to the existing project and begin rewriting existing code in one of these two languages.
- Identify existing functionality that is well compartmentalized, and rewrite it in another framework or frameworks to assess ergonomics and interoperability.
- Identify a planned component that would benefit from the strengths of a different framework and write it in that framework.

Consequences
------------
All developers will need to thoroughly read and familiarize themselves with the [Angular 2 Upgrade Guide](https://angular.io/docs/ts/latest/guide/upgrade.html). We will need to track development of the Angular 2 and 1 ecosystems. We will need to work to strike a balance between investment in new features and migrating away from Angular 1 to avoid accruing technical debt. We will likely need to develop temporary systems for doing development on Angular 1 alongside Angular 2 or another framework within the same frontend application.
