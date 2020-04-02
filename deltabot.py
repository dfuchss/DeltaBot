from threading import Lock
from typing import Optional, Tuple

from discord import Client, Game, Status, User

from cognitive import TextToSpeech, NLUService
from configuration import Configuration
from dialogs import *
from misc import delete, is_direct, cleanup
from system_commands import handle_system


class BotInstance:
    def __init__(self, bot):
        self._bot = bot
        self._dialogs: List[Dialog] = []
        self._intent_to_dialog: Dict[str, str] = {}
        self._load_dialogs()

        self.__active_dialog_stack = []

    def _load_dialogs(self):
        self._dialogs = [
            NotUnderstanding(self._bot),
            QnA(self._bot),
            Clock(self._bot),
            Debug(self._bot),
            News(self._bot),
            Shutdown(self._bot),
            Cleanup(self._bot),
            QnAAnswer(self._bot)
        ]

        self._intent_to_dialog = {
            "None".lower(): NotUnderstanding.ID,
            "QnA".lower(): QnA.ID,
            "Clock".lower(): Clock.ID,
            "Debug".lower(): Debug.ID,
            "News".lower(): News.ID,
            "Shutdown".lower(): Shutdown.ID,
            "Cleanup".lower(): Cleanup.ID
        }

    def __lookup_dialog(self, dialog_id: str):
        return next((d for d in self._dialogs if d.dialog_id == dialog_id), [None])

    @staticmethod
    def _check_special_intents(message: Message) -> Optional[str]:
        if message.content.startswith("\\answer"):
            return QnAAnswer.ID

        return None

    async def handle(self, message: Message):
        (intents, entities) = self._bot.nlu.recognize(cleanup(message.content, self))

        await self.print_intents_entities(message, intents, entities)

        special_intents = self._check_special_intents(message)

        if special_intents is not None:
            dialog = special_intents
        elif len(self.__active_dialog_stack) != 0:
            dialog = self.__active_dialog_stack.pop(0)
        elif intents is None or len(intents) == 0:
            dialog = NotUnderstanding.ID
        else:
            intent = intents[0].name
            score = intents[0].score
            dialog = self._intent_to_dialog.get(intent)

            if score <= self._bot.config.nlu_threshold:
                dialog = NotUnderstanding.ID
            elif intent.startswith("QnA"):
                dialog = QnA.ID

        dialog = self.__lookup_dialog(dialog)
        if dialog is None:
            await send(message.author, message.channel, self, "Dialog nicht gefunden. Bitte an Botadmin wenden!")
            return

        result = await dialog.proceed(message, intents, entities)
        if result == DialogResult.WAIT_FOR_INPUT:
            self.__active_dialog_stack.insert(0, dialog.dialog_id)

    async def print_intents_entities(self, message: Message, intents: List[IntentResult], entities: List[EntityResult]) -> None:
        """ Prints the stats of classification of one message.
        :param message the message
        :param intents the intent result
        :param entities the found entities
        """
        if not self._bot.config.debug_indicator:
            return

        result: str = "------------\n"
        result += f"Intents({len(intents)}):\n"

        for intent in intents:
            result += f"{intent}\n"

        result += f"\nEntities({len(entities)}):\n"
        for entity in entities:
            result += f"{entity}\n"

        result += "------------"

        await send(message.author, message.channel, self, result, mention=False)

    def has_active_dialog(self):
        return len(self.__active_dialog_stack) != 0


class DeltaBot(Client):
    """ The DeltaBot main client. """

    channels: List[int] = []
    admins: List[Tuple[str, str]] = []

    def __init__(self) -> None:
        """ Initialize the DeltaBot. """
        Client.__init__(self)
        self.config = Configuration()
        self.tts = TextToSpeech(self.config)
        self.nlu = NLUService(self.config)

        self._user_to_instance = {}
        self._user_to_instance_lock = Lock()

        for channel in self.config.channels:
            self.channels.append(channel)

        for admin in self.config.admins:
            self.admins.append((admin[0], admin[1]))

    async def on_ready(self) -> None:
        """ Will be executed on ready event. """
        print('Logged on as', self.user)
        game = Game("Schreib' mir ..")
        await self.change_presence(status=Status.idle, activity=game)

    def is_admin(self, user: User) -> bool:
        """
        Check for Admin.
        :param user: the actual user object
        :return: indicator for administrative privileges
        """
        if len(self.admins) == 0:
            return True

        for (name, dsc) in self.admins:
            if user.name == name and user.discriminator == dsc:
                return True
        return False

    def add_admins(self, message: Message):
        for user in message.mentions:
            self.admins.append((user.name, user.discriminator))

    async def shutdown(self) -> None:
        """Shutdown the bot"""
        await self.close()
        await self.logout()

    def lookup_user(self, user_id: int) -> Optional[User]:
        """Find user by id
        :param user_id: the id of the user
        :return the found user object or None
        """
        users = list(filter(lambda u: u.id == user_id, self.users))
        if len(users) != 1:
            return None
        return users[0]

    def get_bot_user(self) -> Client:
        """ Get the Discord User of the Bot.
        :return the Discord User as Client
        """
        return self.user

    @staticmethod
    def log(message: Message):
        """ Log a message to std out.
        :param message the actual message
        """
        print(f"{datetime.now()} => {message.author}[{message.channel}]: {message.content}")

    async def on_message(self, message: Message) -> None:
        """Handle a new message.
        :param message: the discord.Message
        """

        # don't respond to ourselves
        if message.author == self.user:
            return

        if await handle_system(self, message):
            return

        instance = self.__get_bot_instance(message.author)

        if not (is_direct(message) or (self.user in message.mentions and message.channel.id in self.channels) or instance.has_active_dialog()):
            return

        await delete(message, self)
        self.log(message)

        await instance.handle(message)

    def __get_bot_instance(self, author: User) -> BotInstance:
        with self._user_to_instance_lock:
            instance = self._user_to_instance.get(author.id)
            if instance is None:
                instance = BotInstance(self)
                self._user_to_instance[author.id] = instance
        return instance


def main() -> None:
    """The main method of the system."""
    discord = DeltaBot()
    discord.run(discord.config.token)


if __name__ == "__main__":
    main()
