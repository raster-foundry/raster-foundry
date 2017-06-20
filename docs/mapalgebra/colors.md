# Color-Related Metadata

Histograms are automatically generated when insufficient information has
been manually supplied (either by the `Tool` or by the `ToolRun`). We
try to be clever about generating these histograms but they are, at
best, approximations. In light of this, a few options are provided for
defining the coloring behavior of each node of a MapAlgebra
transformation tree.


### Color Map

The `ColorMap` option offers the most direct control over exactly how a
tile will be rendered. It consists of a `Map` from `Double`s to `Int`s -
`Double`'s provide enough precision to work with any other numerical
type you'll encounter when working with rasters while `Int`s correspond
directly to colors (each byte in a 32 bit integer corresponds to an
r/g/b/alpha 0-256 value).

Minimal `NodeMetadata` specifying a color map:
```json
{
  "label": null,
  "description": null,
  "histogram": null,
  "colorRamp": null,
  "classMap": {
    "classifications": {
      "0.6000000000000001": 9,
      "0.20000000000000018": 7,
      "0.8": 10,
      "0.0": 6,
      "-0.8": 2,
      "-1.0": 1,
      "-0.19999999999999996": 5,
      "1.0": 11,
      "-0.6": 3,
      "-0.3999999999999999": 4,
      "0.40000000000000013": 8
    }
  },
  "breaks": null
}
```


### Color Ramp

Providing a `ColorRamp` specifies the colors to use when interpolating
values complementary to those produced automatically by a histogram or
manually via classbreaks. Interpolation of values means that a color
ramp doesn't need to match the number of breaks: it is possible to
estimate colors which correspond to a greater or lesser number of
breaks.

Minimal `NodeMetadata` specifying a color ramp:
```json
{
  "label": null,
  "description": null,
  "histogram": null,
  "colorRamp": [-1298349057, 1334754303, -1886242561, -1048299521,
-1244220929, -747067649, -732545793, -104970241],
  "classMap": null,
  "breaks": null
}
```


### Color Breaks

This will probably be the most commonly used option for specifying
default rendering options due to the fact that certain indices (like
NDVI) have scales which are meaningful regardless of a raster layer's
actual values (in other words, we don't need to assume that the
distribution calculated by way of a generated histogram accurately
conveys the significance of a layer's values).


Minimal `NodeMetadata` specifying color breaks:
```json
{
  "label": null,
  "description": null,
  "histogram": null,
  "colorRamp": null,
  "classMap": null,
  "breaks": [-1.0, -0.8, -0.6000000000000001, -0.4000000000000001,
-0.20000000000000007, -5.551115123125783E-17, 0.19999999999999996,
0.39999999999999997, 0.6, 0.8, 1.0]
}
```



### Default and override metadata
Each node of an AST can specify default `NodeMetadata` (or not).
`ToolRun`s have the ability to override these defaults by providing
a mapping from the node ID to the preferred `NodeMetadata` values.

Here's an NDVI `Tool` definition with a sensible (if imprecise) set of
color breaks:
```json
{
  "apply": "/",
  "args": [{
    "apply": "-",
    "args": [{
      "id": "c583c503-336b-40ac-9405-e37988fbfaa0",
      "metadata": null
    }, {
      "id": "2ca64844-6783-42ae-b953-ebde1d2d0f1b",
      "metadata": null
    }],
    "id": "968ba2d0-4740-4179-83f9-d229c609a62d",
    "metadata": {
      "label": "c583c503-336b-40ac-9405-e37988fbfaa0",
      "description": null,
      "histogram": null,
      "colorRamp": null,
      "classMap": null,
      "breaks": null
    }
  }, {
    "apply": "+",
    "args": [{
      "id": "c583c503-336b-40ac-9405-e37988fbfaa0",
      "metadata": null
    }, {
      "id": "2ca64844-6783-42ae-b953-ebde1d2d0f1b",
      "metadata": null
    }],
    "id": "1ff78cb5-3aa0-4c39-b85d-f7139a138a1c",
    "metadata": {
      "label": "c583c503-336b-40ac-9405-e37988fbfaa0",
      "description": null,
      "histogram": null,
      "colorRamp": null,
      "classMap": null,
      "breaks": null
    }
  }],
  "id": "5817ce54-d701-493f-9420-426ec512f1a0",
  "metadata": {
    "label": "c583c503-336b-40ac-9405-e37988fbfaa0",
    "description": null,
    "histogram": null,
    "colorRamp": null,
    "classMap": null,
    "breaks": [-1.0, -0.8, -0.6000000000000001, -0.4000000000000001, -0.20000000000000007, -5.551115123125783E-17, 0.19999999999999996, 0.39999999999999997, 0.6, 0.8, 1.0]
  }
}
```

If we wanted to override it with a more precise set of breaks, this would work:
```json
{
  "sources": {
    "c583c503-336b-40ac-9405-e37988fbfaa0": {
      "id": "b501307c-8cae-45ec-8b49-9229481f0457",
      "band": 4,
      "type": "project"
    },
    "2ca64844-6783-42ae-b953-ebde1d2d0f1b": {
      "id": "c6dbcd52-852f-4fa0-9dc4-e4385b653680",
      "band": 5,
      "type": "project"
    }
  },
  "metadata": {
    "5817ce54-d701-493f-9420-426ec512f1a0": {
      "label": "c583c503-336b-40ac-9405-e37988fbfaa0",
      "description": null,
      "histogram": null,
      "colorRamp": null,
      "classMap": null,
      "breaks": [-1.0, -0.9, -0.8, -0.7000000000000001, -0.6000000000000001, -0.5000000000000001, -0.40000000000000013, -0.30000000000000016, -0.20000000000000015, -0.10000000000000014, -1.3877787807814457E-16, 0.09999999999999987, 0.19999999999999987, 0.2999999999999999, 0.3999999999999999, 0.4999999999999999, 0.5999999999999999, 0.6999999999999998, 0.7999999999999998, 0.8999999999999998, 0.9999999999999998]
    }
  }
}
```

