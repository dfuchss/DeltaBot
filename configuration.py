from os import getenv
from typing import List

from discord import Message, User

from loadable import Loadable


class Configuration(Loadable):
    """The configuration of the DeltaBot"""

    def __init__(self):
        super().__init__(getenv("CONF_FILE", "./config.json"), version=2)

        self.nlu_dir = "rasa/models"
        """The directory of the RASA models"""

        self.nlu_name = "nlu"
        """The name of the RASA NLU"""

        self.nlu_threshold = 0.7
        """The RASA classification threshold for intents"""

        self.nlu_not_classified = "rasa/training-nc.md"
        """The file to store not classified sentences"""

        self.entity_file = "rasa/entities.json"
        """The file that contains the entity definitions"""

        self.ttl = 10.0
        """The time to live for a message that gonna be deleted"""

        self._channels = []
        """All text channel ids with enabled dialog system"""

        self._admins = []
        """All admin user ids"""

        self._debug_indicator = False
        """Indicator whether debug mode is enabled or not"""

        self._respond_all_indicator = False
        """Indicator whether the bot shall interpret all messages originates from _channels or only iff mentioned"""

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
        """
        Check whether debug is on.

        :return: the indicator
        """
        return self._debug_indicator

    def is_respond_all(self) -> bool:
        """
        Check whether respond all is on.

        :return: the indicator
        """
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
        """
        Toggle the debug flag

        :return: the new value of the flag
        """
        self._debug_indicator = not self._debug_indicator
        self._store()
        return self._debug_indicator

    def toggle_respond_all(self) -> bool:
        """
        Toggle the respond all flag

        :return: the new value of the flag
        """
        self._respond_all_indicator = not self._respond_all_indicator
        self._store()
        return self._respond_all_indicator

    def _migrate(self, loaded) -> None:
        print(f"Config is at version {self.version}")

        if loaded["version"] is None:
            print("Migration not possible. No version found.")
            return

        if loaded["version"] == 1:
            # Push to V2
            for attr in loaded.keys():
                if not attr.startswith("__") and hasattr(self, attr):
                    setattr(self, attr, loaded[attr])
            self.version = 2
            self.entity_file = "rasa/entities.json"
            self._store()
            print("Configuration: version changed from 1 to 2")
            return

        print("Migration not possible. No migration profile available")
