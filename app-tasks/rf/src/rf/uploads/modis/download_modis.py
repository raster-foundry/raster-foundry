import os
import requests
import logging

from HTMLParser import HTMLParser

logger = logging.getLogger(__name__)

EARTHDATA_USER = os.getenv('RF_EARTHDATA_USER')
EARTHDATA_PASS = os.getenv('RF_EARTHDATA_PASS')


def get_stream(session, url, auth, previous_tries):
    """Traverse redirects to get the final url for MODIS

    Args:
        session (Session): requests session for making URL requests
        url (str): URL to make request for
        auth (tuple): user, pass for making requests
        previous_tries (int): number of previous tries requested
    """
    stream = session.get(url, auth=auth, allow_redirects=False, stream=True)
    # if we get back to the same url, it's time to download
    if url in previous_tries:
        return stream
    if stream.status_code == 302:
        previous_tries.append(url)
        link = LinkFinder()
        link.feed(stream.text)
        return get_stream(session, link.download_link, auth, previous_tries)
    else:
        raise RuntimeError("Earthdata Authentication Error")


class LinkFinder(HTMLParser):
    """Simple parser to find download link for MODIS data"""
    def __init__(self):
        self.reset()
        self.download_link = None

    def handle_starttag(self, tag, attrs):
        if attrs and attrs[0][0] == 'href':
            self.download_link = attrs[0][1]


def download_hdf(url, outdir):
    """Download HDF file and save it in outdir

    Args:
        url (str): where to request from
        outdir (str): directory to save downloaded file
    """
    download_path = os.path.join(outdir, os.path.basename(url))
    auth = (EARTHDATA_USER, EARTHDATA_PASS)
    session = requests.Session()
    stream = get_stream(session, url, auth, [])
    chunk_size = 1024
    try:
        with open(download_path, 'wb') as f:
            logger.info('Saving %s' % download_path)
            for chunk in stream.iter_content(chunk_size):
                f.write(chunk)
    except:
        raise RuntimeError("Problem fetching %s" % stream)

    return download_path
