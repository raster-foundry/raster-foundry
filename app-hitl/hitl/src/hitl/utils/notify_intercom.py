import json
import logging
import os

import requests

from .io import get_session

logging.basicConfig(format='%(levelname)s:%(message)s', level=logging.INFO)
logger = logging.getLogger(__name__)

api_base = "https://api.intercom.io"
INTERCOM_TOKEN = os.getenv("INTERCOM_TOKEN")
INTERCOM_ADMIN_ID = os.getenv("INTERCOM_ADMIN_ID")
HOST = os.getenv("RF_HOST")

def notify(user_id, message):
    """Notify user a message to their intercom

    Args:
        user_id (str): ID of the user
        message (str): A message to send
    """
    url = f"{HOST}/api/users/{user_id}/conversations/"
    session = get_session()
    response = session.get(url)
    if not response.content:
        new_convo = create_conversation(user_id, message)        
        return insert_convo(user_id, new_convo["conversation_id"])
    conversation = response.json()
    return reply_conversation(conversation["conversationId"], message)

def insert_convo(user_id, convo_id):
    db_convo = {
        "userId": user_id,
        "converstationId": convo_id
    }
    url = f"{HOST}/api/users/{user_id}/conversations/"
    session = get_session()
    response = session.post(url, json = json.dumps(db_convo))
    try:
        response.raise_for_status()
    except:
        logger.exception(
            f"Unable to insert convo record into DB: {response.text} with {db_convo}"
        )
        raise
    return response.json()

def create_conversation(user_id, message):
    new_convo = {
        "from": {
            "type": "user",
            "id": user_id
        },
        "body": message
    }
    url = f"{api_base}/conversations"
    session = requests.Session()
    session.headers.update({"Authorization": f"Bearer {INTERCOM_TOKEN}"})
    session.headers.update({"Accept": "application/json"})
    session.headers.update({"Content-Type": "application/json"})
    response = session.post(url, json = json.dumps(new_convo))
    try:
        response.raise_for_status()
    except:
        logger.exception(
            f"Unable to create a conversation: {response.text} with {new_convo}"
        )
        raise
    return response.json()

def reply_conversation(convo_id, message):
    reply = {
        "message_type": "comment",
        "type": "admin",
        "admin_id": INTERCOM_ADMIN_ID,
        "body": message
    }
    url = f"{api_base}/conversations/{convo_id}/reply"
    session = requests.Session()
    session.headers.update({"Authorization": f"Bearer {INTERCOM_TOKEN}"})
    session.headers.update({"Accept": "application/json"})
    session.headers.update({"Content-Type": "application/json"})
    response = session.post(url, json = json.dumps(reply))
    try:
        response.raise_for_status()
    except:
        logger.exception(
            f"Unable to reply to conversation: {response.text} with {reply}"
        )
        raise
    return response.json()

