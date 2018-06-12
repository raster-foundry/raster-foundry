import glob
import os
import subprocess


import rasterio
import requests


from rf.uploads.geotiff.factories import GeoTiffS3SceneFactory
from rf.utils import io
from .parse_mtl import extract_metadata


class MultiSpectralScannerConfig(object):
    bands = ['1', '2', '3', '4']


class ThematicMapperConfig(object):
    bands = ['1', '2', '3', '4', '5', '6', '7']


class EnhancedThematicMapperConfig(object):
    bands = ['1', '2', '3', '4', '5', '6_VCID_1', '6_VCID_2', '7', '8']


class LandsatHistoricalSceneFactory(object):
    def __init__(self, upload):
        landsat_id = upload.source
        path_meta = io.base_metadata_for_landsat_id(landsat_id)
        self.upload = upload
        self.landsat_id = landsat_id
        self.gcs_prefix = io.gcs_path_for_landsat_id(landsat_id)
        self.mission_num = path_meta['landsat_number']
        self.sensor = path_meta['sensor_id']
        self.config = {
            'M': MultiSpectralScannerConfig,
            'T': ThematicMapperConfig,
            'E': EnhancedThematicMapperConfig
        }[self.sensor]
        self._metadata = None
        self.filenames = {
            'COG': '/tmp/{}.COG.tif'.format(self.landsat_id),
            'STACKED': '/tmp/{}.STACKED.tif'.format(self.landsat_id)
        }

    def get_geotiff_factory(self):
        for band in self.config.bands:
            self.fetch_band(band)
        self.convert_to_cog()
        s3_location = self.upload_file()
        self.upload.files = ['s3://{}'.format(s3_location)]
        GeoTiffS3SceneFactory(self.upload)

    @property
    def metadata(self):
        if self._metadata is None:
            self._metadata = extract_metadata(
                requests.get(io.make_path_for_mtl(self.gcs_prefix, self.landsat_id)).content
            )
            return self._metadata
        else:
            return self._metadata

    def fetch_band(self, band):
        with open('/tmp/{}'.format(io.make_fname_for_band(band, self.landsat_id)), 'w') as outf:
            outf.write(
                requests.get(
                    io.make_path_for_band(self.gcs_prefix, band, self.landsat_id)
                ).content
            )

    def convert_to_cog(self):
        # It's possible I guess that somehow a container would get reused and have leftover
        # tifs in /tmp/ ? I don't know. Seems like a reasonable and cheap precaution.
        own_tifs = glob.glob('/tmp/{}*.TIF'.format(self.landsat_id))
        with rasterio.open(own_tifs[0]) as src0:
            meta = src0.meta
            meta.update(count=len(own_tifs))
        with rasterio.open(self.filenames['STACKED'], 'w', **meta) as dst:
            for i, band in enumerate(self.config.bands):
                fname = '/tmp/{}_B{}.TIF'.format(self.landsat_id, band)
                with rasterio.open(fname) as src:
                    dst.write_band(i + 1, src.read(1))

        translate_cmd = [
            'gdal_translate', self.filenames['STACKED'], '/tmp/translated.tif',
            '-co', 'TILED=YES', '-co', 'COMPRESS=DEFLATE',
            '-co', 'PREDICTOR=2'
        ]
        overviews_cmd = [
            'gdaladdo', '-r', 'average', '/tmp/translated.tif', '2', '4', '8', '16', '32'
        ]
        cog_cmd = [
            'gdal_translate', '/tmp/translated.tif', self.filenames['STACKED'],
            '-co', 'TILED=YES', '-co', 'COMPRESS=DEFLATE',
            '-co', 'COPY_SRC_OVERVIEWS=YES'
        ]

        subprocess.check_call(translate_cmd)
        subprocess.check_call(overviews_cmd)
        subprocess.check_call(cog_cmd)

    def upload_file(self):
        key = 'user-uploads/{}/{}'.format(
            self.upload.owner, self.filenames['COG']
        )
        io.s3.put_object(
            Bucket=os.getenv('DATA_BUCKET', 'rasterfoundry-development-data-us-east-1'),
            Key=key,
            Body=open(self.filenames['COG'], 'r')
        )
        return key


class Dummy(object):
    source = 'LE70010562000338CUB00'


exampleFactory = LandsatHistoricalSceneFactory(Dummy())
