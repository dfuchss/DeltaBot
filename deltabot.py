from typing import Optional

from discord import Client, Game, Status, User

from handler import *
from misc import Bot, cleanup, delete, is_direct, send


class DeltaBot(Client, Bot):
    """ The DeltaBot main client .. """

    channels = [174101906854117376, 369422961821745162]

    nluHandler = {
        "None".lower(): NoneHandler(),
        "QnA".lower(): QnAHandler(),
        "Clock".lower(): ClockHandler(),
        "Debug".lower(): DebugHandler(),
        "News".lower(): NewsHandler(),
        "LoL".lower(): LoLHandler(),
        "Shutdown".lower(): ShutdownHandler()
    }

    def __init__(self) -> None:
        Client.__init__(self)
        Bot.__init__(self)

    async def on_ready(self) -> None:
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
    def log(message):
        print(f"{datetime.now()} => {message.author}[{message.channel}]: {message.content}")

    async def on_message(self, message: Message) -> None:
        """Handle a new message.

        Arguments:
            message {discord.Message} -- the message
        """
        # don't respond to ourselves
        if message.author == self.user:
            return

        if await self._handle_special(message):
            return

        if not is_direct(message) and message.channel.id not in self.channels:
            return

        if not is_direct(message) and self.user not in message.mentions:
            return

        await delete(message, self)
        self.log(message)
        await self._handle(message)

    async def _handle_special(self, message: Message) -> bool:
        if message.content == "\\listen":
            if is_direct(message):
                await send(message.author, message.channel, self, "Ich höre Dich schon!")
                return True

            if not self.is_admin(message.author):
                await send(message.author, message.channel, self, "Du bist nicht authorisiert!")
                await delete(message, self)
                return True

            self.channels.append(message.channel.id)
            await delete(message, self)
            return True

        if message.content.startswith("\\echo"):
            if not self.is_admin(message.author):
                await send(message.author, message.channel, self, "Du bist nicht authorisiert!")
                await delete(message, self)
                return True

            await message.channel.send(message.content.replace("<", "").replace(">", ""))
            await delete(message, self)
            return True

        if message.content.startswith("\\tts"):
            if not self.is_admin(message.author):
                await send(message.author, message.channel, self, "Du bist nicht authorisiert!")
                await delete(message, self)
                return True
            await send(message.author, message.channel, self, f"TTS ist jetzt: {self.toggle_tts()}")
            await delete(message, self)
            return True

        if message.content.startswith("\\cleanup"):
            if not self.is_admin(message.author):
                await send(message.author, message.channel, self, "Du bist nicht authorisiert!")
                await delete(message, self)
                return True
            await CleanupHandler().handle(self, None, message)
            await delete(message, self)
            return True

        if message.content.startswith("\\answer"):
            if not self.is_admin(message.author):
                await send(message.author, message.channel, self, "Du bist nicht authorisiert!")
                await delete(message, self)
                return True

            await QnAAnswerHandler().handle(self, None, message)
            await delete(message, self)

            return True

        if message.content.startswith("\\test"):
            if not self.is_admin(message.author):
                await send(message.author, message.channel, self, "Du bist nicht authorisiert!")
                await delete(message, self)
                return True

            await TestHandler().handle(self, None, message)
            await delete(message, self)

            return True
        return False

    async def _handle(self, message: Message):
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

    async def print_intents_entities(self, message: Message, intents: List[IntentResult],
                                     entities: List[EntityResult]) -> None:

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
