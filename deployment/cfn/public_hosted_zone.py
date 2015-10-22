from boto import route53 as r53

from majorkirby import CustomActionNode


class PublicHostedZone(CustomActionNode):
    """Represents a Route53 public hosted zone"""
    INPUTS = {
        'Region': ['global:Region'],
        'PublicHostedZoneName': ['global:PublicHostedZoneName'],
        'AppServerLoadBalancerEndpoint':
        ['global:AppServerLoadBalancerEndpoint',
         'Application:AppServerLoadBalancerEndpoint'],
        'AppServerLoadBalancerHostedZoneNameID':
        ['global:AppServerLoadBalancerHostedZoneNameID',
         'Application:AppServerLoadBalancerHostedZoneNameID'],
        'StackType': ['global:StackType'],
    }

    DEFAULTS = {
        'Region': 'us-east-1',
        'StackType': 'Staging',
    }

    ATTRIBUTES = {
        'StackType': 'StackType'
    }

    def action(self):
        region = self.get_input('Region')
        hosted_zone_name = self.get_input('PublicHostedZoneName')
        app_lb_endpoint = self.get_input('AppServerLoadBalancerEndpoint')
        app_lb_hosted_zone_id = self.get_input('AppServerLoadBalancerHostedZoneNameID')  # NOQA

        route53_conn = r53.connect_to_region(region,
                                             profile_name=self.aws_profile)
        public_hosted_zone = route53_conn.get_zone(hosted_zone_name)

        record_sets = r53.record.ResourceRecordSets(route53_conn,
                                                    public_hosted_zone.id)
        record_sets.add_change('UPSERT', hosted_zone_name, 'A',
                               alias_hosted_zone_id=app_lb_hosted_zone_id,
                               alias_dns_name=app_lb_endpoint,
                               alias_evaluate_target_health=True)
        record_sets.commit()
