from json import dumps
from re import sub
from typing import List

import requests
from requests import Response

from configuration import Configuration


class IntentResult:
    """ Defines a result of intents from NLU. """

    def __init__(self, name: str, score: float) -> None:
        """
        Create a new Intent Result.

        :param name the name of the intent
        :param score the probability of the intent.
        """
        self.name = name
        self.score = score

    def __repr__(self) -> str:
        return f"{self.name} ({self.score})"


class EntityResult:
    """ Defines a result of entities from NLU. """

    def __init__(self, name: str, group: str, value: str) -> None:
        """
        Create the new Entity Result.

        :param name the name of the entity
        :param group the group of the entity
        :param value the actual value of the entity
        """
        self.name = name
        self.group = group
        self.value = value

    def __repr__(self) -> str:
        return f"{self.name}[{self.group}]({self.value})"


class NLUService:
    """ Defines a enhanced RASA NLU Service. """

    def __init__(self, config: Configuration) -> None:
        """
        Create service by config.

        :param config the bot configuration
        """
        self._config = config
        self._url = config.nlu_url

        self._version = None

    def recognize(self, content: str) -> (List[IntentResult], List[EntityResult]):
        """
        Interpret input.

        :param content the text input
        :return a tuple which contains a list of intent results and a list of entity results
        """

        if self._version is None:
            self._version = self._get_rasa_version()
            if self._version is None:
                return [], []

        content = sub(r"[^a-zA-Z0-9ÄÖÜäöüß -]", "", content)
        if content == "":
            return [], []

        try:
            payload = dumps({"text": content})
            response = requests.post(f"{self._url}/model/parse", data=payload)
        except Exception:
            print("Cannot establish connection to RASA")
            return [], []

        if response.status_code != 200:
            print(f"Error while getting data from RASA: {response}")
            return [], []

        res = response.json()

        # Set top scoring intent ..
        intent_ranking = res["intent_ranking"]
        entities_dump = res["entities"]

        intents = self._to_intents(intent_ranking)
        entities = self._to_entities(content, entities_dump)

        return intents, entities

    @staticmethod
    def _to_intents(intents: List[dict]) -> List[IntentResult]:
        """
        Convert intents from RASA to a list of intent results.

        :param intents: the output from RASA
        :return: the wrapped intent results
        """
        result = []
        for intent in intents:
            ir = IntentResult(intent["name"], intent["confidence"])
            result.append(ir)
        return result

    @staticmethod
    def _to_entities(content: str, entities: List[dict]) -> List[EntityResult]:
        """
        Identify mentioned entities in a string.

        :param content: the string that shall be searched for entities
        :return: a list of entity results
        """
        result = []
        for entity in entities:
            result.append(EntityResult(entity["value"], entity["entity"], content[entity["start"]: entity["end"]]))

        return result

    def _get_rasa_version(self):
        # Hello from Rasa: 2.6.1
        try:
            response: Response = requests.get(self._url)
        except Exception:
            print("RASA Service not available")
            return None

        if response.status_code != 200:
            print("Cannot connect to RASA Service")
            return None

        status_msg_rgx = "Hello from Rasa: "

        msg = response.text
        if not msg.startswith(status_msg_rgx):
            print("Unknown status message from Rasa Service")
            return None

        version = msg.split(status_msg_rgx)[1]
        print(f"Connected to Rasa Service: {version}")
        return version
