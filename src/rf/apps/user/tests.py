# -*- coding: utf-8 -*-
from __future__ import print_function
from __future__ import unicode_literals
from __future__ import division

from django.contrib.auth.models import User
from django.test import TestCase
from django.test.client import Client


class UserTestCase(TestCase):
    HOMEPAGE_URL = '/'
    LOGIN_URL = '/user/login'

    def setUp(self):
        self.client = Client()

        User.objects.create_user(username='bob', email='bob@azavea.com',
                                 password='bob')

    def attempt_login(self, username, password):
        payload = {'username': username, 'password': password}
        return self.client.post(self.LOGIN_URL, payload)

    def test_no_username_returns_403(self):
        response = self.attempt_login('', 'bob')
        self.assertEqual(response.status_code, 403,
                         'Incorrect server response. Expected 403 found %s'
                         % response.status_code)

    def test_no_password_returns_403(self):
        response = self.attempt_login('bob', '')
        self.assertEqual(response.status_code, 403,
                         'Incorrect server response. Expected 403 found %s'
                         % response.status_code)

    def test_bad_username_returns_403(self):
        response = self.attempt_login('notbob', 'bob')
        self.assertEqual(response.status_code, 403,
                         'Incorrect server response. Expected 403 found %s'
                         % response.status_code)

    def test_bad_password_returns_403(self):
        response = self.attempt_login('bob', 'badpass')
        self.assertEqual(response.status_code, 403,
                         'Incorrect server response. Expected 403 found %s'
                         % response.status_code)

    def test_bad_credentials_returns_403(self):
        response = self.attempt_login('bob1', 'bob1')
        self.assertEqual(response.status_code, 403,
                         'Incorrect server response. Expected 403 found %s'
                         % response.status_code)

    def test_good_credentials_returns_200(self):
        response = self.attempt_login('bob', 'bob')
        self.assertEqual(response.status_code, 200,
                         'Incorrect server response. Expected 200 found %s'
                         % response.status_code)
