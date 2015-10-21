from majorkirby import GlobalConfigNode

from vpc import VPC
from private_hosted_zone import PrivateHostedZone
from data_plane import DataPlane
from application import Application

import ConfigParser
import sys


def get_config(rf_config_path, profile):
    """Parses a configuration file

    Arguments
    :param rf_config_path: Path to the config file
    :param profile: Config profile to read
    """
    rf_config = ConfigParser.ConfigParser()
    rf_config.optionxform = str
    rf_config.read(rf_config_path)

    try:
        section = rf_config.items(profile)
    except ConfigParser.NoSectionError:
        sys.stderr.write('There is no section [{}] in the configuration '
                         'file\n'.format(profile))
        sys.stderr.write('you specified. Did you specify the correct file?')
        sys.exit(1)

    return {k: v.strip('"').strip("'") for k, v in section}


def build_graph(rf_config, aws_profile, **kwargs):
    """
    Builds graphs for all of the Raster Foundry stacks
    Args:
      rf_config (dict): dictionary representation of `default.yml`
      aws_profile (str): name of AWS profile to use for authentication
    """
    global_config = GlobalConfigNode(**rf_config)
    vpc = VPC(globalconfig=global_config, aws_profile=aws_profile)
    private_hosted_zone = PrivateHostedZone(globalconfig=global_config,
                                            VPC=vpc, aws_profile=aws_profile)
    data_plane = DataPlane(globalconfig=global_config, VPC=vpc,
                           PrivateHostedZone=private_hosted_zone,
                           aws_profile=aws_profile)
    application = Application(globalconfig=global_config, VPC=vpc,
                              DataPlane=data_plane, aws_profile=aws_profile)

    return application


def build_stacks(rf_config, aws_profile, **kwargs):
    """Trigger actual building of graphs"""
    application_graph = build_graph(rf_config, aws_profile, **kwargs)

    application_graph.go()
