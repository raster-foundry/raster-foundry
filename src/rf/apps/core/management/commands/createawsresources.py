import json

import boto3
import botocore

from django.core.management.base import BaseCommand

VISIBILITY_TIMEOUT = 120
MAX_RECEIVE_COUNT = 5


class AwsResources(object):
    """"
    Encapsulates a process for creating an SQS queue,
    an attached dead letter queue, and an S3 bucket
    that sends messages to the SQS queue on object creation.
    """
    def __init__(self, stdout=None):
        self.stdout = stdout

        self.stdout.write('Establishing session for creating AWS resources...')

        self.session = boto3.session.Session()

        self.sqs = self.session.resource('sqs')
        self.sqs_client = self.session.client('sqs')

        self.s3 = self.session.resource('s3')
        self.s3_client = self.session.client('s3')

    def create_queue(self, queue_name):
        try:
            queue = self.sqs.get_queue_by_name(QueueName=queue_name)

            self.stdout.write('Queue [{}] already created'.format(queue_name))
        except botocore.exceptions.ClientError as e:
            error_code = e.response['Error']['Code']
            if error_code == 'AWS.SimpleQueueService.NonExistentQueue':
                self.stdout.write('Creating [{}] queue...'.format(queue_name))

                queue = self.sqs.create_queue(QueueName=queue_name)
            else:
                raise e

        return queue

    def make_sqs_policy(self, queue_arn, bucket_name):
        """
        Policy that allows S3 bucket to publish to SQS queue.

        See also: http://docs.aws.amazon.com/AmazonS3/latest/dev/ways-to-add-notification-config-to-bucket.html  # NOQA
        """

        return {
            "Version": "2008-10-17",
            "Statement": [
                {
                    "Sid": "Stmt1444351543000",
                    "Effect": "Allow",
                    "Principal": {
                        "AWS": "*"
                    },
                    "Action": [
                        "SQS:SendMessage"
                    ],
                    "Resource": queue_arn,
                    "Condition": {
                        "ArnLike": {
                            "aws:SourceArn":
                            "arn:aws:s3:*:*:{}".format(bucket_name)
                        }
                    }
                }
            ]
        }

    def readonly_policy(self, bucket_arn):
        return {
            'Version': '2012-10-17',
            'Statement': [
                {
                    'Sid': 'AddPerm',
                    'Effect': 'Allow',
                    'Principal': '*',
                    'Action': [
                        's3:GetObject'
                    ],
                    'Resource': [
                        bucket_arn
                    ]
                }
            ]
        }

    def create_main_queue(self, queue_name, dead_letter_queue, s3_bucket):
        main_queue = self.create_queue(queue_name)

        # Setup dead letter queue and policy so S3 can publish to queue
        dead_letter_arn = dead_letter_queue.attributes['QueueArn']
        main_arn = main_queue.attributes['QueueArn']
        redrive_policy = json.dumps({
            'maxReceiveCount': MAX_RECEIVE_COUNT,
            'deadLetterTargetArn': dead_letter_arn,
        })
        sqs_policy = json.dumps(self.make_sqs_policy(main_arn, s3_bucket))

        self.sqs_client.set_queue_attributes(
            QueueUrl=main_queue.url,
            Attributes={
                'RedrivePolicy': redrive_policy,
                'Policy': sqs_policy
            }
        )

        return main_queue

    def create_bucket(self, bucket_name):
        try:
            bucket = self.s3_client.head_bucket(Bucket=bucket_name)

            self.stdout.write('Bucket [{}] already created'.format(
                bucket_name))
        except botocore.exceptions.ClientError as e:
            error_code = int(e.response['Error']['Code'])
            if error_code == 404:
                self.stdout.write('Creating [{}] bucket...'.format(
                    bucket_name))
                bucket = self.s3_client.create_bucket(Bucket=bucket_name)
            else:
                raise e
        return bucket

    def create_readonly_bucket(self, bucket_name):
        self.create_bucket(bucket_name)
        bucket_arn = 'arn:aws:s3:::{}/*'.format(bucket_name)
        policy = self.readonly_policy(bucket_arn)
        self.s3_client.put_bucket_policy(
            Bucket=bucket_name,
            Policy=json.dumps(policy)
        )

    def create_s3_bucket(self, main_queue, s3_bucket):
        self.create_readonly_bucket(s3_bucket)

        # Setup bucket to notify SQS on object creation.
        waiter = self.s3_client.get_waiter('bucket_exists')
        waiter.wait(Bucket=s3_bucket)

        bucket_notification = self.s3.BucketNotification(s3_bucket)
        main_queue_arn = main_queue.attributes['QueueArn']
        bucket_notification.put(
            NotificationConfiguration={
                'QueueConfigurations': [
                    {
                        'QueueArn': main_queue_arn,
                        'Events': [
                            's3:ObjectCreated:*',
                        ],
                    },
                ],
            }
        )

        # Setup CORS config so that frontend can post to the bucket.
        bucket_cors = self.s3.BucketCors(s3_bucket)
        bucket_cors.put(
            CORSConfiguration={
                'CORSRules': [
                    {
                        'AllowedHeaders': ['*'],
                        'AllowedMethods': ['GET', 'PUT', 'POST', 'DELETE'],
                        'AllowedOrigins': ['*'],
                        'ExposeHeaders': ['ETag'],
                        'MaxAgeSeconds': 3000,
                    },
                ]
            }
        )

    def create_resources(self, queue_name, dead_letter_queue, s3_bucket):
        dead_letter_queue = self.create_queue(dead_letter_queue)
        main_queue = self.create_main_queue(queue_name, dead_letter_queue,
                                            s3_bucket)

        self.create_s3_bucket(main_queue, s3_bucket)


class Command(BaseCommand):
    help = 'Used to create AWS resources the Django web application depends on'
    can_import_settings = True

    def handle(self, *args, **kwargs):
        from django.conf import settings

        r = AwsResources(stdout=self.stdout)
        r.create_resources(settings.AWS_SQS_QUEUE,
                           settings.AWS_SQS_DEAD_LETTER_QUEUE,
                           settings.AWS_BUCKET_NAME)

        r.create_bucket(settings.AWS_LOGS_BUCKET)
        r.create_readonly_bucket(settings.AWS_TILES_BUCKET)
        r.create_readonly_bucket(settings.AWS_WORKSPACE_BUCKET)
