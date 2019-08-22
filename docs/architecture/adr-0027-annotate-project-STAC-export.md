# 0027 Annotate Project STAC Export

## Context

This is an ADR documenting decisions made on MVP features of exporting Raster Foundry/Annotate label data and scenes in STAC-compliant catalogs. This aims to make us one step closer towards integrating STAC to the path of interoperability among Azavea’s Machine Learning workflow.

In Raster Foundry, we support creating geospatial labels based on images and scenes added to project layers. This feature is further extended in Annotate App, which is an application backed by Raster Foundry APIs, so that teams can work on an image labeling projects simultaneously within status-tracked tasks. To enable downstream Machine Learning work better in interoperating these ground truth labels, we have decided to implement a feature for exporting images and labels in STAC catalogs in an asynchronous manner. The following sections will go into details about the export process, the structure of the exported catalog, and where they are stored.

## Decision

### CRUD endpoints

Similar to image and scene exports in Raster Foundry, we have created CRUD endpoints for scene and label exports in the form of STAC catalogs by `api/stac`. More details about the API are in the [spec](https://github.com/raster-foundry/raster-foundry/blob/1.27.0/docs/swagger/spec.yml#L4802).

1. Create

`POST` JSON in the following shape creates an export record and kicks off the catalog building batch process.

```json
{
     "name": "Annotate project export test",
     "layerDefinitions": [
          {
               "projectId": "<UUID of a project>",
               "layerId": "<UUID of a project layer>"
          }
     ],
     "taskStatuses": [“<task status>”]
}
```

One or multiple objects for `layerDefinitions` may be supplied so that the exported catalog contains images and labels from one or many project layers. The creation will succeed only if the operating user has `VIEW` access to the project, and the layer exists within the project.

`taskStatuses` should be one or multiple of `UNLABELED`, `LABELING_IN_PROGRESS`, `LABELED`, `VALIDATION_IN_PROGRESS`, and `VALIDATED`. These are enums marking statuses of tasks in the database according to Annotate frontend operations. Only labels spatially fall in tasks of these statuses are included in the exported catalog. The geometry of the exported STAC Label Collection and Item will exclude the unexported task areas.

2. Update

Update is only permitted for super users or export owners. Only `name`, `export_location`, and `export_status` are open for updates -- the latter two fields will be updated after the catalog is exported and stored on S3.

3. List, get, and delete

There is nothing too special about these three, except that only super users or export owners are able to have valid results from these actions.

### STAC catalog builder

We have created a STAC catalog export builder that will build the catalog in the following structure:

```
Exported Catalog:
     |-> Layer collection
     |   |-> Scene Collection
     |   |   |-> Scene Item
     |   |   |-> (One or more scene items)
     |   |-> Label Collection
     |   |   |-> Label Item (Only one)
     |   |   |-> Label Data in GeoJSON Feature Collection
     |-> (One or more Layer Collections)
```

One may think of an export as a snapshot of scenes and labels contained in the specified layers that fall into tasks marked with certain statuses at a certain time. So an exported Catalog may contain multiple Layer Collections with each layer being a project layer in Raster Foundry. A Layer Collection contains one Scene Collection and one Label Collection. A Scene collection has one or multiple Scene Items with assets pointing to `COG` resources on S3, and these resources are from the `ingest_location` field of scenes in Raster Foundry database. A Label collection contains one Label Item representing ground truth labels with an asset pointing to a GeoJSON Feature Collection of the label data and with links pointing to Scene items representing the labelled imagery.

The `id` of the export record is the `id` of the Catalog. A Layer Collection’s `id` maps to a layer in Raster Foundry database. Scene Items’ `id`s are also scene `id`s in Raster Foundry database. `id` field in each `Feature` of the `FeatureCollection` of the ground truth data are annotation IDs in Raster Foundry database. Other IDs are generated on the fly.

### Async batch job

We have created jobs in AWS batch to build these exports in an asynchronous manner. A STAC export job is kicked off when a user successfully performs a `POST` with the above mentioned JSON to the `api/stac` endpoint from Raster Foundry.

### Static catalogs on S3

The exported STAC catalogs live on environment specific Raster Foundry S3 buckets. The S3 locations are determined by the self links in all STAC resources from the export builder. Resources are linked to each other by absolute links currently, except that `root` links use relative links. In future work, we will update the links so that only `self` links are absolute, and the rest will use relative links.

## Consequences

The STAC endpoints, export builder, and the export batch job transform scenes and labels from layers in Raster Foundry database to STAC resources stored on S3.

## Future Work

In terms of areas of enhancement for future work and better interoperability, some of these may be considered: update absolute links to relative links wherever makes sense, reuse the email notification component and notify export creator about the resource on S3 when the export is ready, support providing masks on exports to create training, validation, test sets, etc.
