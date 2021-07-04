import functools
from datetime import datetime, timedelta
from typing import List, Union, Tuple, Dict, Callable, Any

from discord import ChannelType, Message, NotFound, Client, TextChannel, User, DMChannel, Embed

from cognitive import NLUService
from configuration import Configuration
from loadable import Loadable
from scheduler import BotScheduler

__registered_commands: Dict[Tuple[str, bool], str] = {}
"""All registered commands with their documentation as (cmd_name, is_system_command) -> help_msg"""
__registered_subcommands: Dict[str, Dict[str, str]] = {}
"""All registered subcommands commands with their documentation as cmd_name -> (subcommand_name -> help_msg)"""


def __register_command(method: Callable, help_msg: str, is_system_command: bool, name: str, params: List[str],
                       subcommands: Dict[str, str]) -> None:
    """
    Register a system or user command with its documentation.

    :param method: the method
    :param help_msg: the help message
    :param is_system_command: indicator whether system or user command
    :param name: the name of the command (if none it will be extracted from the __method)
    :param params: a list of parameters for the command
    :param subcommands: a dictionary from subcommand to description
    """
    if name is None:
        assert method.__name__.startswith("__")
        name = method.__name__[2:].replace("_", "-")
    if params is None:
        params = []
    if subcommands is None:
        subcommands = {}

    cmd_name = f"{name} {' '.join(params)}".strip()
    __registered_commands[(cmd_name, is_system_command)] = help_msg.strip()
    __registered_subcommands[cmd_name] = subcommands


def command_meta(help_msg: str = None, is_system_command: bool = False, name: str = None,
                 params: List[str] = None, subcommands: Dict[str, str] = None) -> Callable:
    """
    Wrapper that decorates an existing command function and registers it to the documentation.

    :param help_msg: the (mandatory) help message for the command
    :param is_system_command: indicator whether system command or user command
    :param name: overrides a computed name for the command
    :param params: a list of names of parameters of the command
    :param subcommands: a dictionary from subcommand to description
    :return: the decorated function (same behavior as before)
    """

    def decorator(func):
        if help_msg is not None:
            __register_command(func, help_msg, is_system_command, name, params, subcommands)

        @functools.wraps(func)
        def wrapper(*args, **kwargs):
            return func(*args, **kwargs)

        return wrapper

    return decorator


__registered_dialogs: Dict[str, List[str]] = {}
"""All registered dialogs with its documentation as dialog_name -> dialog_info"""


def __register_dialog(cls, dialog_info: List[str], name: str) -> None:
    """
    Register a dialog with its documentation.

    :param cls: the class
    :param dialog_info: the dialog information (multiple strings possible)
    :param name: the name of the command (if none it will be extracted from the cls)
    """

    if isinstance(dialog_info, str):
        dialog_info = [dialog_info]

    if name is None:
        name = cls.__name__

    __registered_dialogs[name] = [s.strip() for s in dialog_info]


def dialog_meta(dialog_info: Union[str, List[str]] = None, name: str = None):
    """
    Wrapper that decorates an existing dialog class and registers it to the documentation.

    :param dialog_info: the (mandatory) help message for the command
    :param name: overrides a computed name for the command
    :return: the decorated class (same behavior as before)
    """

    def decorator(cls):
        if dialog_info is not None:
            __register_dialog(cls, dialog_info, name)

        @functools.wraps(cls)
        def wrapper(*args, **kwargs):
            return cls(*args, **kwargs)

        return wrapper

    return decorator


class DeletionState(Loadable):
    """
    This state contains the scheduled deletions
    """

    def __init__(self):
        super().__init__(path="./states/delete_state.json", version=1)
        self._deletions = []
        self._load()

    def add_deletion(self, deletion: dict) -> None:
        """
        Add a new deletion.

        :param deletion: the data as dictionary (see delete)
        """
        self._deletions.append(deletion)
        self._store()

    def remove_deletion(self, deletion: dict) -> None:
        """
        Remove a new deletion.

        :param deletion: the data as dictionary (see delete)
        """
        self._deletions.remove(deletion)
        self._store()

    def deletions(self) -> List[dict]:
        """
        Return all updates as list of dictionaries

        :return: all updates
        """
        return self._deletions


_deletion_state = DeletionState()
"""The one and only deletion state"""


async def _execute_deletion(bot: Client, deletion: dict):
    """Execute deletion of a message"""
    _deletion_state.remove_deletion(deletion)

    try:
        ch: TextChannel = await bot.fetch_channel(deletion["cid"])
        msg: Message = await ch.fetch_message(deletion["mid"])
        await msg.delete()
    except NotFound:
        pass


class BotBase(Client):
    """The base class for the bot."""

    def __init__(self):
        super().__init__()
        self.config: Configuration = Configuration()
        # Load NLU Lazy ..
        self.nlu: Union[NLUService, Callable] = lambda: NLUService(self.config)
        self.scheduler: BotScheduler = BotScheduler(self.loop)
        self._init_deletions()

    def __getattribute__(self, name: str):
        attr = super(BotBase, self).__getattribute__(name)
        if name == "nlu" and callable(attr):
            nlu = self.nlu = attr()
            return nlu
        return attr

    @staticmethod
    def log(message: Message) -> None:
        """
        Log a message to the console.

        :param message: the message to be logged
        """
        print(f"{datetime.now()} => {message.author}[{message.channel}]: {message.content}")

    async def shutdown(self) -> None:
        """Shutdown the bot"""
        await self.close()

    def _init_deletions(self):
        """Init scheduled deletions"""
        for deletion in _deletion_state.deletions():
            self.scheduler.queue(_execute_deletion(self, deletion), deletion["ts"])


async def send(recipient: User, channel: Union[DMChannel, TextChannel], bot: BotBase, message: Any,
               mention: bool = True) -> Message:
    """
    Send a message to a channel.

    :param recipient: the user which has started the conversation
    :param channel: the target channel for sending the message
    :param bot: the bot itself
    :param message: the message to send
    :param mention: indicator for mentioning the respondee
    :return the sent message
    """

    if mention:
        msg = await channel.send(f"{recipient.mention} {message}")
    else:
        msg = await channel.send(message)
    return msg


async def delete(message: Message, bot: BotBase, delay: float = None) -> None:
    """
    Delete a message.

    :param message the actual message
    :param bot the actual bot
    :param delay some delay in seconds
    """
    if is_direct(message):
        return

    if delay is None:
        try:
            await message.delete()
        except NotFound:
            pass
    else:
        # Schedule deletion
        ts = (datetime.now() + timedelta(seconds=delay)).timestamp()
        deletion = {
            "ts": ts,
            "cid": message.channel.id,
            "mid": message.id
        }
        _deletion_state.add_deletion(deletion)
        bot.scheduler.queue(_execute_deletion(bot, deletion), deletion["ts"])


def is_direct(message: Message) -> bool:
    """ Indicates whether a message was sent via a DM Channel

    :param message the message
    :return the indicator
    """
    return message.channel.type == ChannelType.private


def _gen_message_for_commands(command_type: str, is_system_command: bool, bot: BotBase) -> str:
    symbol = bot.config.system_command_symbol if is_system_command else bot.config.user_command_symbol
    response = f"*Folgende {command_type} unterstütze ich:*\n\n"
    for (name, sys_command) in sorted(__registered_commands.keys(), key=lambda name_x_type: name_x_type[0]):
        if sys_command == is_system_command:
            response += f"**{symbol}{name}**\n" + __registered_commands[(name, sys_command)] + "\n"
            for subcommand in __registered_subcommands[name]:
                response += f"→ **{subcommand}**: " + __registered_subcommands[name][subcommand] + "\n"
            response += "\n"
    return response


async def send_help_message(message: Message, bot: BotBase, timeout: bool = True) -> None:
    """
    Send a help message to the author of message (and to the channel of message)

    :param message: the message to identify author and channel
    :param bot: the bot itself
    :param timeout indicator whether the message shall be deleted after some time
    """

    response = f"Ich kann verschiedene Aufgaben erledigen. Um mich anzusprechen, schreib {bot.user.mention}:\n\n"
    # Dialogs
    dialog_infos: List[str] = [item for sublist in
                               ([__registered_dialogs[name] for name in __registered_dialogs.keys()]) for item in
                               sublist]

    for task in sorted(dialog_infos):
        response += f"* {task}\n"

    # User Commands:
    response += f"\n\n{_gen_message_for_commands('Befehle', False, bot)}"

    # System Commands:
    if timeout and bot.config.is_admin(message.author):
        response += f"\n{_gen_message_for_commands('System-Befehle', True, bot)}"

    ch: TextChannel = message.channel
    embed = Embed(title=f"{bot.user.display_name} Hilfe", description=response.strip(), color=0x0D0A33)
    response_msg = await ch.send(embed=embed)
    if timeout:
        await delete(response_msg, bot, delay=60)
