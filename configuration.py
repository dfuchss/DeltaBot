"""The configuration of the DeltaBot"""
from json import loads, dumps
from os import getenv
from os.path import exists
from typing import List

from discord import Message, User

from json_objects import convert_to_dict


class Configuration:
    def __init__(self):
        self.version = 1
        self._path = getenv("CONF_FILE", "./config.json")

        self.nlu_dir = "rasa/models"
        self.nlu_name = "nlu"
        self.nlu_threshold = 0.7
        self.nlu_not_classified = "rasa/training-nc.md"
        self.entity_file = "rasa/training-entities.json"

        self.ttl = 10.0
        self._channels = []
        self._admins = []

        self._debug_indicator = False
        self._respond_all_indicator = False
        self._keep_messages_indicator = True

        self._load()

    def is_admin(self, user: User) -> bool:
        """
        Check for Admin.
        :param user: the actual user object
        :return: indicator for administrative privileges
        """
        if len(self._admins) == 0:
            return True

        for uid in self._admins:
            if user.id == uid:
                return True
        return False

    def is_debug(self) -> bool:
        return self._debug_indicator

    def is_keep_messages(self) -> bool:
        return self._keep_messages_indicator

    def is_respond_all(self) -> bool:
        return self._respond_all_indicator

    def get_channels(self) -> List[int]:
        return self._channels

    def get_admins(self) -> List[int]:
        return self._admins

    def add_admins(self, message: Message) -> None:
        if not self.is_admin(message.author):
            return

        for user in message.mentions:
            self._admins.append(user.id)

        self._store()

    def add_channel(self, channel_id) -> None:
        self._channels.append(channel_id)
        self._store()

    def toggle_debug(self) -> bool:
        self._debug_indicator = not self._debug_indicator
        self._store()
        return self._debug_indicator

    def toggle_respond_all(self) -> bool:
        self._respond_all_indicator = not self._respond_all_indicator
        self._store()
        return self._respond_all_indicator

    def toggle_keep_messages(self) -> bool:
        self._keep_messages_indicator = not self._keep_messages_indicator
        self._store()
        return self._keep_messages_indicator

    def _store(self) -> None:
        with open(self._path, "w", encoding="utf-8-sig") as outfile:
            outfile.write(dumps(self, default=convert_to_dict, indent=4))

    def _load(self) -> None:
        if not exists(self._path):
            self._store()
            return
        try:
            with open(self._path, encoding="utf-8-sig") as jsonfile:
                loaded = loads(jsonfile.read())

                if loaded["version"] == self.version:
                    for attr in loaded.keys():
                        if not attr.startswith("__") and hasattr(self, attr):
                            setattr(self, attr, loaded[attr])
                else:
                    self.__migrate(loaded)
        except Exception:
            print("State could not be loaded .. reinitialize")
            self._store()

    def __migrate(self, loaded) -> None:
        print(f"Config is at version {self.version}")

        if loaded["version"] is None:
            print("Migration not possible. No version found.")
            return

        print("Migration not possible. No migration profile available")
