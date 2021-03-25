from asyncio import iscoroutine
from typing import Union, Callable, Awaitable, Optional

from discord import Message, VoiceChannel

from misc import delete, send, BotBase

from random import randint, shuffle

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


def __read_number_param(text, default):
    val = default
    split = text.strip().split(" ")
    if len(split) == 2:
        try:
            val = int(split[1])
            if val < 1:
                val = default
        except Exception:
            pass
    return val


async def __roll(message, self):
    text: str = message.content
    dice = __read_number_param(text, 6)
    rnd = randint(1, dice)
    await send(message.author, message.channel, self, f"{rnd}")


async def __teams(message, self):
    text: str = message.content
    num = __read_number_param(text, 2)

    channel: Optional[VoiceChannel] = None
    try:
        channel = message.author.voice.channel
    except Exception:
        pass

    if channel is None:
        await send(message.author, message.channel, self, "Ich finde keinen Voice Channel")
        return

    members = [await self.fetch_user(member) for member in channel.voice_states.keys()]
    members = list(map(lambda n: n.mention, filter(lambda m: m is not None, members)))
    shuffle(members)

    groups = {}
    for i in range(0, num):
        groups[i] = []

    i = 0
    for e in members:
        groups[i].append(e)
        i = (i + 1) % num

    teams = ""
    for t in groups.keys():
        teams = teams + f"{t + 1}: {groups[t]}\n"

    await send(message.author, message.channel, self, f"Zuordnung:\n{teams.strip()}", False)


async def handle_user(self: BotBase, message: Message) -> bool:
    if await __handling_template(self, "roll", message,
                                 lambda: __roll(message, self),
                                 ):
        return True

    if await __handling_template(self, "teams", message, lambda: __teams(message, self)):
        return True

    if await __handling_template(self, "", message,
                                 lambda: send(message.author, message.channel, self, "Unbekannter Befehl")):
        return True

    return False
