- Update batch container (`docker-compose build batch`), server assemblies
- Get auth token and export it: `export JWT_AUTH_TOKEN=<token>`
- Create an export:
```bash
echo '{"projectId":"94bb99c1-c8da-4321-a03b-fce884009a90","exportStatus":"NOTEXPORTED","exportType":"S3","visibility":"PRIVATE","exportOptions":{"resolution":11,"crop":false,"raw":false,"mask":{"type":"MultiPolygon","coordinates":[[[[-107.529736,36.226074],[-107.531608,36.187282],[-107.454849,36.16813],[-107.447985,36.212474],[-107.529736,36.226074]]]]},"bands":[0,1,2,3]},"projectLayerId":"32a99ebc-83f0-4178-8c50-c70e66e343ba"}' | http --auth-type=jwt :9091/api/exports
```
- Grab the ID and run an export from RF
```bash
./scripts/console batch "rf export <export-id>"
```
- Ingest Landsat 8 Scene
```bash
./scripts/console batch "rf ingest-scene b0557dc2-1766-43db-b4be-ddd914e6566b"
```
- Ingest Sentinel 2 Scene
```bash
./scripts/console batch "rf ingest-scene 4b80b91d-b5eb-4a56-b177-034d16acdfb3"
```
- Upload tif and verify that upload processing still works (`s3://rasterfoundry-development-data-us-east-1/phila-sample.tif`)
```bash
echo '{"files":["s3://rasterfoundry-development-data-us-east-1/phila-sample.tif"],"datasource":"b4ca6661-27fa-497e-a85a-2a5632d5fe7c","fileType":"GEOTIFF","uploadStatus":"UPLOADED","visibility":"PRIVATE","metadata":{"acquisitionDate":"2019-11-18T16:19:17.630Z","cloudCover":0},"uploadType":"LOCAL","projectId":"410d4975-0eed-40c5-aa5a-511277507191"}' | http --auth-type=jwt :9091/api/uploads
./scripts/console batch "rf process-upload <upload-id>"
```