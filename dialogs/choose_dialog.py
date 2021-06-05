from typing import List

from discord import Message

from cognitive import IntentResult, EntityResult
from misc import BotBase
from dialog_management import Dialog, DialogResult
from misc import send
from random import shuffle
from re import split


class Choose(Dialog):
    ID = "Choose"

    def __init__(self, bot: BotBase):
        super().__init__(bot, Choose.ID)
        self._elements = []
        self._num = -1

    def _load_initial_steps(self):
        self.add_step(self._check_state)

    async def _check_state(self, message: Message, intents: List[IntentResult],
                           entities: List[EntityResult]) -> DialogResult:
        if len(self._elements) != 0:
            self.add_step(self._ask_for_use_old_elements)
        else:
            self.add_step(self._ask_for_new_elements)

        return DialogResult.NEXT

    async def _ask_for_use_old_elements(self, message: Message, intents: List[IntentResult],
                                        entities: List[EntityResult]) -> DialogResult:
        await send(message.author, message.channel, self._bot,
                   f"Soll ich die alten Werte nochmal neu zuordnen? : {self._elements}")
        self.add_step(self._check_ask_for_use_old_elements)
        return DialogResult.WAIT_FOR_INPUT

    async def _check_ask_for_use_old_elements(self, message: Message, intents: List[IntentResult],
                                              entities: List[EntityResult]) -> DialogResult:
        if len(intents) == 0 or intents[0].name != "yes":
            self.add_step(self._ask_for_new_elements)
        else:
            self.add_step(self._generate)

        return DialogResult.NEXT

    async def _ask_for_new_elements(self, message: Message, intents: List[IntentResult],
                                    entities: List[EntityResult]) -> DialogResult:
        await send(message.author, message.channel, self._bot, f"Bitte gib die Werte an, die zur Auswahl stehen ..")
        self.add_step(self._update_elements)
        return DialogResult.WAIT_FOR_INPUT

    async def _update_elements(self, message: Message, intents: List[IntentResult],
                               entities: List[EntityResult]) -> DialogResult:
        text: str = str(message.clean_content)
        splits = split("\\s+", text)
        splits = list(filter(lambda x: len(x.strip()) != 0, splits))
        self._elements.clear()
        for val in splits:
            self._elements.append(val)

        self.add_step(self._ask_for_groups)
        return DialogResult.NEXT

    async def _ask_for_groups(self, message: Message, intents: List[IntentResult],
                              entities: List[EntityResult]) -> DialogResult:
        await send(message.author, message.channel, self._bot,
                   f"Bitte gib an, in wie viele Gruppen ich die Menge teilen muss")
        self.add_step(self._check_ask_for_groups)
        return DialogResult.WAIT_FOR_INPUT

    async def _check_ask_for_groups(self, message: Message, intents: List[IntentResult],
                                    entities: List[EntityResult]) -> DialogResult:
        text: str = str(message.clean_content)
        text = text.strip()
        value = -1
        try:
            value = int(text)
        except Exception:
            pass

        if value < 1:
            await send(message.author, message.channel, self._bot, f"Das ist keine gute Zahl")
            self.add_step(self._ask_for_groups)
            return DialogResult.NEXT

        self._num = value
        self.add_step(self._generate)
        return DialogResult.NEXT

    async def _generate(self, message: Message, intents: List[IntentResult],
                        entities: List[EntityResult]) -> DialogResult:
        shuffle(self._elements)

        groups = {}
        for i in range(0, self._num):
            groups[i] = []

        i = 0
        for e in self._elements:
            groups[i].append(e)
            i = (i + 1) % self._num

        await send(message.author, message.channel, self._bot, f"Zuordnung: {groups}")
        return DialogResult.NEXT
