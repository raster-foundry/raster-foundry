"""Finds scenes on a given day to extract"""

from csv import DictReader
from datetime import date, timedelta
import logging
import urllib2


from .io import get_records_from_csv
from .settings import usgs_landsat_url


def find_landsat8_scenes(year, month, day):
    """Finds landsat8 scenes as csv rows for year, month, day.
    For example, find_landsat8_scenes(2016, 03, 23) finds all Landsat 8
    scenes from March 23, 2016 up to but not including March 24, 2016.

    Args:
        year (int): year to search
        month (int): month to search
        day (int): day to search
    """
    logging.info('Searching for new scenes on %s-%s-%s', year, month, day)

    dr = DictReader(urllib2.urlopen(usgs_landsat_url))
    start_date = date(year, month, day).strftime('%Y-%m-%d')
    end_date = (date(year, month, day) + timedelta(days=1)).strftime('%Y-%m-%d')

    records = get_records_from_csv(
        dr, start_date, end_date, 'acquisitionDate'
    )

    logging.info('Found %s csv rows to create scenes from', len(records))
    return records
