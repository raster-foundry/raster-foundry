"""Utilities for transforming public scenes into COGs"""

from rf.utils.io import s3_bucket_and_key_from_url

import boto3
import rasterio
from rio_cogeo.cogeo import cog_validate

import logging
from multiprocessing import cpu_count, Pool
import os
import subprocess

DATA_BUCKET = os.getenv("DATA_BUCKET")

s3client = boto3.client("s3")
logger = logging.getLogger(__name__)


def georeference_file(file_path):
    logger.info("Georeferencing %s", file_path)
    with rasterio.open(file_path) as ds:
        width = ds.width
        height = ds.height

    output_dir, source_filename = os.path.split(file_path)
    translated_tiff = os.path.join(
        output_dir, "{}-referenced.tif".format(source_filename.split(".")[0])
    )
    translate_command = [
        "gdal_translate",
        "-a_ullr",
        "0",
        str(height),
        str(width),
        "0",
        "-a_srs",
        "epsg:3857",
        file_path,
        translated_tiff,
    ]
    logger.debug("Running translate command: %s", translate_command)
    subprocess.check_call(translate_command)
    return translated_tiff


def convert_to_cog(tif_path, local_dir):
    is_valid_cog, _, _ = cog_validate(tif_path)
    if is_valid_cog is True:
        logger.info("Skipping conversion of %s to a cog", tif_path)
        return tif_path

    logger.info("Converting %s to a cog", tif_path)
    with rasterio.open(tif_path) as src:
        has_64_bit = rasterio.dtypes.float64 in src.dtypes
    out_path = os.path.join(local_dir, "cog.tif")
    cog_command = [
        "gdal_translate",
        tif_path,
        "-co",
        "TILING_SCHEME=GoogleMapsCompatible",
        "-co",
        "COMPRESS=DEFLATE",
        "-co",
        "BIGTIFF=IF_SAFER",
        *(["-co", "PREDICTOR=2"] if not has_64_bit else []),
        "-of",
        "COG",
        out_path,
    ]
    subprocess.check_call(cog_command)
    return out_path
