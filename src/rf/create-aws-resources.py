#!/usr/bin/env python

# -*- coding: utf-8 -*-
from __future__ import print_function
from __future__ import unicode_literals
from __future__ import division

import json
from os import environ

import boto3
import botocore

SQS_REGION = environ.get('RF_SQS_REGION')
SQS_QUEUE = environ.get('RF_SQS_QUEUE')
SQS_DEAD_LETTER_QUEUE = environ.get('RF_SQS_DEAD_LETTER_QUEUE')
S3_BUCKET = environ.get('RF_S3_BUCKET')

VISIBILITY_TIMEOUT = 120
MAX_RECEIVE_COUNT = 5


def make_sqs_policy(queue_arn, bucket_name):
    """
    Policy that allows S3 bucket to publish to SQS queue.
    from http://docs.aws.amazon.com/AmazonS3/latest/dev/
    ... ways-to-add-notification-config-to-bucket.html
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
                        "aws:SourceArn": "arn:aws:s3:*:*:" + bucket_name
                    }
                }
            }
        ]
    }


class CreateAwsResources(object):
    """"
    Encapsulates a process for creating an SQS queue,
    an attached dead letter queue, and an S3 bucket
    that sends messages to the SQS queue on object creation.
    """
    def __init__(self):
        print('create-aws-resources.py started')

        self.session = boto3.session.Session(region_name=SQS_REGION)

        self.sqs = self.session.resource('sqs')
        self.sqs_client = self.session.client('sqs')

        self.s3 = self.session.resource('s3')
        self.s3_client = self.session.client('s3')

        self.create_resources()

    def create_queue(self, queue_name):
        try:
            queue = self.sqs.get_queue_by_name(QueueName=queue_name)
        except botocore.exceptions.ClientError as e:
            error_code = e.response['Error']['Code']
            if error_code == 'AWS.SimpleQueueService.NonExistentQueue':
                queue = self.sqs.create_queue(QueueName=queue_name)
            else:
                raise e

        return queue

    def create_main_queue(self, dead_letter_queue):
        main_queue = self.create_queue(SQS_QUEUE)

        # Setup dead letter queue and policy so S3 can publish to queue
        dead_letter_arn = dead_letter_queue.attributes['QueueArn']
        main_arn = main_queue.attributes['QueueArn']
        redrive_policy = json.dumps({
            'maxReceiveCount': MAX_RECEIVE_COUNT,
            'deadLetterTargetArn': dead_letter_arn,
        })
        sqs_policy = json.dumps(make_sqs_policy(main_arn, S3_BUCKET))

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
        except botocore.exceptions.ClientError as e:
            error_code = int(e.response['Error']['Code'])
            if error_code == 404:
                bucket = self.s3_client.create_bucket(Bucket=bucket_name)
            else:
                raise e
        return bucket

    def create_s3_bucket(self, main_queue):
        self.create_bucket(S3_BUCKET)

        # Setup bucket to notify SQS on object creation.
        waiter = self.s3_client.get_waiter('bucket_exists')
        waiter.wait(Bucket=S3_BUCKET)

        bucket_notification = self.s3.BucketNotification(S3_BUCKET)
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
        bucket_cors = self.s3.BucketCors(S3_BUCKET)
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

    def create_resources(self):
        dead_letter_queue = self.create_queue(SQS_DEAD_LETTER_QUEUE)
        main_queue = self.create_main_queue(dead_letter_queue)

        self.create_s3_bucket(main_queue)

CreateAwsResources()
