from json import loads
from random import choice
from typing import List

from discord import Message

from cognitive import IntentResult, EntityResult
from misc import BotBase
from dialog_management import Dialog, DialogResult
from misc import send, send_help_message


class NotUnderstanding(Dialog):
    ID = "NotUnderstanding"

    def __init__(self, bot: BotBase):
        super().__init__(bot, NotUnderstanding.ID)

    def _load_initial_steps(self):
        self.add_step(self._not_understood_step)

    async def _not_understood_step(self, message: Message, intents: List[IntentResult],
                                   entities: List[EntityResult]) -> DialogResult:
        await send_help_message(message, self._bot)
        return DialogResult.NEXT
