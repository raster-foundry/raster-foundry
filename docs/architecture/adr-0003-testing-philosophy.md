0003 - Testing Philosophy
=========================

Context
-------

Different projects have followed different testing approaches on a
continuum from "test all the things!" to "only test some of the things".
"Test all the things!" is higher cost and higher maintenance if expected
feature behavior changes, while "only test some of the things" risks
missing some tests that should exist. Picking a point on that continuum
that gets the best of both worlds without the worst of either is hard.

Decision
--------

Testing rules should vary based on what is being tested. Our testing
philosophy should emphasize meaningful, readable tests. To the extent
possible, some sort of narrative testing framework
([ScalaTest](http://www.scalatest.org/),
[Jasmine](http://jasmine.github.io/2.4/introduction.html), etc.) should
be used to make explanation of features easily communicable to
non-development staff. "Only test some of the things" can work as long
as there are rules for when tests are necessary.

#### New features

These should almost always include automated tests demonstrating the
feature's behavior. The exception is for "small" features. Features
should be labeled "small" when their cards are created. If a feature
isn't labeled "small," it needs a test.

Frontend testing should test behavior rather than presence of elements
on a page except when element presence testing is the only way to test
behavior.

#### Bug fixes

These should always include automated tests to protect against
regression. Commit messages *and* docstrings for the test must include
issue numbers to make discussion around the original bug more accessible
if the test regresses in the future.

#### Refactors

These shouldn’t need new tests unless the refactor is fixing a buggy
behavior, in which case it’s more properly a bug fix and requires a test
accordingly.

#### Non-application changes

This category includes changes to AWS configurations, documentation,
corrections to misspelled words, etc. These changes do not require
tests. For changes most like the former, review should ensure
appropriate behavior.

#### Retirement of worthless tests

Tests that stop providing information should be retired. This category
refers primarily to tests that don't address bug fixes. It may be
important initially to include a test that demonstrates that a feature
functions as intended; however, over time, this test becomes less
important, and it's likely that the test has never failed outside the
development environment in which the feature was developed. We will
develop standards and seek out tools to enforce worthless test
retirement.

Consequences
------------

The team will need to:

1.  carefully maintain links between cards and issues

2.  make testing requirements explicit up-front, when cards are created

3.  enforce new testing philosophy through code review and available
    tools

4.  find tools for worthless test retirement where tools don't currently
    exist


