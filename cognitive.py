from json import loads
from re import search, sub
from typing import List

from rasa.model import get_model
from rasa.nlu.model import Interpreter

from configuration import Configuration


class Entity:
    """Defines an entity."""

    def __init__(self, name: str, values: List[str]) -> None:
        """ Initialize Entity.
        :param name the name of the entity
        :param values the values / synonyms of the entity
        """
        self.name = name
        self.values = list(map(lambda s: s.lower(), values))


class EntityGroup:
    """ Defines a group of entities. """

    def __init__(self, name: str, entities: List[Entity] = None) -> None:
        """ Initialize group of entities.
        :param name the name of the group
        :param entities the initial set of entities
        """
        if entities is None:
            entities = []
        self.entities = entities
        self.name = name

    def add_entity(self, entity: Entity) -> None:
        """ Add an entity to group.
        :param entity the entity
        """
        self.entities.append(entity)


class EntityModel:
    """ Defines a serializable Entity Model. """

    def __init__(self, groups: List[EntityGroup]):
        """ Create a new Entity Model.
        :param groups the groups in the model.
        """
        self.groups = groups


class IntentResult:
    """ Defines a result of intents from NLU. """

    def __init__(self, name: str, score: float) -> None:
        """ Create a new Intent Result.
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
        """ Create the new Entity Result.
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
        """ Create service by config.
        :param config the bot configuration
        """
        self._config = config
        model = f"{get_model(self._config.nlu_dir)}/{self._config.nlu_name}"
        self.interpreter = Interpreter.load(model)
        with open(config.entity_file, encoding="utf-8-sig") as ef:
            self.entity_model = self._load_entities_from_json(loads(ef.read()))

    def recognize(self, content: str) -> (List[IntentResult], List[EntityResult]):
        """ Interpret input.
        :param content the text input
        :return a tuple which contains a list of intent results and a list of entity results
        """
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

        for group in self.entity_model.groups:
            for entity in group.entities:
                for value in entity.values:
                    if search(f"\\b{value}\\b", content) is not None:
                        er = EntityResult(entity.name, group.name, value)
                        result.append(er)
                        break

        return result

    @staticmethod
    def _load_entities_from_json(data: dict):
        groups = []
        for group in data["groups"]:
            group_name = group["name"]
            group_entities = []
            for entity in group["entities"]:
                entity_name = entity["name"]
                entity_vals = entity["values"]
                group_entities.append(Entity(entity_name, entity_vals))
            groups.append(EntityGroup(group_name, group_entities))
        return EntityModel(groups)
