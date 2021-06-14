from json import loads
from random import choice
from typing import List

from discord import Message

from bot_base import BotBase, send
from cognitive import IntentResult, EntityResult
from dialog_management import Dialog, DialogResult


class NotUnderstanding(Dialog):
    """This dialog handles all inputs that have not been understood by the bot."""

    ID = "NotUnderstanding"
    """The ID of the Dialog"""

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
