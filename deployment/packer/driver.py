"""Helper functions to handle AMI creation with packer"""

import boto
import os
import subprocess

import logging
import urllib2
import csv


LOGGER = logging.getLogger('rf')

UBUNTU_RELEASE_URL = 'http://cloud-images.ubuntu.com/query/trusty/server/released.current.txt'  # NOQA
UBUNTU_RELEASE_FIELD_NAMES = ['version', 'version_type', 'release_status',
                              'date', 'storage', 'arch', 'region', 'id',
                              'kernel', 'unknown_col', 'virtualization_type']


def get_recent_ubuntu_ami(region):
    """Gets AMI ID for current release in region"""
    response = urllib2.urlopen(UBUNTU_RELEASE_URL).readlines()
    reader = csv.DictReader(response, fieldnames=UBUNTU_RELEASE_FIELD_NAMES,
                            delimiter='\t')

    def ami_filter(ami):
        """Helper function to filter AMIs"""
        return (ami['region'] == region and
                ami['arch'] == 'amd64' and
                ami['storage'] == 'ebs-ssd' and
                ami['virtualization_type'] == 'hvm')

    return [row for row in reader if ami_filter(row)][0]['id']


def update_ansible_roles():
    """Function that executes ansible-galaxy to ensure all roles are
       up-to-date before Packer runs."""
    ansible_dir = os.path.join(
        os.path.dirname(os.path.dirname(os.path.realpath(__file__))),
        'ansible')
    ansible_roles_path = os.path.join(ansible_dir, 'roles')
    ansible_command = ['ansible-galaxy',
                       'install',
                       '-f',
                       '-r', 'roles.txt',
                       '-p', ansible_roles_path]
    subprocess.check_call(ansible_command, cwd=ansible_dir)


def get_git_sha():
    """Function that executes Git to determine the current SHA"""
    git_command = ['git',
                   'describe',
                   '--tags',
                   '--always',
                   '--dirty',
                   '--abbrev=40']

    return subprocess.check_output(git_command).rstrip()


def get_git_branch():
    """Function that executes Git to determine the current branch"""
    git_command = ['git',
                   'rev-parse',
                   '--abbrev-ref',
                   'HEAD']

    return subprocess.check_output(git_command).rstrip()


def run_packer(rf_config, machine_types, aws_profile):
    """Function to run packer

    Args:
      rf_config (dict): Dict of configuration settings
      machine_types (list): list of machine types to build
      aws_profile (str): aws profile name to use for authentication
    """

    region = rf_config['Region']
    stack_type = rf_config['StackType']

    # Get AWS credentials based on profile
    aws_dir = os.path.expanduser('~/.aws')
    boto_config_path = os.path.join(aws_dir, 'config')
    aws_creds_path = os.path.join(aws_dir, 'credentials')
    boto.config.read([boto_config_path, aws_creds_path])
    aws_access_key_id = boto.config.get(aws_profile,
                                        'aws_access_key_id')
    aws_secret_access_key = boto.config.get(aws_profile,
                                            'aws_secret_access_key')

    # Get most recent Ubuntu release AMI
    aws_ubuntu_ami = get_recent_ubuntu_ami(region)

    update_ansible_roles()

    env = os.environ.copy()
    env['AWS_ACCESS_KEY'] = aws_access_key_id
    env['AWS_SECRET_ACCESS_KEY'] = aws_secret_access_key

    packer_template_path = os.path.join(
        os.path.dirname(os.path.realpath(__file__)),
        'template.js')

    LOGGER.info('Creating %s AMI in %s region', machine_types, region)

    packer_command = ['packer', 'build',
                      '-var', 'version={}'.format(env.get('GIT_COMMIT',
                                                          get_git_sha())),
                      '-var', 'branch={}'.format(env.get('GIT_BRANCH',
                                                         get_git_branch())),
                      '-var', 'aws_region={}'.format(region),
                      '-var', 'aws_ubuntu_ami={}'.format(aws_ubuntu_ami),
                      '-var', 'stack_type={}'.format(stack_type)]

    if machine_types is not None:
        packer_command.extend(['-only', ','.join(machine_types)])

    packer_command.append(packer_template_path)

    LOGGER.debug('Running Packer Command: %s', ' '.join(packer_command))

    subprocess.check_call(packer_command, env=env)
