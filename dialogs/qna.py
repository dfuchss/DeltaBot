from json import loads, dumps
from os import listdir
from os.path import exists
from random import choice
from typing import List

from discord import Message

from bot_base import BotBase, send, dialog_meta
from cognitive import IntentResult, EntityResult
from dialog_management import Dialog, DialogResult


@dialog_meta(dialog_info=[
    "Ich kann Witze erzählen",
    "Ich kann grüßen",
    "Ich kann Dir sagen was ich kann :)",
])
class QnA(Dialog):
    """This dialog handles all QuestionAndAnswers form Json Files"""

    ID = "QnA"
    """The ID of the Dialog"""

    def __init__(self, bot: BotBase):
        super().__init__(bot, QnA.ID)

    def _load_initial_steps(self):
        self.add_step(self._qna_step)

    async def _qna_step(self, message: Message, intents: List[IntentResult],
                        entities: List[EntityResult]) -> DialogResult:
        intent = intents[0].name[4:]
        name = f"QnA/{intent}.json"
        if not exists(name):
            await send(message.author, message.channel, self._bot, f"Ich finde keinen Eintrag für {intent}")
            return DialogResult.NEXT

        qna_file = open(name, "r", encoding="utf-8-sig")
        response = loads(qna_file.read().strip())
        qna_file.close()
        response = choice(response)
        response = self.enhance(response, message)
        await send(message.author, message.channel, self._bot, response)

        return DialogResult.NEXT


@dialog_meta(dialog_info="Du kannst neue Antworten einfügen, die ich dann kenne")
class QnAAnswer(Dialog):
    ID = "QnAAnswer"

    def __init__(self, bot: BotBase):
        super().__init__(bot, QnAAnswer.ID)
        self._qna = None
        self._text = None

    def _load_initial_steps(self):
        self.add_step(self._find_qna)

    async def _find_qna(self, message: Message, intents: List[IntentResult],
                        entities: List[EntityResult]) -> DialogResult:
        if not self._bot.config.is_admin(message.author):
            await send(message.author, message.channel, self._bot, f"Dazu hast Du keine Berechtigung")
            return DialogResult.NEXT

        qna_names = sorted(map(lambda f: f.replace(".json", ""), listdir("QnA")))
        resp = "Für welches QnA soll ein neuer Text gespeichert werden?\n```\n"
        for qna in qna_names:
            resp += f"* {qna}\n"
        resp += "```"

        await send(message.author, message.channel, self._bot, resp)
        self.add_step(self._store_qna_ask_text)
        return DialogResult.WAIT_FOR_INPUT

    async def _store_qna_ask_text(self, message: Message, intents: List[IntentResult],
                                  entities: List[EntityResult]) -> DialogResult:
        name = f"QnA/{message.content}.json"
        if not exists(name):
            await send(message.author, message.channel, self._bot, f"Ich finde keinen Eintrag für {name}")
            return DialogResult.NEXT

        self._qna = name
        await send(message.author, message.channel, self._bot, f"Bitte gib jetzt Deinen Text für {name} ein:")
        self.add_step(self._store_text_ask_confirmation)
        return DialogResult.WAIT_FOR_INPUT

    async def _store_text_ask_confirmation(self, message: Message, intents: List[IntentResult],
                                           entities: List[EntityResult]) -> DialogResult:
        self._text = message.content
        await send(message.author, message.channel, self._bot,
                   f"Soll ich in {self._qna} '{self._text}' speichern? (Y/N)")
        self.add_step(self._qna_step)
        return DialogResult.WAIT_FOR_INPUT

    async def _qna_step(self, message: Message, intents: List[IntentResult],
                        entities: List[EntityResult]) -> DialogResult:
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
