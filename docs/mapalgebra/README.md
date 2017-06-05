# Model Lab Map Algebra

## The Model Lab Tool

Raster Foundry represents MapAlgebra transformations as a tree
(sometimes referred to as a `Tool` or a `Tool` definition) in which
operations are the intermediate nodes and raster sources are end nodes.
These trees only abstractly specify a set of operations. In addition,
evaluation of said trees requires specification of how raster sources
(the end nodes of our tree) are satisfied.

Here's an example of what such a tree looks like upon serialization.
Note that this tree represents NDVI:
```json
{
  "apply": "/",
  "args": [{
    "apply": "-",
    "args": [{
      "id": "0d93f106-d87a-44b2-a315-a751f6c97512",
      "metadata": null
    }, {
      "id": "f4f1ecbf-7977-49b4-ac06-4614a98d9823",
      "metadata": null
    }],
    "id": "e0f9eb65-f284-413b-af53-a6e7a236ef71",
    "metadata": {
      "label": "0d93f106-d87a-44b2-a315-a751f6c97512",
      "description": null,
      "histogram": null,
      "colorRamp": null,
      "classMap": null
    }
  }, {
    "apply": "+",
    "args": [{
      "id": "0d93f106-d87a-44b2-a315-a751f6c97512",
      "metadata": null
    }, {
      "id": "f4f1ecbf-7977-49b4-ac06-4614a98d9823",
      "metadata": null
    }],
    "id": "8a2d5e0a-6e76-41c1-8f36-5d66842182df",
    "metadata": {
      "label": "0d93f106-d87a-44b2-a315-a751f6c97512",
      "description": null,
      "histogram": null,
      "colorRamp": null,
      "classMap": null
    }
  }],
  "id": "dbbbe8a1-5e14-4361-bdab-9a526baac66c",
  "metadata": {
    "label": "0d93f106-d87a-44b2-a315-a751f6c97512",
    "description": null,
    "histogram": null,
    "colorRamp": null,
    "classMap": null
  }
}
```

There's a great deal of information here, but each node is actually
fairly simple and a minimal serialized representation is generated and
printed for reference in the tests for the `tool` subproject.


### Supported Operations

#### Local Operations
- Addition
- Subtraction
- Multiplication
- Division
- Classification
- Min
- Max


### Evaluation Errors

When there's a failure parsing or evaluating a `ToolRun` and its
associated `Tool`, a list of errors will be produced. Below is a list of
the currently supported errors and some brief exposition about their
meanings.

#### Missing Parameter
This error is encountered whenever a source-required parameter is not found
within the parameter map of a `ToolRun`.

#### Incorrect Argument Count
This error is encountered whenever an operation's arity requirements
(which may vary) are not met. Unary operations like `Classification`
will produce this error if provided more than one inbound node.

#### Unhandled Case
This is either an implementation error (perhaps your newly implemented
operation has not had behavior defined in the relevant `case` block) or
an error due to pattern-match failure.

#### NoBandGiven
This error is encountered when a `ToolRun`'s map provides a valid source but
fails to properly specify the band of that source to use.

#### Attribute Store Fetch Error
Encountered whenever IO fails to retrieve a scene's attribute store.

#### Raster Retrieval Error
Encountered whenever IO fails to retrieve a layer specified within a
`ToolRun`

#### Database Error
Encountered whenever an IO error related to the database which backs
`Tool`s and `ToolRun`s is encountered in the process of evaluating a
`ToolRun`.

#### AST Decode Error
Encountered whenever a `Tool`'s JSON is unable to be parsed to the
appropriate Map Algebra representing structure.


