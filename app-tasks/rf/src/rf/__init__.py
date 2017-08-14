__version__ = "0.1.0"

import logging
from logging import NullHandler

logging.getLogger(__name__).addHandler(NullHandler())
logging.getLogger('boto3').setLevel(logging.WARNING)
logging.getLogger('botocore').setLevel(logging.WARNING)
logging.getLogger('nose').setLevel(logging.WARNING)
