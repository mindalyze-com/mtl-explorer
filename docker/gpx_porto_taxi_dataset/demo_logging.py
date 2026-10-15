"""Timestamped demo preparation logs on stdout for the console and docker logs."""
import logging
import sys
import time

LOG_FORMAT = "%(asctime)s.%(msecs)03dZ %(levelname)-5s [%(process)d] %(name)s : %(message)s"
LOG_DATE_FORMAT = "%Y-%m-%dT%H:%M:%S"


def get_logger(name: str) -> logging.Logger:
    logger = logging.getLogger(name)
    if not logger.handlers:
        formatter = logging.Formatter(LOG_FORMAT, datefmt=LOG_DATE_FORMAT)
        formatter.converter = time.gmtime
        handler = logging.StreamHandler(sys.stdout)
        handler.setFormatter(formatter)
        logger.addHandler(handler)
        logger.setLevel(logging.INFO)
        logger.propagate = False
    return logger
