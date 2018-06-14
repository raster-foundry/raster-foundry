"""Helper functions and patterns for extracting metadata from MTL documents
"""


from datetime import datetime
import re


cloud_cover_re = re.compile(r'CLOUD_COVER = (?P<cloud_cover>\d{1,2}(\.\d+)?)')
acquisition_date_re = re.compile(r'DATE_ACQUIRED = (?P<acquisition_date>\d{4}-\d{2}-\d{2})')


def extract_metadata(inf_string):
    cloud_cover = cloud_cover_re.search(inf_string).group('cloud_cover')
    acquisition_date = acquisition_date_re.search(inf_string).group('acquisition_date')
    acquisition_date_dt = datetime.strptime(acquisition_date, '%Y-%m-%d')
    return {
        'cloud_cover': cloud_cover,
        'acquisition_date': acquisition_date_dt.isoformat() + 'Z',
        'raw': inf_string
    }
