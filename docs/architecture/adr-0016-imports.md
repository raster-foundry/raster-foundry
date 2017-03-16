0016 - Initial Uploads/Imports Support
======

Context
------

The scene creation process consists of multiple steps for Raster Foundry. Work has already been completed to import scenes for Landsat 8 and Sentinel-2 data. Lessons learned from that process can be applied to how Raster Foundry approaches supporting user uploads. This ADR summarizes our high-level desires regarding initial user scene creation and gives an outline for accomplishing them. At a high level, the scene creation process for data needs to do three things:

 1. Put imagery data in a location accessible for Raster Foundry
 2. Create new Scenes with required metadata
 3. Determine level of ingest
   - Metadata only
   - RDD at (near-)native resolution
   - Pyramided RDD

Data can come from a number of sources, of which Raster Foundry should remain agnostic as much as that is feasible. The API and models for images already accomplishes this. The ultimate goal here would be to replicate something similar to the UI in Carto that allows the user to import imagery easily. The main issue with importing data imagery differs from something like the imports that Carto allows is that often raster data requires gathering some initial metadata. Additionally, the size of imports can vary significantly not only in terms of the number of files, but the size as well.

One use-case Raster Foundry will encounter is how to handle uploads from a local filesystem. Other applications developed by Azavea allowed users to upload data using a supplied signed URL, allowing a user to make a PUT request to the URL. That does not seem feasible in the case of imagery because often the user may not, and Raster Foundry definitely will not, know the amount of data to be uploaded, the file extension, or other necessary information to generate a signed URL.

Raster Foundry will initially have a limited capability to determine metadata for a scene. Some metadata will be required, other metadata can be omitted but will negatively impact the searchability of the scene. Scene creation needs to handle arbitrary amounts of metadata and the possibility this metadata is included in an external file.

Decision
------

Raster Foundry will support a two-step process for users creating scenes. Users will be required to upload their data to a folder in an S3 bucket and then submit a “scene” with metadata via the API that includes the level of ingest or import. Later edits to this workflow will allow users to forego adding imagery to a bucket we designate if they can provide a URI that Raster Foundry can access.

### Uploading Data
Prior to importing data into Raster Foundry a user should have orthorectified data in some form that they can upload to s3. At some point in the future we will need to provide options for orthorectification, but I think that can wait.

Uplaods are initiated via an initial post to `/api/uploads/`. This returns an upload object.

If this is a local fileystem upload (and only that upload) then a get at `/api/tokens/<ID>/credentials/` returns a JSON response that includes the follwing:
 - AWS API credentials with write access
 - Bucket to upload to
 - Prefix that can be used for uploads (should be the randomly generated ID of the upload so that uploaded files can be associated back to a user)
The benefits of this approach are the following:
 - users can use whatever tool they desire to upload something to S3, including Amazon provided SDKs with native support for multipart, streaming uploads
 - Raster Foundry can determine the amount of storage that a user has uploaded
 - Raster Foundry can provide a means for removing uploads (though this will not be implemented immediately)

An alternative used on other projects to have an endpoint that returns a signed URL does not seem feasible for the following reasons:
 - Users will have an indeterminate amount of files to upload for a single ingest
 - Filenames and types will not be known in advance, a signed PUT URL requires a filename
 - Limits the type of upload and tools that clients can use

### Creating Scenes

At this point a background task in Airflow polls for new uploads that need to be processed to create scenes and will initiate any further work required. For instance, if a newly uploaded file needs to have thumbnails and footprints generated, but will not be ingested into RDDs yet, tasks for generating thumbnails and footprints will begin for a given scene.

Consequences
------

The main consequences of this ADR is a set of work that can now be started to support initial uploads of user data (rather than on uploads initiated by Raster Foundry). However, a few issues are revealed that we will need to start thinking about in the long-term.

Design work needs to be done on the import process which emphasizes the following:
 - Import starts with uploading images
 - After uploading images, they are grouped together and metadata is manually uploaded
 - Users will be able to upload arbitrary files that contain metadata, these will need to be separately associated

The decision here is very AWS specific, but it should be emphasized that the image metadata and upload process is actually agnostic as it stands. For instance, Raster Foundry already indexes publicly available imagery for Landsat 8, Sentinel 2, and imagery available in the Open Imagery Network -- all without having to import images into a Raster Foundry bucket. This is because the images themselves can be hosted anywhere that Raster Foundry can access -- in this case all of the above are on S3, but they could just as easily be HTTP URLs or other accessible locations with permissions scoped to Raster Foundry (or Raster Foundry users). While support for other cloud providers is not planned in the short-term, nothing stated so far inhibits adding additional support as time goes on and user needs dictate that.

Clients will need to be able to list their uploads and delete them if they desire. It is unclear how this should affect Scenes and their metadata, if at all. However, one problem we want to avoid is that the files for imagery get deleted without removing imagery. This is something we need to think about, but since in the short-term the goal is to dogfood the APIs before committing to them we can hold off on this for a bit.

Additionally, we need to conduct research on orthorectification and a solution for how to support orthorectification prior to (or as an additional step of importing). It is unclear if the orthorectification techniques available that can do this automatically (for instance, using a DEM file) or if user input will always be required. In some cases then an upload may not directly precede scene creation since some separate orthorectification will be required.

A command line utility should be written that can upload a list of files from a directory and create a scene with them. This should build off the python work already done for importing imagery with Airflow tasks. As use cases become more evident we can work to accommodate them. In the short-term, jobs that we have already written for ingestion need to be updated to allow for these uploads.
