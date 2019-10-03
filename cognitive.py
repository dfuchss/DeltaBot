from datetime import datetime
from json import loads
from re import search, sub
from typing import List
from xml.etree import ElementTree

from rasa.model import get_model
from rasa.nlu.model import Interpreter
from requests import post

from configuration import Configuration
from json_objects import dict_to_obj


class Entity:
    def __init__(self, name: str, values: List[str]) -> None:
        self.name = name
        self.values = list(map(lambda s: s.lower(), values))


class EntityGroup:
    def __init__(self, name: str, entities=None) -> None:
        if entities is None:
            entities = []
        self.entities = entities
        self.name = name

    def add_entity(self, entity: Entity) -> None:
        self.entities.append(entity)


class EntityModel:
    def __init__(self, groups: List[EntityGroup]):
        self.groups = groups


class IntentResult:
    def __init__(self, name: str, score: float) -> None:
        self.name = name
        self.score = score

    def __repr__(self) -> str:
        return f"{self.name} ({self.score})"


class EntityResult:
    def __init__(self, name: str, group: str, value: str) -> None:
        self.name = name
        self.group = group
        self.value = value

    def __repr__(self) -> str:
        return f"{self.name}[{self.group}]({self.value})"


class NLUService:
    def __init__(self, config: Configuration) -> None:
        self._config = config
        model = f"{get_model(self._config.nlu_dir)}/{self._config.nlu_name}"
        self.interpreter = Interpreter.load(model)
        with open(config.entity_file, encoding="utf-8-sig") as ef:
            self.entityModel = loads(ef.read(), object_hook=dict_to_obj)

    def recognize(self, content: str) -> (List[IntentResult], List[EntityResult]):
        content = sub(r"[^a-zA-Z0-9ÄÖÜäöüß -]", "", content)
        if content == "":
            return None, None

        res = self.interpreter.parse(content)
        # Set top scoring intent ..
        intent_ranking = res["intent_ranking"]

        intents = self._to_intents(intent_ranking)
        entities = self._recognize_entities(content)

        if len(intents) == 0 or intents[0].score < self._config.nlu_threshold:
            with open(self._config.nlu_not_classified, "w+", encoding="utf-8-sig") as nc:
                nc.write(f"- {content}")

        return intents, entities

    @staticmethod
    def _to_intents(intents: List[dict]) -> List[IntentResult]:
        result = []
        for intent in intents:
            ir = IntentResult(intent["name"], intent["confidence"])
            result.append(ir)
        return result

    def _recognize_entities(self, content: str) -> List[EntityResult]:
        result = []
        content = content.lower()

        for group in self.entityModel.groups:
            for entity in group.entities:
                for value in entity.values:
                    if search(f"\\b{value}\\b", content) is not None:
                        er = EntityResult(entity.name, group.name, value)
                        result.append(er)
                        break

        return result


class TextToSpeech:
    TokenLife = 600

    def __init__(self, config: Configuration) -> None:
        self._access_token = None
        self._access_time = None
        self._config = config

    def _get_token(self) -> None:
        fetch_token_url = f"https://{self._config.tts_region}.api.cognitive.microsoft.com/sts/v1.0/issueToken"
        headers = {'Ocp-Apim-Subscription-Key': self._config.tts_key}
        response = post(fetch_token_url, headers=headers)
        self._access_token = str(response.text)
        self._access_time = datetime.now()

    def create_tts(self, text: str, path: str) -> bool:
        if self._access_token is None or (datetime.now() - self._access_time).total_seconds() >= self.TokenLife:
            self._get_token()

        url = f"https://{self._config.tts_region}.tts.speech.microsoft.com/cognitiveservices/v1"
        headers = {
            'Authorization': 'Bearer ' + self._access_token,
            'Content-Type': 'application/ssml+xml',
            'X-Microsoft-OutputFormat': "audio-24khz-48kbitrate-mono-mp3",
            'User-Agent': self._config.tts_resource
        }
        xml_body = ElementTree.Element('speak', version='1.0')
        xml_body.set('{http://www.w3.org/XML/1998/namespace}lang', 'de-DE')
        voice = ElementTree.SubElement(xml_body, 'voice')
        voice.set('{http://www.w3.org/XML/1998/namespace}lang', 'de-DE')
        # Speeches: Hedda, HeddaRUS, Stefan-Apollo
        voice.set('name', 'Microsoft Server Speech Text to Speech Voice (de-DE, Stefan, Apollo)')
        voice.text = sub(r"[^.,;:\na-zA-Z0-9ÄÖÜäöüß -]", "", text)
        body = ElementTree.tostring(xml_body)

        response = post(url, headers=headers, data=body)
        if response.status_code == 200:
            with open(path, 'wb') as audio:
                audio.write(response.content)
                return True
        return False
