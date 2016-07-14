from troposphere import (
    Parameter,
    Ref,
    Tags,
    Join,
    GetAtt,
    cloudwatch,
    ec2,
    rds,
    route53 as r53
)

from utils.constants import (
    POSTGRESQL,
    RDS_INSTANCE_TYPES,
    VPC_CIDR
)

from majorkirby import StackNode


class DataPlane(StackNode):
    INPUTS = {
        'Tags': ['global:Tags'],
        'Region': ['global:Region'],
        'StackType': ['global:StackType'],
        'RDSInstanceType': ['global:RDSInstanceType'],
        'RDSDbName': ['global:RDSDbName'],
        'RDSUsername': ['global:RDSUsername'],
        'RDSPassword': ['global:RDSPassword'],
        'PrivateSubnets': ['global:PrivateSubnets', 'VPC:PrivateSubnets'],
        'PrivateHostedZoneId': ['global:PrivateHostedZoneId',
                                'PrivateHostedZone:PrivateHostedZoneId'],
        'PrivateHostedZoneName': ['global:PrivateHostedZoneName'],
        'VpcId': ['global:VpcId', 'VPC:VpcId'],
        'GlobalNotificationsARN': ['global:GlobalNotificationsARN'],
    }

    DEFAULTS = {
        'Tags': {},
        'Region': 'us-east-1',
        'StackType': 'Staging',
        'RDSInstanceType': 'db.t2.micro',
        'RDSDbName': 'rasterfoundry',
        'RDSUsername': 'rasterfoundry',
        'RDSPassword': 'rasterfoundry',
    }

    ATTRIBUTES = {'StackType': 'StackType'}

    def set_up_stack(self):
        super(DataPlane, self).set_up_stack()

        tags = self.get_input('Tags').copy()
        tags.update({'StackType': 'DataPlane'})

        self.default_tags = tags
        self.region = self.get_input('Region')

        self.add_description('Data plane stack for Raster Foundry')

        # Parameters
        self.rds_instance_type = self.add_parameter(Parameter(
            'RDSInstanceType', Type='String', Default='db.t2.micro',
            Description='RDS instance type', AllowedValues=RDS_INSTANCE_TYPES,
            ConstraintDescription='must be a valid RDS instance type.'
        ), 'RDSInstanceType')

        self.rds_db_name = self.add_parameter(Parameter(
            'RDSDbName', Type='String', Description='Database name'
        ), 'RDSDbName')

        self.rds_username = self.add_parameter(Parameter(
            'RDSUsername', Type='String', Description='Database username'
        ), 'RDSUsername')

        self.rds_password = self.add_parameter(Parameter(
            'RDSPassword', Type='String', NoEcho=True,
            Description='Database password',
        ), 'RDSPassword')

        self.private_subnets = self.add_parameter(Parameter(
            'PrivateSubnets', Type='CommaDelimitedList',
            Description='A list of private subnets'
        ), 'PrivateSubnets')

        self.private_hosted_zone_id = self.add_parameter(Parameter(
            'PrivateHostedZoneId', Type='String',
            Description='Route 53 private hosted zone ID'
        ), 'PrivateHostedZoneId')

        self.private_hosted_zone_name = self.add_parameter(Parameter(
            'PrivateHostedZoneName', Type='String',
            Description='Route 53 private hosted zone name'
        ), 'PrivateHostedZoneName')

        self.vpc_id = self.add_parameter(Parameter(
            'VpcId', Type='String',
            Description='VPC ID'
        ), 'VpcId')

        self.notification_topic_arn = self.add_parameter(Parameter(
            'GlobalNotificationsARN', Type='String',
            Description='ARN for an SNS topic to broadcast notifications'
        ), 'GlobalNotificationsARN')

        rds_database = self.create_rds_instance()
        self.create_rds_cloudwatch_alarms(rds_database)

        self.create_dns_records(rds_database)

    def create_rds_instance(self):
        rds_security_group_name = 'sgDatabaseServer'

        rds_security_group = self.add_resource(ec2.SecurityGroup(
            rds_security_group_name,
            GroupDescription='Enables access to database servers',
            VpcId=Ref(self.vpc_id),
            SecurityGroupIngress=[
                ec2.SecurityGroupRule(
                    IpProtocol='tcp', CidrIp=VPC_CIDR, FromPort=p, ToPort=p
                )
                for p in [POSTGRESQL]
            ],
            SecurityGroupEgress=[
                ec2.SecurityGroupRule(
                    IpProtocol='tcp', CidrIp=VPC_CIDR, FromPort=p, ToPort=p
                )
                for p in [POSTGRESQL]
            ],
            Tags=self.get_tags(Name=rds_security_group_name)
        ))

        rds_subnet_group_name = 'dbsngDatabaseServer'

        rds_subnet_group = self.add_resource(rds.DBSubnetGroup(
            rds_subnet_group_name,
            DBSubnetGroupDescription='Private subnets for the RDS instances',
            SubnetIds=Ref(self.private_subnets),
            Tags=self.get_tags(Name=rds_subnet_group_name)
        ))

        rds_parameter_group = self.add_resource(rds.DBParameterGroup(
            'dbpgDatabaseServer',
            Family='postgres9.4',
            Description='Parameter group for the RDS instances',
            Parameters={'log_min_duration_statement': '500'}
        ))

        rds_database_name = 'DatabaseServer'

        return self.add_resource(rds.DBInstance(
            rds_database_name,
            AllocatedStorage=64,
            AllowMajorVersionUpgrade=False,
            AutoMinorVersionUpgrade=True,
            BackupRetentionPeriod=30,
            DBInstanceClass=Ref(self.rds_instance_type),
            DBName=Ref(self.rds_db_name),
            DBParameterGroupName=Ref(rds_parameter_group),
            DBSubnetGroupName=Ref(rds_subnet_group),
            Engine='postgres',
            EngineVersion='9.4.4',
            MasterUsername=Ref(self.rds_username),
            MasterUserPassword=Ref(self.rds_password),
            MultiAZ=False,
            PreferredBackupWindow='04:00-04:30',  # 12:00AM-12:30AM ET
            PreferredMaintenanceWindow='sun:04:30-sun:05:30',  # SUN 12:30AM-01:30AM ET
            StorageType='gp2',
            VPCSecurityGroups=[Ref(rds_security_group)],
            Tags=self.get_tags(Name=rds_database_name)
        ))

    def create_rds_cloudwatch_alarms(self, rds_database):
        self.add_resource(cloudwatch.Alarm(
            'alarmDatabaseServerCPUUtilization',
            AlarmDescription='Database server CPU utilization',
            AlarmActions=[Ref(self.notification_topic_arn)],
            Statistic='Average',
            Period=300,
            Threshold='75',
            EvaluationPeriods=1,
            ComparisonOperator='GreaterThanThreshold',
            MetricName='CPUUtilization',
            Namespace='AWS/RDS',
            Dimensions=[
                cloudwatch.MetricDimension(
                    'metricDatabaseServerName',
                    Name='DBInstanceIdentifier',
                    Value=Ref(rds_database)
                )
            ],
        ))

        self.add_resource(cloudwatch.Alarm(
            'alarmDatabaseServerDiskQueueDepth',
            AlarmDescription='Database server disk queue depth',
            AlarmActions=[Ref(self.notification_topic_arn)],
            Statistic='Average',
            Period=60,
            Threshold='10',
            EvaluationPeriods=1,
            ComparisonOperator='GreaterThanThreshold',
            MetricName='DiskQueueDepth',
            Namespace='AWS/RDS',
            Dimensions=[
                cloudwatch.MetricDimension(
                    'metricDatabaseServerName',
                    Name='DBInstanceIdentifier',
                    Value=Ref(rds_database)
                )
            ],
        ))

        self.add_resource(cloudwatch.Alarm(
            'alarmDatabaseServerFreeStorageSpace',
            AlarmDescription='Database server free storage space',
            AlarmActions=[Ref(self.notification_topic_arn)],
            Statistic='Average',
            Period=60,
            Threshold=str(int(5.0e+09)),  # 5GB in bytes
            EvaluationPeriods=1,
            ComparisonOperator='LessThanThreshold',
            MetricName='FreeStorageSpace',
            Namespace='AWS/RDS',
            Dimensions=[
                cloudwatch.MetricDimension(
                    'metricDatabaseServerName',
                    Name='DBInstanceIdentifier',
                    Value=Ref(rds_database)
                    )
                ],
            ))

        self.add_resource(cloudwatch.Alarm(
            'alarmDatabaseServerFreeableMemory',
            AlarmDescription='Database server freeable memory',
            AlarmActions=[Ref(self.notification_topic_arn)],
            Statistic='Average',
            Period=60,
            Threshold=str(int(1.28e+08)),  # 128MB in bytes
            EvaluationPeriods=1,
            ComparisonOperator='LessThanThreshold',
            MetricName='FreeableMemory',
            Namespace='AWS/RDS',
            Dimensions=[
                cloudwatch.MetricDimension(
                    'metricDatabaseServerName',
                    Name='DBInstanceIdentifier',
                    Value=Ref(rds_database)
                    )
                ],
            ))

    def create_dns_records(self, rds_database):
        self.add_resource(r53.RecordSetGroup(
            'dnsPrivateRecords',
            HostedZoneId=Ref(self.private_hosted_zone_id),
            RecordSets=[
                r53.RecordSet(
                    'dnsDatabaseServer',
                    Name=Join('', ['database.service.',
                              Ref(self.private_hosted_zone_name), '.']),
                    Type='CNAME',
                    TTL='10',
                    ResourceRecords=[
                        GetAtt(rds_database, 'Endpoint.Address')
                    ]
                ),
            ]
        ))

    def get_tags(self, **kwargs):
        """Helper method to return Troposphere tags + default tags

        Args:
          **kwargs: arbitrary keyword arguments to be used as tags
        """
        kwargs.update(self.default_tags)
        return Tags(**kwargs)
