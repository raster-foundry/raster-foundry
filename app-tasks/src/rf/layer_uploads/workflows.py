import os

from botoflow.workflow_definition import WorkflowDefinition
from botoflow.decorators import execute
from botoflow.core import async

from .activities import LayerImageUploadActivities
from ..utils.logger import get_logger

logger = get_logger()


class LayerImageUploadWorkflow(WorkflowDefinition):
    """Workflow that manages the upload of a layer image"""

    @execute(os.environ.get('SWF_VERSION', '1.0'), execution_start_to_close_timeout=60)
    def process_layer(self, image_path):
        """Entrypoint to layer image upload workflow"""

        logger.info('Starting Workflow to process %s', image_path)
        futures = []

        tags = self.get_image_tags(image_path)
        futures.append(tags)

        info = self.get_image_info(image_path)
        futures.append(info)

        thumbnails = self.get_image_thumbnail(image_path)
        futures.append(thumbnails)

        yield futures

    @async
    def get_image_tags(self, layer_image_path):
        """Get metadata tags from image

        Args:
            layer_image_path (str): remote path to image
        """
        tags = LayerImageUploadActivities.get_image_tags(layer_image_path)
        yield tags

    @async
    def get_image_info(self, layer_image_path):
        """Get image info

        Args:
            layer_image_path (str): remote path to image
        """
        info = LayerImageUploadActivities.get_image_info(layer_image_path)
        yield info

    @async
    def get_image_thumbnail(self, layer_image_path):
        """Create image thumbnail

        Args:
            layer_image_path (str): remote path to image
        """
        thumbnail = LayerImageUploadActivities.generate_thumbnail(layer_image_path)
        yield thumbnail
