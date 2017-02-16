import logging
from logging import StreamHandler

logging.getLogger(__name__).addHandler(StreamHandler())
