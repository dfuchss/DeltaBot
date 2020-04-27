from asyncio import iscoroutine
from enum import Enum
from typing import List, Callable

from discord import Message

from cognitive import IntentResult, EntityResult
from deltabot import DeltaBot
from misc import is_direct


class DialogResult(Enum):
    NEXT = 0
    WAIT_FOR_INPUT = 1


class Dialog:
    StepType = Callable[[Message, List[IntentResult], List[EntityResult]], DialogResult]

    def __init__(self, bot: DeltaBot, dialog_id: str):
        self._steps: List[Dialog.StepType] = []
        self._next = 0
        self._bot = bot
        self.dialog_id = dialog_id
        self._load_initial_steps()

    def _load_initial_steps(self):
        pass

    async def proceed(self, message: Message, intents: List[IntentResult], entities: List[EntityResult]) -> DialogResult:
        while len(self._steps) > self._next:
            res = self._steps[self._next](message, intents, entities)
            if iscoroutine(res):
                res = await res

            self._next += 1
            if res == DialogResult.WAIT_FOR_INPUT:
                return DialogResult.WAIT_FOR_INPUT

        self.reset()
        return DialogResult.NEXT

    def add_step(self, step: StepType):
        self._steps.append(step)

    def reset(self):
        self._steps.clear()
        self._next = 0
        self._load_initial_steps()

    @staticmethod
    def enhance(response: str, reference: Message) -> str:
        """ Enhances the response by information like user, channel etc.
        :param response the message to enhance
        :param reference the reference message (from the user)
        :return the new response
        """
        result = response.replace("#USER", reference.author.name)
        if is_direct(reference):
            channel_name = f"@{reference.author.name}"
        else:
            channel_name = f"@{reference.channel.name}"
        result = result.replace("#CHANNEL", channel_name)
        return result
