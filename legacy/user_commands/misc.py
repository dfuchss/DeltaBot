from random import randint, shuffle
from typing import Optional

from discord import Message, VoiceChannel

from bot_base import send, BotBase, send_help_message, command_meta
from .helpers import __read_number_param


@command_meta(help_msg="Zeigt diese Hilfe an :)")
async def __help(message: Message, bot: BotBase) -> None:
    """
    Send a help message to the user

    :param message: the message from the user
    :param bot: the bot itself
    """
    await send_help_message(message, bot)


async def __help_persist(message: Message, bot: BotBase) -> None:
    # Not documented as this method shall be hidden to the user
    """
    Send a help persistent message to the user

    :param message: the message from the user
    :param bot: the bot itself
    """
    await send_help_message(message, bot, timeout=False)


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


@command_meta(
    help_msg="Ordnet die Leute in Deinem Voice Channel in N Teams (wenn N fehlt, verwende ich als Teamanzahl 2)",
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
