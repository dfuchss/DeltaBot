from asyncio import iscoroutine
from enum import Enum
from typing import List, Callable

from discord import Message

from cognitive import IntentResult, EntityResult
from bot_base import is_direct, BotBase


class DialogResult(Enum):
    """All different results of a dialog execution"""

    NEXT = 0
    """Indicates that the next step can be executed or the dialog has been finished"""

    WAIT_FOR_INPUT = 1
    """Indicates that the system needs user input"""


class Dialog:
    """The definition of an abstract dialog"""

    StepType = Callable[[Message, List[IntentResult], List[EntityResult]], DialogResult]
    """The type of a step. A dialog consists of dialog steps"""

    def __init__(self, bot: BotBase, dialog_id: str):
        """
        Create a new dialog.

        :param bot: the bot itself
        :param dialog_id: the dialog's id
        """

        self.dialog_id = dialog_id
        """The id of the dialog"""

        self._steps: List[Dialog.StepType] = []
        self._next = 0
        self._bot = bot
        self._load_initial_steps()

    def _load_initial_steps(self) -> None:
        """
        Load all initial steps to an empty _steps field.
        """
        pass

    async def proceed(self, message: Message, intents: List[IntentResult],
                      entities: List[EntityResult]) -> DialogResult:
        """
        Execute / Proceed the dialog.

        :param message: the current message for the dialog
        :param intents: the detected intents
        :param entities: the detected entities
        :return: the result of the current execution
        """
        while len(self._steps) > self._next:
            res = self._steps[self._next](message, intents, entities)
            if iscoroutine(res):
                res = await res

            self._next += 1
            if res == DialogResult.WAIT_FOR_INPUT:
                return DialogResult.WAIT_FOR_INPUT

        self.reset()
        return DialogResult.NEXT

    def add_step(self, step: StepType) -> None:
        """
        Add a new step to the dialog's step queue.

        :param step: the step
        """
        self._steps.append(step)

    def reset(self):
        """
        Reset the current dialog state.
        """
        self._steps.clear()
        self._next = 0
        self._load_initial_steps()

    @staticmethod
    def enhance(response: str, reference: Message) -> str:
        """
        Enhances the response by information like user, channel etc.

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
