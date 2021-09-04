from datetime import datetime
from typing import List

from discord import Message

from bot_base import BotBase, send, dialog_meta
from cognitive import IntentResult, EntityResult
from dialog_management import Dialog, DialogResult


@dialog_meta(dialog_info="Ich kann Dir die Uhrzeit sagen")
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
