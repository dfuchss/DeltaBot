from os import environ
from threading import Lock
from typing import Dict

from discord import Status, User, Activity, ActivityType, RawReactionActionEvent, TextChannel

from dialogs.generic_dialogs import *
from dialogs.misc_dialogs import *
from dialogs.admin_dialogs import *
from dialogs.news_dialog import News
from dialogs.qna import *
from dialogs.choose_dialog import Choose
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
            Choose(self._bot)
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
            "Choose".lower(): Choose.ID
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

    async def on_ready(self) -> None:
        """ Will be executed on ready event. """
        print('Logged on as', self.user)
        activity = Activity(type=ActivityType.watching, name="fuchss.org/L/DeltaBot")
        await self.change_presence(status=Status.online, activity=activity)

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
        ch_id = message.channel.id

        handle_message: bool = False
        handle_message |= is_direct(message)
        handle_message |= (self.user in message.mentions or self.config.is_respond_all()) and ch_id in self.config.get_channels()
        handle_message |= instance.has_active_dialog()

        if not handle_message:
            return

        await delete(message, self)
        self.log(message)

        await instance.handle(message)

    async def on_raw_reaction_add(self, payload):
        if not type(payload) is RawReactionActionEvent:
            return

        pl: RawReactionActionEvent = payload
        if pl.event_type != "REACTION_ADD" or pl.user_id == self.user.id:
            return

        channel: TextChannel = await self.fetch_channel(pl.channel_id)
        message: Message = await channel.fetch_message(pl.message_id)

        if message.author != self.user:
            return

        # Check whether the reactions a restricted by me ..
        restricted = any(map(lambda r: r.me, message.reactions))
        if not restricted:
            return

        user: User = await self.fetch_user(pl.user_id)
        for reaction in message.reactions:
            if not reaction.me:
                await reaction.remove(user)

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
    discord.run(environ["DiscordToken"])


if __name__ == "__main__":
    main()
