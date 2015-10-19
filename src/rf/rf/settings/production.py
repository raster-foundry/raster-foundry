"""Production settings and globals."""

from base import *  # NOQA
from boto.utils import get_instance_metadata

instance_metadata = get_instance_metadata(timeout=5)

if not instance_metadata:
    raise ImproperlyConfigured('Unable to access the instance metadata')


# HOST CONFIGURATION
# See: https://docs.djangoproject.com/en/1.5/releases/1.5/#allowed-hosts-required-in-production  # NOQA
ALLOWED_HOSTS = [
    'rf.azavea.com',
    '.elb.amazonaws.com',
    'localhost'
]

# ELBs use the instance IP in the Host header and ALLOWED_HOSTS checks against
# the Host header.
ALLOWED_HOSTS.append(instance_metadata['local-ipv4'])
# END HOST CONFIGURATION
