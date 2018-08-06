import os
import rasterio


def get_geotiff_metadata(tif_path):
    """Reads Metadata from GeoTIFF at tif_path

    Args:
        tif_path (str): Path to local GeoTIFF file from which metadata should be read

    Returns:
        dict of any metadata found in the GeoTIFF
    """
    with rasterio.open(tif_path) as src:
        return src.tags()


def get_geotiff_name(tif_path):
    """Reads Metadata from GeoTIFF at tif_path

    Args:
        tif_path (str): Path to local GeoTIFF file from which metadata should be read

    Returns:
        string of tiff name
    """
    with rasterio.open(tif_path) as src:
        return os.path.splitext(src.name)[0]  # src.name includes extension


def get_geotiff_resolution(tif_path):
    """Reads ground resolution from GeoTIFF at tif_path

    Args:
        tif_path (str): Path to local GeoTIFF file

    Returns:
        Resolution of the GeoTIFF as an (x, y) tuple.
    """
    with rasterio.open(tif_path) as src:
        return src.res


def get_geotiff_size_bytes(tif_path):
    """Reads file size in bytes from GeoTIFF at tif_path

    Args:
        tif_path (str): Path to local GeoTIFF file

    Returns:
        Size of the GeoTIFF file in bytes
    """
    return os.path.getsize(tif_path)


def s3_url(bucket, key):
    return 's3://{bucket}/{key}'.format(bucket=bucket, key=key)


def get_geotiff_dimensions(tif_path):
    """Reads image dimensions in pixels from GeoTIFF at tif_path

    Args:
        tif_path (str): Path to local GeoTIFF file

    Returns:
        Size of the GeoTIFF file in pixels (width, height)
    """
    with rasterio.open(tif_path) as src:
        return (src.width, src.height)
