import logging


def get_logger(level='INFO'):
    """Returns logger to use"""

    logger = logging.getLogger('rf')
    logger.setLevel(level)
    handler = logging.StreamHandler()
    formatter = logging.Formatter('%(asctime)s - %(name)s - %(levelname)s - %(message)s')
    handler.setFormatter(formatter)
    logger.addHandler(handler)
    return logger
