# -*- coding: utf-8 -*-
from __future__ import print_function
from __future__ import unicode_literals
from __future__ import division

from django.conf import settings

import json
import boto.sqs
from boto.sqs.message import Message

MAX_WAIT = 20  # Seconds.
DEFAULT_DELAY = 0


class SQSManager(object):
    """
    Manages SQS queue creation and message functionality
    """
    def __init__(self):
        self.q = self._get_queue()

    def get_message(self):
        message = self.q.read(wait_time_seconds=MAX_WAIT,
                              message_attributes=['All'])
        if message:
            # Return raw message and parsed body.
            return {
                'message': message,
                'body': json.loads(message.get_body())
            }
        else:
            return None

    def add_message(self, job_type, data, delay=DEFAULT_DELAY):
        payload = self._make_payload(job_type, data)
        self._post_to_queue(payload, delay)

    def remove_message(self, message):
        self.q.delete_message(message['message'])

    def _make_payload(self, job_type, data):
        return {
            'Records': [
                {
                    'eventSource': 'rf:boto',
                    'eventName': job_type,
                    'data': data
                }
            ]
        }

    def _get_queue(self):
        """
        Get a connection to the SQS queue.
        """
        queue_name = settings.AWS_SQS_QUEUE
        queue_region = settings.AWS_SQS_REGION
        conn = boto.sqs.connect_to_region(queue_region)
        return conn.get_queue(queue_name)

    def _post_to_queue(self, payload, delay=DEFAULT_DELAY):
        """
        Create an SQS message from a payload.
        data -- structured data representing the new SQS message.
        """
        m = Message()
        m.set_body(json.dumps(payload))
        self.q.write(m, delay)
