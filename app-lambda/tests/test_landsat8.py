import json

from rflambda.landsat8.new_landsat8_event import NewLandsat8Event
from rflambda.landsat8.create_scene import (
    Footprints, create_scene, images_from_event, metadata_from_json)
from shapely.geometry import shape
import uuid

sample_event = json.loads("""
    {
   "Records":[
      {
         "eventVersion":"2.0",
         "eventSource":"aws:s3",
         "awsRegion":"us-east-1",
         "eventTime":"1970-01-01T00:00:00.000Z",
         "eventName":"ObjectCreated:Put",
         "userIdentity":{
            "principalId":"AIDAJDPLRKLG7UEXAMPLE"
         },
         "requestParameters":{
            "sourceIPAddress":"127.0.0.1"
         },
         "responseElements":{
            "x-amz-request-id":"C3D13FE58DE4C810",
            "x-amz-id-2":"FMyUVURIY8/IgAtTv8xRjskZQpcIZ9KG4V5Wp6S7S/JRWeUWerMUE5JgHvANOjpD"
         },
         "s3":{
            "s3SchemaVersion":"1.0",
            "configurationId":"testConfigRule",
            "bucket":{
               "name":"landsat-pds",
               "ownerIdentity":{
                  "principalId":"A3NL1KOZZKExample"
               },
               "arn":"arn:aws:s3:::landsat-pds"
            },
            "object":{
               "key":"c1/L8/003/004/LC08_L1TP_003004_20180805_20180814_01_T1/index.html",
               "size":1024,
               "eTag":"d41d8cd98f00b204e9800998ecf8427e",
               "versionId":"096fKKXTRTtl3on89fVO.nfljtsv6qko",
               "sequencer":"0055AED6DCD90281E5"
            }
         }
      }
   ]
}
""")


def test_event_parse():
    """Test that we're translating between an event and s3 paths correctly"""
    parsed = NewLandsat8Event.parse(sample_event)
    assert parsed.prefix == 'c1/L8/003/004/LC08_L1TP_003004_20180805_20180814_01_T1'
    assert parsed.scene_name == 'LC08_L1TP_003004_20180805_20180814_01_T1'
    assert parsed.mtl_json == (
        'c1/L8/003/004/LC08_L1TP_003004_20180805_20180814_01_T1/'
        'LC08_L1TP_003004_20180805_20180814_01_T1_MTL.json')


def test_metadata_parse():
    """Test that we're getting the expected fields from the MTL json"""
    parsed = NewLandsat8Event.parse(sample_event)
    (filters, _) = metadata_from_json(parsed)
    assert filters.cloud_cover == 40.35
    assert filters.sun_azimuth == -164.18055709
    assert filters.sun_elevation == 28.3560937
    assert filters.acquisition_date == '2018-08-05T00:00:00Z'


def test_footprint_construction():
    """Test that data and tile footprints mean what they're supposed to mean and that
    the geometry transformation is occurring without errors
    """
    some_points = [(0, 0), (1, 0), (1, 1)]
    footprints = Footprints(some_points)
    assert footprints.data_polygon['type'] == 'MultiPolygon'
    assert footprints.tile_polygon['type'] == 'MultiPolygon'
    # area assertion confirms that the data footprint and tile footprint are being
    # projected correctly and that the triangle / enclosing square relationship is preserved
    assert (shape(footprints.data_polygon).area * 2 == shape(
        footprints.tile_polygon).area)


def test_images_fetch():
    """Test that image creation happens without errors and with expectations about
    band resolutions
    """
    scene_id = uuid.uuid4()
    ims = images_from_event(NewLandsat8Event.parse(sample_event), scene_id)

    assert len(ims) == 11
    assert [x['bands'][0]['name'] for x in ims
            if x['resolutionMeters'] == 15] == ['panchromatic - 8']


def test_scene_create():
    """Test that the scene creation process completes without errors"""
    event = NewLandsat8Event.parse(sample_event)
    scene = create_scene(event)

    assert len(scene['thumbnails']) == 2
