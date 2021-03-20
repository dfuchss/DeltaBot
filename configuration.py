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

        # Discord
        self.ttl = 10.0
        self.token = environ["DiscordToken"]

        # Load accepted channels
        env_channels_str = getenv("Channels", "")
        env_channels = env_channels_str.split(';')
        self.channels = map(int, filter(None, env_channels))

        # Load admin users
        env_admin_str = getenv("Admins", "")
        env_admins = env_admin_str.split(';')
        self.admins = map(lambda name: name.split(','), filter(None, env_admins))

        # Indicators
        self.debug_indicator = False
        self.respond_all = False
        self.keep_messages = True

    def toggle_debug(self):
        self.debug_indicator = not self.debug_indicator
        return self.debug_indicator

    def toggle_respond_all(self):
        self.respond_all = not self.respond_all
        return self.respond_all

    def toggle_keep_messages(self):
        self.keep_messages = not self.keep_messages
        return self.keep_messages
