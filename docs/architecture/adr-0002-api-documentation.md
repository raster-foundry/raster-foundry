# 0002 - API Design Framework/Documentation

## Context
Planning and documenting an API is critical to its success. Poorly planned and undocumented APIs are difficult for end-users to build against and make building a front-end application that uses the API more difficult as well. Choosing a good API modeling should not impose a heavy burden on development, but ideally provide a strong set of tools that can be used to produce documentation, testing, and producing scaffolding for software development kits (SDKs)

There are primarily two options for a framework that are worth evaluating:
 - Swagger
 - RESTful API Modeling Language (RAML)
 
## Decision
Raster Foundry will use Swagger to design and document its API instead of using RAML for a few reasons.
 - Azavea has used Swagger successfully on a few projects already and has experience using it
 - The community around Swagger is larger and rooted in open source (Swagger has been donated to the Open API Initiative) and is currently at version 2.0
 - The tooling around Swagger is better documented and supports a more diverse set of languages compared to RAML

## Consequences
As a consequence to choosing Swagger for documenting the API we will need to develop a Swagger specification for Raster Foundry. Ideally, we should design the API first and build it second to ensure that it is easy to use and intuitive. Additionally, we should deploy a version of the Swagger UI alongside the application to serve as documentation for end-users and to help with testing.
