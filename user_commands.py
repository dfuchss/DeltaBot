from asyncio import iscoroutine
from typing import Union, Callable, Awaitable

from discord import Message

from misc import delete, send, BotBase

from random import randint

HandlingFunction = Union[Callable[[], None], Callable[[], Awaitable[None]]]

USER_COMMAND_SYMBOL = "/"


async def __handling_template(self: BotBase, cmd: str, message: Message, func: HandlingFunction):
    cmd = f"{USER_COMMAND_SYMBOL}{cmd}"
    if not message.content.startswith(cmd):
        return False

    run = func()
    if iscoroutine(run):
        await run

    await delete(message, self)
    return True


async def __roll(message, self):
    text: str = message.content
    split = text.strip().split(" ")
    dice = 6
    if len(split) == 2:
        try:
            dice = int(split[1])
            if dice < 1:
                dice = 6
        except Exception:
            pass

    rnd = randint(1, dice)
    await send(message.author, message.channel, self, f"{rnd}")


async def handle_user(self: BotBase, message: Message) -> bool:
    if await __handling_template(self, "roll", message,
                                 lambda: __roll(message, self),
                                 ):
        return True

    if await __handling_template(self, "", message,
                                 lambda: send(message.author, message.channel, self, "Unbekannter Befehl")):
        return True

    return False
