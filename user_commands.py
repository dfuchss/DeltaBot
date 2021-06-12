from asyncio import iscoroutine
from typing import Union, Callable, Awaitable, Optional

from discord import Message, VoiceChannel, TextChannel, RawReactionActionEvent

from misc import delete, send, BotBase, send_help_message

from random import randint, shuffle

from user_command_helpers import __read_number_param
from user_command_reminder import _init_reminders, __reminder
from user_command_summon import __summon, __handling_reaction_summon, _init_summon_updates

USER_COMMAND_SYMBOL = "/"


async def __help(message, self):
    return await send_help_message(message, self)


async def __roll(message, self):
    text: str = message.content
    dice = __read_number_param(text, 6)
    rnd = randint(1, dice)
    await send(message.author, message.channel, self, f"{rnd}")


async def __teams(message: Message, self: BotBase):
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

    await send(message.author, message.channel, self, f"Zuordnung:\n{teams.strip()}", mention=False)


def __unknown(message: Message, self: BotBase):
    return send(message.author, message.channel, self, "Unbekannter Befehl")


HandlingFunction = Union[  #
    Callable[[Message, BotBase], None],  #
    Callable[[Message, BotBase], Awaitable[None]]  #
]


async def __handling_template(self: BotBase, cmd: str, message: Message, func: HandlingFunction):
    cmd = f"{USER_COMMAND_SYMBOL}{cmd}"
    if not message.content.startswith(cmd):
        return False

    run = func(message, self)
    if iscoroutine(run):
        await run

    await delete(message, self)
    return True


commands = [__help, __roll, __teams, __summon, __reminder]
commands.sort(key=lambda m: len(m.__name__), reverse=True)


def init_user_commands(self: BotBase):
    _init_reminders(self)
    _init_summon_updates(self)


async def handle_user(self: BotBase, message: Message) -> bool:
    if not message.clean_content.strip().startswith(USER_COMMAND_SYMBOL):
        return False

    for command in commands:
        name = command.__name__[2:].replace("_", "-")
        if await __handling_template(self, name, message, command):
            return True

    await __handling_template(self, "", message, __unknown)
    return True


async def handle_user_reaction(self: BotBase, payload: RawReactionActionEvent, message: Message, channel: TextChannel):
    if await __handling_reaction_summon(self, payload, message, channel):
        return True

    return False
