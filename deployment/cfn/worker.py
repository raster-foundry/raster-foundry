from troposphere import (
    Parameter,
    Ref,
    Tags,
    Base64,
    Join,
    cloudwatch as cw,
    ec2,
    autoscaling as asg
)

from utils.cfn import get_recent_ami

from utils.constants import (
    ALLOW_ALL_CIDR,
    EC2_INSTANCE_TYPES,
    HTTP,
    HTTPS,
    POSTGRESQL,
    SSH,
    VPC_CIDR
)

from majorkirby import StackNode, MKUnresolvableInputError


class Worker(StackNode):
    INPUTS = {
        'Tags': ['global:Tags'],
        'Region': ['global:Region'],
        'StackType': ['global:StackType'],
        'StackColor': ['global:StackColor'],
        'KeyName': ['global:KeyName'],
        'AvailabilityZones': ['global:AvailabilityZones',
                              'VPC:AvailabilityZones'],
        'RDSPassword': ['global:RDSPassword', 'DataPlane:RDSPassword'],
        'WorkerInstanceType': ['global:WorkerInstanceType'],
        'WorkerAMI': ['global:WorkerAMI'],
        'WorkerInstanceProfile': ['global:WorkerInstanceProfile'],
        'WorkerAutoScalingDesired': ['global:WorkerAutoScalingDesired'],  # NOQA
        'WorkerAutoScalingMin': ['global:WorkerAutoScalingMin'],
        'WorkerAutoScalingMax': ['global:WorkerAutoScalingMax'],
        'PublicSubnets': ['global:PublicSubnets', 'VPC:PublicSubnets'],
        'PrivateSubnets': ['global:PrivateSubnets', 'VPC:PrivateSubnets'],
        'VpcId': ['global:VpcId', 'VPC:VpcId'],
        'GlobalNotificationsARN': ['global:GlobalNotificationsARN'],
    }

    DEFAULTS = {
        'Tags': {},
        'Region': 'us-east-1',
        'StackType': 'Staging',
        'StackColor': 'Green',
        'KeyName': 'rf-stg',
        'IPAccess': ALLOW_ALL_CIDR,
        'WorkerInstanceType': 't2.micro',
        'WorkerInstanceProfile': 'WorkerInstanceProfile',
        'WorkerAutoScalingDesired': '1',
        'WorkerAutoScalingMin': '1',
        'WorkerAutoScalingMax': '1',
    }

    ATTRIBUTES = {
        'StackType': 'StackType',
        'StackColor': 'StackColor',
    }

    def set_up_stack(self):
        super(Worker, self).set_up_stack()

        tags = self.get_input('Tags').copy()
        tags.update({'StackType': 'Worker'})

        self.default_tags = tags
        self.region = self.get_input('Region')

        self.add_description('Worker stack for Raster Foundry')

        # Parameters
        self.color = self.add_parameter(Parameter(
            'StackColor', Type='String',
            Description='Stack color', AllowedValues=['Blue', 'Green']
        ), 'StackColor')

        self.keyname = self.add_parameter(Parameter(
            'KeyName', Type='String',
            Description='Name of an existing EC2 key pair'
        ), 'KeyName')

        self.availability_zones = self.add_parameter(Parameter(
            'AvailabilityZones', Type='CommaDelimitedList',
            Description='Comma delimited list of availability zones'
        ), 'AvailabilityZones')

        self.rds_password = self.add_parameter(Parameter(
            'RDSPassword', Type='String', NoEcho=True,
            Description='Database password',
        ), 'RDSPassword')

        self.worker_instance_type = self.add_parameter(Parameter(
            'WorkerInstanceType', Type='String', Default='t2.micro',
            Description='Worker EC2 instance type',
            AllowedValues=EC2_INSTANCE_TYPES,
            ConstraintDescription='must be a valid EC2 instance type.'
        ), 'WorkerInstanceType')

        self.worker_ami = self.add_parameter(Parameter(
            'WorkerAMI', Type='String',
            Default=self.get_recent_worker_ami(),
            Description='Worker AMI'
        ), 'WorkerAMI')

        self.worker_instance_profile = self.add_parameter(Parameter(
            'WorkerInstanceProfile', Type='String',
            Default='WorkerInstanceProfile',
            Description='Worker instance profile'
        ), 'WorkerInstanceProfile')

        self.worker_auto_scaling_desired = self.add_parameter(Parameter(
            'WorkerAutoScalingDesired', Type='String', Default='1',
            Description='Worker AutoScalingGroup desired'
        ), 'WorkerAutoScalingDesired')

        self.worker_auto_scaling_min = self.add_parameter(Parameter(
            'WorkerAutoScalingMin', Type='String', Default='1',
            Description='Worker AutoScalingGroup minimum'
        ), 'WorkerAutoScalingMin')

        self.worker_auto_scaling_max = self.add_parameter(Parameter(
            'WorkerAutoScalingMax', Type='String', Default='1',
            Description='Worker AutoScalingGroup maximum'
        ), 'WorkerAutoScalingMax')

        self.public_subnets = self.add_parameter(Parameter(
            'PublicSubnets', Type='CommaDelimitedList',
            Description='A list of public subnets'
        ), 'PublicSubnets')

        self.private_subnets = self.add_parameter(Parameter(
            'PrivateSubnets', Type='CommaDelimitedList',
            Description='A list of private subnets'
        ), 'PrivateSubnets')

        self.vpc_id = self.add_parameter(Parameter(
            'VpcId', Type='String',
            Description='VPC ID'
        ), 'VpcId')

        self.notification_topic_arn = self.add_parameter(Parameter(
            'GlobalNotificationsARN', Type='String',
            Description='ARN for an SNS topic to broadcast notifications'
        ), 'GlobalNotificationsARN')

        worker_security_group = self.create_security_groups()
        worker_auto_scaling_group = self.create_auto_scaling_resources(
            worker_security_group)

        self.create_cloud_watch_resources(worker_auto_scaling_group)

    def get_recent_worker_ami(self):
        try:
            worker_ami_id = self.get_input('WorkerAMI')
        except MKUnresolvableInputError:
            filters = {'name': 'rf-worker-*',
                       'architecture': 'x86_64',
                       'block-device-mapping.volume-type': 'gp2',
                       'root-device-type': 'ebs',
                       'virtualization-type': 'hvm'}

            worker_ami_id = get_recent_ami(self.aws_profile, filters,
                                           region=self.region)

        return worker_ami_id

    def create_security_groups(self):
        worker_security_group_name = 'sgWorker'

        return self.add_resource(ec2.SecurityGroup(
            worker_security_group_name,
            GroupDescription='Enables access to workers',
            VpcId=Ref(self.vpc_id),
            SecurityGroupIngress=[
                ec2.SecurityGroupRule(
                    IpProtocol='tcp', CidrIp=VPC_CIDR, FromPort=p, ToPort=p
                )
                for p in [SSH]
            ],
            SecurityGroupEgress=[
                ec2.SecurityGroupRule(
                    IpProtocol='tcp', CidrIp=VPC_CIDR, FromPort=p, ToPort=p
                )
                for p in [POSTGRESQL]
            ] + [
                ec2.SecurityGroupRule(
                    IpProtocol='tcp', CidrIp=ALLOW_ALL_CIDR, FromPort=p,
                    ToPort=p
                )
                for p in [HTTP, HTTPS]
            ],
            Tags=self.get_tags(Name=worker_security_group_name)
        ))

    def create_auto_scaling_resources(self, worker_security_group):
        worker_launch_config_name = 'lcWorker'

        worker_launch_config = self.add_resource(
            asg.LaunchConfiguration(
                worker_launch_config_name,
                ImageId=Ref(self.worker_ami),
                IamInstanceProfile=Ref(self.worker_instance_profile),
                InstanceType=Ref(self.worker_instance_type),
                KeyName=Ref(self.keyname),
                SecurityGroups=[Ref(worker_security_group)],
                UserData=Base64(
                    Join('', self.get_cloud_config()))
            ))

        worker_auto_scaling_group_name = 'asgWorker'

        return self.add_resource(
            asg.AutoScalingGroup(
                worker_auto_scaling_group_name,
                AvailabilityZones=Ref(self.availability_zones),
                Cooldown=300,
                DesiredCapacity=Ref(self.worker_auto_scaling_desired),
                HealthCheckGracePeriod=600,
                HealthCheckType='EC2',
                LaunchConfigurationName=Ref(worker_launch_config),
                MaxSize=Ref(self.worker_auto_scaling_max),
                MinSize=Ref(self.worker_auto_scaling_min),
                NotificationConfigurations=[
                    asg.NotificationConfigurations(
                        TopicARN=Ref(self.notification_topic_arn),
                        NotificationTypes=[
                            asg.EC2_INSTANCE_LAUNCH,
                            asg.EC2_INSTANCE_LAUNCH_ERROR,
                            asg.EC2_INSTANCE_TERMINATE,
                            asg.EC2_INSTANCE_TERMINATE_ERROR
                        ]
                    )
                ],
                VPCZoneIdentifier=Ref(self.private_subnets),
                Tags=[asg.Tag('Name', 'Worker', True)]
            )
        )

    def get_cloud_config(self):
        return ['#cloud-config\n',
                '\n',
                'write_files:\n',
                '  - path: /etc/rf.d/env/RF_STACK_COLOR\n',
                '    permissions: 0750\n',
                '    owner: root:rf\n',
                '    content: ', Ref(self.color), '\n',
                '  - path: /etc/rf.d/env/RF_DB_PASSWORD\n',
                '    permissions: 0750\n',
                '    owner: root:rf\n',
                '    content: ', Ref(self.rds_password)]

    def create_cloud_watch_resources(self, worker_auto_scaling_group):
        self.add_resource(cw.Alarm(
            'alarmWorkerCPU',
            AlarmDescription='Worker scaling group high CPU',
            AlarmActions=[Ref(self.notification_topic_arn)],
            Statistic='Average',
            Period=300,
            Threshold='50',
            EvaluationPeriods=1,
            ComparisonOperator='GreaterThanThreshold',
            MetricName='CPUUtilization',
            Namespace='AWS/EC2',
            Dimensions=[
                cw.MetricDimension(
                    'metricAutoScalingGroupName',
                    Name='AutoScalingGroupName',
                    Value=Ref(worker_auto_scaling_group)
                )
            ]
        ))

    def get_tags(self, **kwargs):
        """Helper method to return Troposphere tags + default tags

        Args:
          **kwargs: arbitrary keyword arguments to be used as tags
        """
        kwargs.update(self.default_tags)
        return Tags(**kwargs)
