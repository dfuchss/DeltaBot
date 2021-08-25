from asyncio import iscoroutine
from typing import Union, Callable, Awaitable, List

from discord import Message

from bot_base import BotBase, send
from bot_base import delete
from .guild import __guild_manager, __show_guild_managers
from .misc import __help, __help_persist, __roll, __teams
from .reminder import _init_reminders, __reminder
from .roles import __roles, __handling_button_roles
from .summon import __summon, _init_summon_updates, __handling_button_summon

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
    cmd = f"{bot.config.user_command_symbol}{cmd}"
    if not message.content.startswith(cmd):
        return False

    run = func(message, bot)
    if iscoroutine(run):
        await run

    await delete(message, bot)
    return True


async def __unknown(message: Message, bot: BotBase) -> None:
    """
    The default handler for unknown commands.

    :param message: the message from the user
    :param bot: the bot itself

    """
    if not bot.config.is_respond_to_unknown_commands():
        return

    resp = await send(message.author, message.channel, bot, "Unbekannter Befehl")
    await delete(resp, bot, delay=10)


commands = [__help, __help_persist, __roll, __teams, __summon, __reminder, __roles, __guild_manager,
            __show_guild_managers]
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
    if not message.clean_content.strip().startswith(bot.config.user_command_symbol):
        return False

    for command in commands:
        name = command.__name__[2:].replace("_", "-")
        if await __handling_template(bot, name, message, command):
            return True

    await __handling_template(bot, "", message, __unknown)
    return True


async def handle_user_button(bot: BotBase, payload: dict, message: Message, button_id: str, user_id: int) -> bool:
    """
    Handle pressed buttons for user commands.

    :param bot: the bot itself
    :param payload: the raw payload from discord
    :param message: the message which belongs to the button
    :param button_id: the id of the pressed button
    :param user_id: the id of the user who pressed the button
    :return: indicator whether the button was related to a user command
    """
    if await __handling_button_roles(bot, payload, message, button_id, user_id):
        return True

    if await __handling_button_summon(bot, payload, message, button_id, user_id):
        return True

    return False


async def handle_user_selection(bot: BotBase, payload: dict, message: Message, selection_id: str, selections: List[str],
                                user_id: int) -> bool:
    """
    Handle selections for user commands.

    :param bot: the bot itself
    :param payload: the raw payload from discord
    :param message: the message which belongs to the button
    :param selection_id: the id of the selection object
    :param selections the list of selected elements
    :param user_id: the id of the user who pressed the button
    :return: indicator whether the button was related to a user command
    """
    return False
