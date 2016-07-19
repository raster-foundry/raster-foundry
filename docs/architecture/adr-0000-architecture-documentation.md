# 0000 - Architecture Documentation

## Context
We need a way to document major architecture decisions; in the past we have used the [Architecture Decision Record (ADR) format](http://thinkrelevance.com/blog/2011/11/15/documenting-architecture-decisions). On past projects, we have found the ADR format to be a useful way to write and manage architecture decisions.

We have written ADRs using both reStructuredText and Markdown formats on past projects. Certain documentation generators, such as Sphinx, can only use one of RST / Markdown. It is currently unknown which documentation generators we are likely to use for this project. The team is somewhat more comfortable writing in Markdown than RST.

## Decision
We will continue to use the ADR format for writing architecture decisions for this project. We will use Markdown for formatting ADR documents.

## Consequences
Major architectural decisions will need to be documented; changes to architectural decisions made via ADR will need to be documented in a superseding ADR.

If we choose to use a documentation generator that does not support Markdown, we may need to convert existing ADRs to that tool's preferred format.
