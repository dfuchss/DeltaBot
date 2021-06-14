from typing import List

from discord import Message

from cognitive import IntentResult, EntityResult
from bot_base import BotBase
from dialog_management import Dialog, DialogResult
from bot_base import send
from datetime import datetime


class Clock(Dialog):
    """This dialog simply sends the time"""

    ID = "Clock"
    """The ID of the Dialog"""

    def __init__(self, bot: BotBase):
        super().__init__(bot, Clock.ID)

    def _load_initial_steps(self):
        self.add_step(self._time_step)

    async def _time_step(self, message: Message, intents: List[IntentResult],
                         entities: List[EntityResult]) -> DialogResult:
        await send(message.author, message.channel, self._bot, f"{datetime.now()}")
        return DialogResult.NEXT
