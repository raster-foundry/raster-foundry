# Raster Foundry Tools

This package contains classes needed to create composiable transformations on tiles, tools. The classes in 'op'
directory are in `geotrellis.raster.op` package as they are preview code and will migrate into GeoTrellis library as
their use solidifies.


## JSON Format

First we need to be able to specify the how to  construct arbitraty ops from JSON.
A JSON node represents a construction of an `Op`.
Until we have some way to parse numeric operations on primitives like `band1 + band2` we have to assume that we have access to a library of standard map algebra functions like: `+`, `-`, `/`, etc.
The rest of the tile operstions will be expressed as combinations of these primitives.

There are two forms which are allowed:

### Function Application

``` scala
{ "apply": "-", "args": ["red", "nir"] }
```

 - `apply`: name of the function being applied
 - `args`: Array of JSON object of function arguments
  - if list: positional arguments
  - if json: by name arguments
  - String arguments refer to layer variables
  - other arguments will be parsed individually by the function and can be something like bbox

### Function Definition

```json
{
    "definition": "ndvi",
    "params": ["red", "nir"],
    "result": {
        "apply": "/",
        "args": [
            { "apply": "-", "args": ["red", "nir"] },
            { "apply": "+", "args": ["red", "nir"] }
        ]
    }
}
```

 - `definition`: name of the function being defined for later re-use
 - `params`: delcariation list of variables that will be function parameters
 - `include`: JSON array of function definitions to be used in `result`
 - `result`: function application that will be the result of application

It is anticipated that root element of an OP POST will have to be a function definition.

### Examples


#### Binary


```json
{
    "definition": "ndvi",
    "params": ["red", "nir"],
    "result": {
        "apply" :"/",
        "args": [
            { "apply": "-", "args": ["red", "nir"] },
            { "apply": "+", "args": ["red", "nir"] }
        ]
    }
}
```

#### Unary

```json
{
    "apply": "mask",
    "args": ["red", [23, 44, 56, 65]]
}
```

#### Multiband Output

```json
{
    "definition": "ndvi",
    "params": ["red", "nir"],
    "result": [
        {
        "apply": "/",
        "args": [
            { "apply": "-", "args": ["red", "nir"] },
            { "apply": "+", "args": ["red", "nir"] }]
        },
        {
        "apply": "/",
        "args": [
            { "apply": "+", "args": ["red", "nir"] },
            { "apply": "-", "args": ["red", "nir"] }]
        }
   ]
}
```

- In normal function we may combine multiple bands to produces a single band output.
- In multiband operation we are producing multiband output by running multiple functions in parallel.
- Note: object is a single band => array is multiband, how do we turn object into array?

things to figure out:

- How do inputs tie with outputs when you re-use functions ?
- Do names matter? How doe named and position arguments co-exit

## Multiband Input

We are expecting the input tile to be a multiband tile, this implis that band index has some kind of predefined meaning.

We can introduce index notation for that parameters:

```json
{
    "definition": "multiband_ndvi",
    "params": ["LC8"],
    "result": {
        "apply": "/",
        "args": [
            { "apply": "-", "args": ["LC8[4]", "LC8[5]"] },
            { "apply": "+", "args": ["LC8[4]", "LC8[5]"] }
        ]
    }
}
```

One problem with above there is that its not very convenient to to repeat indicies.
We can introduce a function that wraps the variable assignment:
```json
{
    "definition": "LC8_ndvi",
    "params": ["LC8"],
    "result": {
        "apply": "ndvi",
        "args": ["LC8[4]", "LC8[5]"]
    }
}
```
How is this handled during parsing ?
If the function is referenced as above there are only two choices:
    - perform textural substituation on JSON
    - rebuild the tree with variable renames
    - lets assume its the second

### Function Reuse

We have the ability to define a functions so they can be reused.
What syntax can we use to perform such definitions.
Lets limit it and say it only makes sense to envoke this feature in a `definition` block.
Further, root of the ML Tool must be a `definition` block as it define params.
We will call this the `include` block. Possibly it will be able to refer to function
libraries by their URI as well as full definition in this example:

```json
{
    "definition": "LC8_ndvi",
    "params": ["LC8"],
    "include": [
        {
            "definition": "ndvi",
            "params": ["red", "nir"],
            "apply": "/",
            "args": [
                { "apply": "-", "args": ["red", "nir"] },
                { "apply": "+", "args": ["red", "nir"] }
            ]
        }
    ],
    "result": {
        "apply", "+",
        "args": [
            {
                "apply": "ndvi",
                "args": ["LC8[4]", "LC8[5]"]
            },
            {
                "apply": "ndvi",
                "args": ["LC8[5]", "LC8[6]"]
            }
        ]
    }
}
```

This means that nested `definition` blocks semantically create scopes.


# Thoughts

When parsing JSON can we lean on HList represetnation of the parameters?
Not sure how this is possible since there will be no input at compile time, so what are we checking?
Parse function can return `Op :: Any :: HNil` eh, not useful.

The only way in which this is useful is if the `HList` is used to assemble the parser implemintation.
But the parser itself must implement some fixed type interface.
