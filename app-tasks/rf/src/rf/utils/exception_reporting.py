import logging
import os

import rollbar

logger = logging.getLogger(__name__)


def wrap_rollbar(func):
    """Decorator to wrap functions to report exceptions to rollbar

    Note:
      This re-raises the exception after notifying rollbar
    """
    environment = os.getenv('ENVIRONMENT')
    rollbar_token = os.getenv('ROLLBAR_API_TOKEN')

    # Avoid use of threads by default in case the worker exits before sending message
    rollbar.init(rollbar_token, environment, handler='blocking')

    def func_wrapper(*args, **kwargs):
        try:
            return func(*args, **kwargs)
        except:
            if all([environment, rollbar_token]):
                rollbar.report_exc_info()
            else:
                logger.warning('Both ENVIRONMENT and ROLLBAR_API_TOKEN must be set to log exceptions to rollbar')
            raise

    return func_wrapper
