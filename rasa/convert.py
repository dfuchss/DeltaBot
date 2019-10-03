from json import dumps, load
import os
import sys
import inspect

current_dir = os.path.dirname(os.path.abspath(inspect.getfile(inspect.currentframe())))
parent_dir = os.path.dirname(current_dir)
sys.path.insert(0, parent_dir)

from cognitive import EntityGroup, Entity, EntityModel
from json_objects import convert_to_dict


def main() -> None:
    with open("rasa/luis.json", encoding="utf-8-sig") as jsonfile:
        data = load(jsonfile)

    intents = []
    utterances = {}
    entities = {}
    groups = {}

    # Parse List-Entities
    for closedList in data["closedLists"]:
        k1 = closedList["name"]
        groups[k1] = []
        for sub in closedList["subLists"]:
            k = f"{k1}_{sub['canonicalForm']}"
            groups[k1].append(sub['canonicalForm'])
            if k not in entities.keys():
                entities[k] = [sub['canonicalForm']]

            else:
                entities[k].append(sub['canonicalForm'])

            for value in sub["list"]:
                entities[k].append(value)

    for intent in data["intents"]:
        intents.append(intent["name"])

    for utterance in data["utterances"]:
        k = utterance["intent"]
        v = utterance["text"]

        if k not in utterances.keys():
            utterances[k] = [v]
        else:
            utterances[k].append(v)

    # Write it ..
    with open("rasa/training.md", "w", encoding="utf-8-sig") as outfile:
        for intent in intents:
            outfile.write(f"## intent:{intent}\n")
            for utterance in utterances[intent]:
                outfile.write(f"- {utterance}\n")
            outfile.write("\n")

    entity_groups = []

    for gk in groups.keys():
        eg = EntityGroup(gk, [])
        entity_groups.append(eg)
        for elements in groups[gk]:
            e = Entity(elements, entities[gk + "_" + elements])
            eg.add_entity(e)

    with open("rasa/training-entities.json", "w", encoding="utf-8-sig") as outfile:
        outfile.write(dumps(EntityModel(entity_groups), default=convert_to_dict, indent=4))


if __name__ == "__main__":
    main()
