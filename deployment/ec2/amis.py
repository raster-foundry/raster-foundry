"""Helper functions to handle EC2 related operations with Boto"""

import boto
import logging


LOGGER = logging.getLogger('rf')
LOGGER.addHandler(logging.StreamHandler())
LOGGER.setLevel(logging.INFO)

MACHINE_TYPE_MAPPING = {
    'rf-app': 'Application',
    'rf-worker': 'Worker',
}


def _prune_ami(ami):
    """Actually deregister AMI and its associated snapshot"""
    LOGGER.info('Identified that [%s] is eligible to be pruned..', ami.id)

    ami.deregister(delete_snapshot=True)

    LOGGER.info('Deregistered [%s] and snapshot [%s]..', ami.id,
                ami.block_device_mapping['/dev/sda1'].snapshot_id)


def prune(rf_config, machine_types, keep, aws_profile):
    """Filter owned AMIs by machine type, environment, and count

    Args:
      rf_config (dict): Dict of configuration settings
      machine_types (list): list of machine types to prune
      keep (int): number of images of this machine type to keep
      aws_profile (str): aws profile name to use for authentication
    """
    stack_type = rf_config['StackType']

    conn = boto.connect_ec2(profile_name=aws_profile)
    for machine_type in machine_types:
        images = conn.get_all_images(owners='self', filters={
            'tag:Service': MACHINE_TYPE_MAPPING[machine_type],
            'tag:Environment': stack_type
        })

        if len(images) > keep:
            map(_prune_ami,
                list(sorted(images, key=lambda i: i.creationDate))[0:len(images) - keep])  # NOQA
        else:
            LOGGER.info('No [%s] AMIs are eligible for pruning', machine_type)
