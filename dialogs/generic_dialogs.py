from json import loads
from random import choice
from typing import List

from discord import Message

from cognitive import IntentResult, EntityResult
from misc import BotBase
from dialog_management import Dialog, DialogResult
from misc import send


class NotUnderstanding(Dialog):
    ID = "NotUnderstanding"

    def __init__(self, bot: BotBase):
        super().__init__(bot, NotUnderstanding.ID)

    def _load_initial_steps(self):
        self.add_step(self._not_understood_step)

    async def _not_understood_step(self, message: Message, intents: List[IntentResult], entities: List[EntityResult]):
        responses = open("QnA/NotUnderstanding.json", "r", encoding="utf-8-sig")
        response = loads(responses.read().strip())
        responses.close()
        response = choice(response)
        response = self.enhance(response, message)
        await send(message.author, message.channel, self._bot, response)
        return DialogResult.NEXT


class Debug(Dialog):
    ID = "Debug"

    def __init__(self, bot: BotBase):
        super().__init__(bot, Debug.ID)

    def _load_initial_steps(self):
        self.add_step(self._toggle_debug_step)

    async def _toggle_debug_step(self, message: Message, intents: List[IntentResult], entities: List[EntityResult]):
        await send(message.author, message.channel, self._bot,
                   f"Entwicklermodus ist jetzt: {self._bot.config.toggle_debug()}")
        return DialogResult.NEXT
