from os import getenv
from typing import List

from discord import Message, User

from loadable import Loadable


class Configuration(Loadable):
    """The configuration of the DeltaBot"""

    def __init__(self):
        super().__init__(getenv("CONF_FILE", "./config.json"), version=3)

        self.system_command_symbol = "\\"
        """The symbol indicator for system commands"""

        self.user_command_symbol = "/"
        """The symbol indicator for user commands"""

        self.nlu_url = "http://localhost:5005"
        """The url of the RASA service"""

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

        self._respond_to_unknown_commands = False
        """Indicator whether the bot shall react to unknown commands"""

        self._disable_nlu = False
        """Indicator whether the NLU Unit shall be disabled or not"""

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

    def is_respond_to_unknown_commands(self) -> bool:
        """
        Returns indicator whether the bot shall react to unknown commands

        :return: the indicator
        """
        return self._respond_to_unknown_commands

    def is_nlu_disabled(self) -> bool:
        """
        Returns indicator whether the NLU Unit of the bot shall be disabled

        :return: the indicator
        """
        return self._disable_nlu

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

    def _migrate(self, loaded) -> None:
        print(f"Config is at version {self.version}")

        if loaded["version"] is None:
            print("Migration not possible. No version found.")
            return

        version = loaded["version"]

        if version == 1:
            version = 2
            # Push to V2
            for attr in loaded.keys():
                if not attr.startswith("__") and hasattr(self, attr):
                    setattr(self, attr, loaded[attr])
            self.version = 2
            self.entity_file = "rasa/entities.json"
            self._store()
            print("Configuration: version changed from 1 to 2")

        if version == 2:
            version = 3
            for attr in loaded.keys():
                if not attr.startswith("__") and hasattr(self, attr):
                    setattr(self, attr, loaded[attr])
            self.version = 3
            self._store()
            print("Configuration: version changed from 2 to 3")
            return

        print("Migration not possible. No migration profile available")
