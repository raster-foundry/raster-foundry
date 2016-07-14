import os

from base import *  # NOQA

# TEST SETTINGS
ALLOWED_HOSTS = ['localhost']

PASSWORD_HASHERS = (
    'django.contrib.auth.hashers.MD5PasswordHasher',
)

CACHES = {
    'default': {
        'BACKEND': 'django.core.cache.backends.dummy.DummyCache',
    }
}

SELENIUM_DEFAULT_BROWSER = 'firefox'
SELENIUM_TEST_COMMAND_OPTIONS = {'pattern': 'uitest*.py'}

DJANGO_LIVE_TEST_SERVER_ADDRESS = os.environ.get(
    'DJANGO_LIVE_TEST_SERVER_ADDRESS', 'localhost:9001')
