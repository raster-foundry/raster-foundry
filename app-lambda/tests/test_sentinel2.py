import uuid

from rflambda.sentinel2.create_scene import (create_scene, images_from_event,
                                             metadata_from_json)
from rflambda.sentinel2.new_sentinel2_event import NewSentinel2Event

sample_event = {
    "Records": [{
        "EventSource":
        "aws:sns",
        "EventVersion":
        "1.0",
        "EventSubscriptionArn":
        "arn:aws:sns:eu-west-1:214830741341:NewSentinel2Product:7f07ce2f-fe89-4ace-be5e-3cb6118a246d",  # noqa: E501
        "Sns": {
            "Type":
            "Notification",
            "MessageId":
            "2283514d-9c1a-5cf7-b387-05f5b4c9f03a",
            "TopicArn":
            "arn:aws:sns:eu-west-1:214830741341:NewSentinel2Product",
            "Subject":
            "S2A_MSIL1C_20190115T145721_N0207_R039_T19MBP_20190115T164135",
            "Message":
            "{\n  \"name\" : \"S2A_MSIL1C_20190115T145721_N0207_R039_T19MBP_20190115T164135\",\n  \"id\" : \"eec370a1-4da8-4ed7-9313-125098191c94\",\n  \"path\" : \"products/2019/1/15/S2A_MSIL1C_20190115T145721_N0207_R039_T19MBP_20190115T164135\",\n  \"timestamp\" : \"2019-01-15T14:57:21.024Z\",\n  \"datatakeIdentifier\" : \"GS2A_20190115T145721_018626_N02.07\",\n  \"sciHubIngestion\" : \"2019-01-15T18:12:52.990Z\",\n  \"s3Ingestion\" : \"2019-01-15T18:28:55.782Z\",\n  \"tiles\" : [ {\n    \"path\" : \"tiles/19/M/BP/2019/1/15/0\",\n    \"timestamp\" : \"2019-01-15T15:04:39.013Z\",\n    \"utmZone\" : 19,\n    \"latitudeBand\" : \"M\",\n    \"gridSquare\" : \"BP\",\n    \"datastrip\" : {\n      \"id\" : \"S2A_OPER_MSI_L1C_DS_SGS__20190115T164135_S20190115T145721_N02.07\",\n      \"path\" : \"products/2019/1/15/S2A_MSIL1C_20190115T145721_N0207_R039_T19MBP_20190115T164135/datastrip/0\"\n    }\n  } ],\n  \"datastrips\" : [ {\n    \"id\" : \"S2A_OPER_MSI_L1C_DS_SGS__20190115T164135_S20190115T145721_N02.07\",\n    \"path\" : \"products/2019/1/15/S2A_MSIL1C_20190115T145721_N0207_R039_T19MBP_20190115T164135/datastrip/0\"\n  } ]\n}",  # noqa: E501
            "Timestamp":
            "2019-01-15T18:29:39.373Z",
            "SignatureVersion":
            "1",
            "Signature":
            "LZgGjZWAiNeByMlGgR80Vu4jTLbGZpqgQ0ctddBP1RFGQdjR3zlaYq56hhCFC6l2wTi2JIpcqqnSDqbq4wRgwchteakQ7oFbhjInRByY+4geHWthk+tIfSqm36WsbF5S1OcsJ2FhzqAh2PfF334ybs966B3omZPVZnYRfPsE3Sxt03p5XQSWCORnxUhKw6oJQPH0RD6kj/kIvOfzps/hSoSVLuhv/OhjR1iEDDKC8rjOC7apVKOTgVCWLLSu0M8A0T0WSK57d4akaEntCxjqumCv8/N0scan4hVXMP1ZWaMR4IVyP3eZ0OkNx8v4NVosEIp+iab7nmugikPBsPPMvA==",  # noqa: E501
            "SigningCertUrl":
            "https://sns.eu-west-1.amazonaws.com/SimpleNotificationService-ac565b8b1a6c5d002d285f9598aa1d9b.pem",  # noqa: E501
            "UnsubscribeUrl":
            "https://sns.eu-west-1.amazonaws.com/?Action=Unsubscribe&SubscriptionArn=arn:aws:sns:eu-west-1:214830741341:NewSentinel2Product:7f07ce2f-fe89-4ace-be5e-3cb6118a246d",  # noqa: E501
            "MessageAttributes": {}
        }
    }]
}


def test_event_parse():
    """Test that we're translating between an event and s3 paths correctly"""
    parsed = NewSentinel2Event.parse(sample_event)
    assert parsed.prefix == 'tiles/19/M/BP/2019/1/15/0'
    assert parsed.scene_name == 'S2 S2A_MSIL1C_20190115T145721_N0207_R039_T19MBP_20190115T164135'
    assert parsed.tile_info_json == 's3://sentinel-s2-l1c/tiles/19/M/BP/2019/1/15/0/tileInfo.json'


def test_metadata_parse():
    """Test that we're getting the expected fields from tile info json"""
    parsed = NewSentinel2Event.parse(sample_event)
    (filters, _) = metadata_from_json(parsed)
    assert filters.cloud_cover == 99.97
    assert filters.sun_azimuth is None
    assert filters.sun_elevation is None
    assert filters.acquisition_date == '2019-01-15T15:04:39.013Z'


def test_images_fetch():
    """Test that image creation happens without errors"""
    event = NewSentinel2Event.parse(sample_event)
    scene_id = uuid.uuid4()
    ims = images_from_event(event, scene_id)

    assert len(ims) == 13


def test_scene_create():
    """Test that the scene creation process completes without errors
    """

    event = NewSentinel2Event.parse(sample_event)
    scene = create_scene(event)

    assert len(scene['thumbnails']) == 1
