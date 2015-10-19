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
