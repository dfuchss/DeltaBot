from threading import Lock

from discord import Game, Status, User

from dialogs import *
from misc import delete, is_direct, cleanup, BotBase
from system_commands import handle_system
from user_commands import handle_user


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
            QnAAnswer(self._bot),
            Random(self._bot)
        ]

        self._intent_to_dialog = {
            "None".lower(): NotUnderstanding.ID,
            "QnA".lower(): QnA.ID,
            "Clock".lower(): Clock.ID,
            "Debug".lower(): Debug.ID,
            "News".lower(): News.ID,
            "Shutdown".lower(): Shutdown.ID,
            "Cleanup".lower(): Cleanup.ID,
            "Answer".lower(): QnAAnswer.ID,
            "Choose".lower(): Random.ID
        }

    def __lookup_dialog(self, dialog_id: str):
        return next((d for d in self._dialogs if d.dialog_id == dialog_id), None)

    async def handle(self, message: Message):
        (intents, entities) = self._bot.nlu.recognize(cleanup(message.content, self._bot))

        await self._bot.print_intents_entities(message, intents, entities)

        if len(self.__active_dialog_stack) != 0:
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
            await send(message.author, message.channel, self._bot, "Dialog nicht gefunden. Bitte an Botadmin wenden!")
            return

        result = await dialog.proceed(message, intents, entities)
        if result == DialogResult.WAIT_FOR_INPUT:
            self.__active_dialog_stack.insert(0, dialog.dialog_id)

    def has_active_dialog(self):
        return len(self.__active_dialog_stack) != 0


class DeltaBot(BotBase):
    """ The DeltaBot main client. """

    def __init__(self) -> None:
        """ Initialize the DeltaBot. """
        super().__init__()
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

    async def on_message(self, message: Message) -> None:
        """Handle a new message.
        :param message: the discord.Message
        """

        # don't respond to ourselves
        if message.author == self.user:
            return

        if await handle_system(self, message):
            return

        if await handle_user(self, message):
            return

        instance = self.__get_bot_instance(message.author)

        if not (is_direct(message) or ((self.user in message.mentions or self.config.respond_all) and message.channel.id in self.channels) or instance.has_active_dialog()):
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
