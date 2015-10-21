# -*- coding: utf-8 -*-
from __future__ import print_function
from __future__ import unicode_literals
from __future__ import division

import json

import boto3
from django.conf import settings


class SQSManager(object):
    """
    Manages SQS queue creation and message functionality
    """
    def __init__(self):
        self.client = boto3.client('sqs')
        self.queue_url = self.client.get_queue_url(
            QueueName=settings.AWS_SQS_QUEUE)['QueueUrl']

    def get_message(self):
        response = self.client.receive_message(QueueUrl=self.queue_url,
                                               WaitTimeSeconds=20)

        message = response['Messages'][0]
        payload = json.loads(message['Body'])

        return {
            'original_message': message,
            'payload': payload
        }

    def add_message(self, job_type, data, delay=0):
        payload = self.make_payload(job_type, data)
        self.client.send_message(
            QueueUrl=self.queue_url,
            MessageBody=json.dumps(payload),
            DelaySeconds=delay,
        )

    def make_payload(self, job_type, data):
        return {
            'eventSource': 'rf:boto',
            'eventName': job_type,
            'data': data
        }

    def remove_message(self, message):
        handle = message['original_message']['ReceiptHandle']
        self.client.delete_message(
            QueueUrl=self.queue_url,
            ReceiptHandle=handle,
        )
