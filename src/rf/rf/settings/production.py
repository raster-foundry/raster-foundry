"""Production settings and globals."""

from base import *  # NOQA


# S3 UPLOAD CONFIGURATION
mac_metadata = instance_metadata['network']['interfaces']['macs']
vpc_id = mac_metadata.values()[0]['vpc-id']

# The VPC id should stay the same for all app servers in a particular
# environment and remain the same after a new deploy, but differ between
# environments.  This makes it a suitable S3 bucket name
AWS_BUCKET_NAME = 'raster-foundry-{}'.format(vpc_id)
AWS_AUTO_CREATE_BUCKET = True
AWS_PROFILE = None

# END S3 UPLOAD CONFIGURATION
