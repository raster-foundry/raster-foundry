import os

from botoflow.decorators import activities, activity

from ..utils.logger import get_logger

logger = get_logger()


@activities(schedule_to_start_timeout=60, start_to_close_timeout=60)
class LayerImageUploadActivities(object):
    """Activities that handle various aspects of a layer upload"""

    @activity(os.environ.get('SWF_VERSION', '1.0'))
    def get_image_tags(self, layer_image_path):
        """Get image tags

        Args:
            layer_image_path (str): path to image to process
        """
        logger.info('Getting Image Tags %s', layer_image_path)

    @activity(os.environ.get('SWF_VERSION', '1.0'))
    def get_image_info(self, layer_image_path):
        """Extract additional information from layer

        Args:
            layer_image_path (str): path to image to process
        """
        logger.info('Getting Image Info %s', layer_image_path)

    @activity(os.environ.get('SWF_VERSION', '1.0'))
    def generate_thumbnail(self, image_path):
        """Generate thumbnails for layer image

        Args:
            layer_image_path (str): path to image to process
        """
        logger.info('Generating Thumbnail %s', image_path)

