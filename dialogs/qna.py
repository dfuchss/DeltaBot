from json import loads, dumps
from random import choice

from typing import List
from discord import Message

from cognitive import IntentResult, EntityResult
from misc import BotBase
from dialog_management import Dialog, DialogResult
from misc import send
from genericpath import exists


class QnA(Dialog):
    ID = "QnA"

    def __init__(self, bot: BotBase):
        super().__init__(bot, QnA.ID)

    def _load_initial_steps(self):
        self.add_step(self._qna_step)

    async def _qna_step(self, message: Message, intents: List[IntentResult], entities: List[EntityResult]):
        intent = intents[0].name[4:]
        name = f"QnA/{intent}.json"
        if not exists(name):
            await send(message.author, message.channel, self._bot, f"Ich finde keinen Eintrag für {intent}")
            return

        qna_file = open(name, "r", encoding="utf-8-sig")
        response = loads(qna_file.read().strip())
        qna_file.close()
        response = choice(response)
        response = self.enhance(response, message)
        await send(message.author, message.channel, self._bot, response)

        return DialogResult.NEXT


class QnAAnswer(Dialog):
    ID = "QnAAnswer"

    def __init__(self, bot: BotBase):
        super().__init__(bot, QnAAnswer.ID)
        self._qna = None
        self._text = None

    def _load_initial_steps(self):
        self.add_step(self._find_qna)

    async def _find_qna(self, message: Message, intents: List[IntentResult], entities: List[EntityResult]):
        if not self._bot.config.is_admin(message.author):
            await send(message.author, message.channel, self._bot, f"Dazu hast Du keine Berechtigung")
            return DialogResult.NEXT

        await send(message.author, message.channel, self._bot,
                   f"Für welches QnA soll ein neuer Text gespeichert werden?")
        self.add_step(self._store_qna_ask_text)
        return DialogResult.WAIT_FOR_INPUT

    async def _store_qna_ask_text(self, message: Message, intents: List[IntentResult], entities: List[EntityResult]):
        name = f"QnA/{message.content}.json"
        if not exists(name):
            await send(message.author, message.channel, self._bot, f"Ich finde keinen Eintrag für {name}")
            return DialogResult.NEXT

        self._qna = name
        await send(message.author, message.channel, self._bot, f"Bitte gib jetzt Deinen Text für {name} ein:")
        self.add_step(self._store_text_ask_confirmation)
        return DialogResult.WAIT_FOR_INPUT

    async def _store_text_ask_confirmation(self, message: Message, intents: List[IntentResult],
                                           entities: List[EntityResult]):
        self._text = message.content
        await send(message.author, message.channel, self._bot,
                   f"Soll ich in {self._qna} '{self._text}' speichern? (Y/N)")
        self.add_step(self._qna_step)
        return DialogResult.WAIT_FOR_INPUT

    async def _qna_step(self, message: Message, intents: List[IntentResult], entities: List[EntityResult]):
        if len(intents) == 0 or intents[0].name != "yes":
            await send(message.author, message.channel, self._bot, f"OK. Keine Änderung vorgenommen!")
            return DialogResult.NEXT

        qna_file = open(self._qna, "r", encoding="utf-8-sig")
        response: list = loads(qna_file.read().strip())
        qna_file.close()

        response.insert(0, self._text)

        qna_file = open(self._qna, "w", encoding="utf-8-sig")
        qna_file.write(dumps(response, indent=True))
        qna_file.close()

        await send(message.author, message.channel, self._bot, f"Habs hinzugefügt!")
        return DialogResult.NEXT

    def reset(self):
        super().reset()
        self._qna = None
        self._text = None
