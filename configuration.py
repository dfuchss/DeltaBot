"""The configuration of the DeltaBot"""
from os import getenv, environ


class Configuration:
    def __init__(self):
        # NLU
        self.nlu_dir = getenv("NLUDir", "rasa/models")
        self.nlu_name = getenv("NLUName", "nlu")
        self.nlu_threshold = 0.7
        self.nlu_not_classified = getenv("NLUNC", "rasa/training-nc.md")

        # Entities
        self.entity_file = getenv("EntityFile", "rasa/training-entities.json")

        # TTS
        self.tts_region = getenv("TTSRegion", "westeurope")
        self.tts_key = environ["TTSKey"]
        self.tts_resource = environ["TTSResource"]

        # Discord
        self.ttl = 10.0
        self.token = environ["DiscordToken"]
