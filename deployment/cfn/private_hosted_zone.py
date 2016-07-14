from boto import route53 as r53

from majorkirby import CustomActionNode

import json


class PrivateHostedZone(CustomActionNode):
    """Represents a Route53 private hosted zone"""
    INPUTS = {
        'Region': ['global:Region'],
        'VpcId': ['global:VpcId', 'VPC:VpcId'],
        'PrivateHostedZoneName': ['global:PrivateHostedZoneName'],
        'StackType': ['global:StackType'],
    }

    DEFAULTS = {
        'Region': 'us-east-1',
        'PrivateHostedZoneName': 'rf.internal',
        'StackType': 'Staging',
    }

    ATTRIBUTES = {'StackType': 'StackType'}

    def action(self):
        region = self.get_input('Region')
        conn = r53.connect_to_region(region, profile_name=self.aws_profile)
        comment = json.dumps(self.get_raw_tags())

        hosted_zones = conn.get_all_hosted_zones()

        for hosted_zone in hosted_zones['ListHostedZonesResponse']['HostedZones']:  # NOQA
            if ('Comment' in hosted_zone['Config'] and
                    hosted_zone['Config']['Comment'] == comment):
                self.stack_outputs = {
                    'PrivateHostedZoneId': hosted_zone['Id'].split('/')[-1]
                }
                return

        hosted_zone = conn.create_hosted_zone(
            '{}.'.format(self.get_input('PrivateHostedZoneName')),
            comment=comment, private_zone=True, vpc_id=self.get_input('VpcId'),
            vpc_region=region
        )
        hosted_zone_id = hosted_zone['CreateHostedZoneResponse']['HostedZone']['Id']  # NOQA
        self.stack_outputs = {'PrivateHostedZoneId': hosted_zone_id.split('/')[-1]}  # NOQA
