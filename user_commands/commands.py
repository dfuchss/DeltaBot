from asyncio import iscoroutine
from random import randint, shuffle
from typing import Union, Callable, Awaitable, Optional

from discord import Message, VoiceChannel, RawReactionActionEvent

from bot_base import delete, send, BotBase, send_help_message, command_meta
from constants import USER_COMMAND_SYMBOL
from .helpers import __read_number_param
from .reminder import _init_reminders, __reminder
from .summon import __summon, __handling_reaction_summon, _init_summon_updates


@command_meta(help_msg="Zeigt diese Hilfe an :)")
async def __help(message: Message, bot: BotBase) -> None:
    """
    Send a help message to the user

    :param message: the message from the user
    :param bot: the bot itself
    """
    await send_help_message(message, bot)


@command_meta(help_msg="Würfelt eine Zahl zwischen 1 und N (wenn N fehlt, verwende ich 6)", params=["N"])
async def __roll(message, bot) -> None:
    """
    Roll a dice. The method tries to parse an int argument of the command. If no int found the dice defaults to 6 sides

    :param message: the message from the user
    :param bot: the bot itself
    """
    text: str = message.content
    dice = __read_number_param(text, 6)
    rnd = randint(1, dice)
    await send(message.author, message.channel, bot, f"{rnd}")


@command_meta(help_msg="Ordnet die Leute in Deinem Voice Channel in N Teams (wenn N fehlt, verwende ich 2)",
              params=["N"])
async def __teams(message: Message, bot: BotBase) -> None:
    """
    Create teams based on your voice channel's members. The method tries to parse an int argument of the command.
    If no int found the dice defaults to 2 teams

    :param message: the message from the user
    :param bot: the bot itself
    """
    text: str = message.content
    num = __read_number_param(text, 2)

    channel: Optional[VoiceChannel] = None
    try:
        channel = message.author.voice.channel
    except Exception:
        pass

    if channel is None:
        await send(message.author, message.channel, bot, "Ich finde keinen Voice Channel")
        return

    members = [await bot.fetch_user(member) for member in channel.voice_states.keys()]
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

    await send(message.author, message.channel, bot, f"Zuordnung:\n{teams.strip()}", mention=False)


def __unknown(message: Message, bot: BotBase) -> Awaitable[None]:
    """
    The default handler for unknown commands.

    :param message: the message from the user
    :param bot: the bot itself
    :return: the send() Awaitable
    """
    return send(message.author, message.channel, bot, "Unbekannter Befehl")


HandlingFunction = Union[  #
    Callable[[Message, BotBase], None],  #
    Callable[[Message, BotBase], Awaitable[None]]  #
]


async def __handling_template(bot: BotBase, cmd: str, message: Message, func: HandlingFunction) -> bool:
    """
    Template method that handles a command based on a HandlingFunction

    :param bot: the bot itself
    :param cmd: the command to be executed (e.g. "help")
    :param message: the message from the user
    :param func: the handling function that shall be executed if the command shall be executed
    :return: indicator whether the command has been executed
    """
    cmd = f"{USER_COMMAND_SYMBOL}{cmd}"
    if not message.content.startswith(cmd):
        return False

    run = func(message, bot)
    if iscoroutine(run):
        await run

    await delete(message, bot)
    return True


commands = [__help, __roll, __teams, __summon, __reminder]
"""
All Registered Commands
"""
commands.sort(key=lambda m: len(m.__name__), reverse=True)


def init_user_commands(bot: BotBase) -> None:
    """
    Initialize the scheduler for user commands

    :param bot: the bot itself
    """
    _init_reminders(bot)
    _init_summon_updates(bot)


async def handle_user(bot: BotBase, message: Message) -> bool:
    """
    Handle user commands.

    :param bot: the bot itself
    :param message: the message from the user
    :return: indicator whether a command has been executed
    """
    if not message.clean_content.strip().startswith(USER_COMMAND_SYMBOL):
        return False

    for command in commands:
        name = command.__name__[2:].replace("_", "-")
        if await __handling_template(bot, name, message, command):
            return True

    await __handling_template(bot, "", message, __unknown)
    return True


async def handle_user_reaction(bot: BotBase, payload: RawReactionActionEvent, message: Message) -> bool:
    """
    Handle reactions of users to responses for user commands.

    :param bot: the bot itself
    :param payload: the raw payload for the added reaction
    :param message: the message the user reacted to
    :return: indicator whether the reaction was handled by the user command handlers
    """
    if await __handling_reaction_summon(bot, payload, message):
        return True

    return False
