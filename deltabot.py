from typing import Optional, Union, Callable, Awaitable
from inspect import iscoroutine

from discord import Client, Game, Status, User

from handler import *
from handler import NewsHandler, DebugHandler, ShutdownHandler, LoLHandler, ClockHandler, NoneHandler, QnAHandler
from misc import Bot, cleanup, delete, is_direct, send


class DeltaBot(Client, Bot):
    """ The DeltaBot main client. """

    channels: List[int] = []

    nluHandler: Dict[str, Handler] = {
        "None".lower(): NoneHandler(),
        "QnA".lower(): QnAHandler(),
        "Clock".lower(): ClockHandler(),
        "Debug".lower(): DebugHandler(),
        "News".lower(): NewsHandler(),
        "LoL".lower(): LoLHandler(),
        "Shutdown".lower(): ShutdownHandler()
    }

    def __init__(self) -> None:
        """ Initialize the DeltaBot. """
        Client.__init__(self)
        Bot.__init__(self)
        for channel in self.config.channels:
            self.channels.append(channel)

    async def on_ready(self) -> None:
        """ Will be executed on ready event. """
        print('Logged on as', self.user)
        game = Game("Schreib' mir ..")
        await self.change_presence(status=Status.idle, activity=game)

    @staticmethod
    def is_admin(user: User) -> bool:
        return user.name == "Dominik" and user.discriminator == "0292"

    async def shutdown(self) -> None:
        await self.close()
        await self.logout()

    def lookup_user(self, user_id: int) -> Optional[User]:
        users = list(filter(lambda u: u.id == user_id, self.users))
        if len(users) != 1:
            return None
        return users[0]

    def get_bot_user(self) -> Client:
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

        if await self.__handle_special(message):
            return

        if not is_direct(message) and message.channel.id not in self.channels:
            return

        if not is_direct(message) and self.user not in message.mentions:
            return

        await delete(message, self)
        self.log(message)
        await self.__handle(message)

    __handling_function = Union[Callable[[], None], Callable[[], Awaitable[None]]]

    async def __handling_template(self, cmd: str, message: Message, func_dm: __handling_function, func_not_admin: __handling_function, func: __handling_function):
        if not message.content.startswith(cmd):
            return False

        if is_direct(message):
            run = func_dm()
            if iscoroutine(run):
                await run

            return True

        if not self.is_admin(message.author):
            run = func_not_admin()
            if iscoroutine(run):
                await run

            await delete(message, self)
            return True

        run = func()
        if iscoroutine(run):
            await run

        await delete(message, self)
        return True

    async def __handle_special(self, message: Message) -> bool:
        if await self.__handling_template("\\listen", message,
                                          lambda: send(message.author, message.channel, self, "Ich höre Dich schon!"),
                                          lambda: send(message.author, message.channel, self, "Du bist nicht authorisiert!"),
                                          lambda: self.channels.append(message.channel.id)
                                          ):
            return True

        if await self.__handling_template("\\echo", message,
                                          lambda: message.channel.send(message.content.replace("<", "").replace(">", "")),
                                          lambda: send(message.author, message.channel, self, "Du bist nicht authorisiert!"),
                                          lambda: message.channel.send(message.content.replace("<", "").replace(">", ""))
                                          ):
            return True

        if await self.__handling_template("\\tts", message,
                                          lambda: send(message.author, message.channel, self, f"Sprachausgabe ist jetzt {'an' if self.toggle_tts() else 'aus'}"),
                                          lambda: send(message.author, message.channel, self, "Du bist nicht authorisiert!"),
                                          lambda: send(message.author, message.channel, self, f"TTS ist jetzt: {self.toggle_tts()}")
                                          ):
            return True

        if await self.__handling_template("\\cleanup", message,
                                          lambda: send(message.author, message.channel, self, f"Für DM nicht sinnvoll."),
                                          lambda: send(message.author, message.channel, self, "Du bist nicht authorisiert!"),
                                          lambda: CleanupHandler().handle(self, None, message)
                                          ):
            return True

        if await self.__handling_template("\\answer", message,
                                          lambda: QnAAnswerHandler().handle(self, None, message),
                                          lambda: send(message.author, message.channel, self, "Du bist nicht authorisiert!"),
                                          lambda: QnAAnswerHandler().handle(self, None, message)
                                          ):
            return True

        if await self.__handling_template("\\test", message,
                                          lambda: TestHandler().handle(self, None, message),
                                          lambda: send(message.author, message.channel, self, "Du bist nicht authorisiert!"),
                                          lambda: TestHandler().handle(self, None, message)
                                          ):
            return True

        return False

    async def __handle(self, message: Message):
        (intents, entities) = self._nlu.recognize(cleanup(message.content, self))

        await self.print_intents_entities(message, intents, entities)

        if intents is None or len(intents) == 0:
            await self.nluHandler["none"].handle(self, (intents, entities), message)
            return

        intent = intents[0].name
        score = intents[0].score

        if score <= self.config.nlu_threshold:
            handler = self.nluHandler["none"]
        elif intent.startswith("QnA"):
            handler = self.nluHandler["qna"]
        else:
            handler = self.nluHandler.get(
                intent.lower(), self.nluHandler["none"])
        await handler.handle(self, (intents, entities), message)

    async def print_intents_entities(self, message: Message, intents: List[IntentResult], entities: List[EntityResult]) -> None:
        """ Prints the stats of classification of one message.
        :param message the message
        :param intents the intent result
        :param entities the found entities
        """
        if not self.debug:
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


def main() -> None:
    """The main method of the system."""
    discord = DeltaBot()
    discord.run(discord.config.token)


if __name__ == "__main__":
    main()
