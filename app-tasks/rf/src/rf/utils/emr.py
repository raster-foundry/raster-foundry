import logging
import time

import boto3
import dns.resolver
from retrying import retry

logger = logging.getLogger(__name__)


def get_cluster_id():
    """Determine EMR cluster ID via DNS"""
    resolver = dns.resolver.Resolver()
    cluster_id = resolver.query('dataproc.rasterfoundry.com', 'TXT')[0]
    return cluster_id.to_text().strip('"')


@retry(wait_exponential_multiplier=2000, wait_exponential_max=30000, stop_max_attempt_number=5)
def wait_for_emr_success(step_id, cluster_id):
    """Wait for batch success/failure given an initial EMR response

    Args:
        step_id (str): EMR step ID
        cluster_id (str): EMR cluster ID to check status of step in

    Returns:
        boolean
    """
    emr = boto3.client('emr')
    get_description = lambda: emr.describe_step(ClusterId=cluster_id, StepId=step_id)
    logger.info('Starting to check for status updates for step %s', step_id)
    step_description = get_description()
    current_status = step_description['Step']['Status']['State']
    logger.info('Initial status: %s', current_status)
    while current_status not in ['COMPLETED', 'FAILED']:
        description = get_description()
        status = description['Step']['Status']['State']
        if status != current_status:
            logger.info('Updating status of %s. Old Status: %s New Status: %s',
                        step_id, current_status, status)
            current_status = status
        time.sleep(30)
    is_success = (current_status == 'COMPLETED')
    if is_success:
        logger.info('Successfully completed step id: %s', step_id)
        return True
    else:
        logger.error('Something went wrong with %s. Current Status: %s', step_id, current_status)
        raise Exception('Step {} failed'.format(step_id))