#!/usr/bin/env python
"""Commands for setting up the Raster Foundry stack on AWS"""

import argparse
import os

from cfn.stacks import get_config
from packer.driver import run_packer


current_file_dir = os.path.dirname(os.path.realpath(__file__))


def create_ami(rf_config, aws_profile, machine_type, **kwargs):
    run_packer(rf_config, machine_type, aws_profile=aws_profile)


def main():
    """Parse args and run desired commands"""
    common_parser = argparse.ArgumentParser(add_help=False)
    common_parser.add_argument('--aws-profile', default='default',
                               help='AWS profile to use for launching stack '
                                    'and other resources')
    common_parser.add_argument('--rf-config-path',
                               default=os.path.join(current_file_dir,
                                                    'default.yml'),
                               help='Path to Raster Foundry stack config')
    common_parser.add_argument('--rf-profile', default='default',
                               help='Raster Foundry stack profile to use for '
                                    'launching stacks')

    parser = argparse.ArgumentParser()
    subparsers = parser.add_subparsers(title='Raster Foundry Stack '
                                             'Commands')

    rf_ami = subparsers.add_parser('create-ami', help='Create AMI for Raster '
                                                      'Foundry',
                                   parents=[common_parser])
    rf_ami.add_argument('--machine-type', type=str,
                        nargs=argparse.ONE_OR_MORE,
                        choices=['rf-app', 'rf-worker'],
                        default=None, help='Machine type to create AMI')
    rf_ami.set_defaults(func=create_ami)

    args = parser.parse_args()
    rf_config = get_config(args.rf_config_path, args.rf_profile)
    args.func(rf_config=rf_config, **vars(args))

if __name__ == '__main__':
    main()
