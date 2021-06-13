import functools
from typing import List, Optional, Union, Any, Tuple, Dict

from discord import ChannelType, Message, User, DMChannel, TextChannel, NotFound, Client

from cognitive import NLUService, IntentResult, EntityResult
from configuration import Configuration
from datetime import datetime

from constants import SYSTEM_COMMAND_SYMBOL, USER_COMMAND_SYMBOL
from scheduler import BotScheduler

__registered_commands: Dict[Tuple[str, bool], str] = {}


def __register_command(method, help_msg: str, is_system_command: bool, name: str, params: List[str]):
    if name is None:
        assert method.__name__.startswith("__")
        name = method.__name__[2:].replace("_", "-")
    if params is None:
        params = []
    __registered_commands[(f"{name} {' '.join(params)}".strip(), is_system_command)] = help_msg


def command_meta(help_msg: str = None, is_system_command: bool = False, name: str = None, params: List[str] = None):
    def decorator(func):
        if help_msg is not None:
            __register_command(func, help_msg, is_system_command, name, params)

        @functools.wraps(func)
        def wrapper(*args, **kwargs):
            return func(*args, **kwargs)

        return wrapper

    return decorator


class BotBase(Client):
    def __init__(self):
        super().__init__()
        self.config: Configuration = Configuration()
        self.nlu: NLUService = NLUService(self.config)
        self.scheduler: BotScheduler = BotScheduler(self.loop)

    @staticmethod
    def log(message: Message) -> None:
        """ Log a message to std out.
        :param message the actual message
        """
        print(f"{datetime.now()} => {message.author}[{message.channel}]: {message.content}")

    async def print_intents_entities(self, message: Message, intents: List[IntentResult],
                                     entities: List[EntityResult]) -> None:
        """ Prints the stats of classification of one message.
        :param message the message
        :param intents the intent result
        :param entities the found entities
        """
        if not self.config.is_debug():
            return

        result: str = "------------\n"
        result += f"Intents({len(intents)}):\n"

        for intent in intents:
            result += f"{intent}\n"

        result += f"\nEntities({len(entities)}):\n"
        for entity in entities:
            result += f"{entity}\n"

        result += "------------"

        await send(message.author, message.channel, self, result, mention=False)

    def get_bot_user(self) -> Client:
        """ Get the Discord User of the Bot.
        :return the Discord User as Client
        """
        return self.user

    async def shutdown(self) -> None:
        """Shutdown the bot"""
        await self.close()


async def send(respondee: User, channel: Union[DMChannel, TextChannel], bot: BotBase, message: Any,
               mention: bool = True, try_delete: bool = True) -> Optional[Message]:
    """ Send a message to a channel.
    :param respondee: the user which has started the conversation
    :param channel: the target channel for sending the message
    :param bot: the bot itself
    :param message: the message to send
    :param mention: indicator for mentioning the respondee
    :param try_delete: try to delete message after ttl (if activated)
    :return Message if not deleted
    """

    if mention:
        msg = await channel.send(f"{respondee.mention} {message}")
    else:
        msg = await channel.send(message)
    if not channel.type == ChannelType.private and try_delete:
        await delete(msg, bot, delay=bot.config.ttl)

    return msg


async def delete(message: Message, bot: BotBase, try_force: bool = False, delay=None) -> None:
    """ Delete a message.
    :param message the actual message
    :param bot the actual bot
    :param try_force indicates whether the bot shall try to delete even iff debug is activated
    :param delay some delay
    """
    if (bot.config.is_debug() or bot.config.is_keep_messages()) and not try_force:
        return

    if is_direct(message):
        return

    try:
        await message.delete(delay=delay)
    except NotFound:
        pass


def is_direct(message: Message) -> bool:
    """ Indicates whether a message was sent via a DM Channel
    :param message the message
    :return the indicator
    """
    return message.channel.type == ChannelType.private


async def send_help_message(message: Message, self: BotBase):
    response = f"""
Ich kann verschiedene Aufgaben erledigen:\n
* Ich kann grüßen
* Ich kann Dir sagen was ich kann :)
* Ich kann Witze erzählen
* Ich kann Nachrichten (News) liefern
* Ich kann Dir die Uhrzeit sagen
* Ich kann Dinge (Gruppen z.B.) zufällig verteilen

* Du kannst mich den Channel aufräumen lassen
* Du kannst neue Antworten einfügen, die ich dann kenne
"""

    # User Commands:
    response += "\n*Folgende User-Befehle unterstütze ich:*\n"
    for (name, sys_command) in sorted(__registered_commands.keys(), key=lambda nXt: nXt[0]):
        if not sys_command:
            response += f"**{USER_COMMAND_SYMBOL}{name}**: " + __registered_commands[(name, sys_command)] + "\n"

    if self.config.is_admin(message.author):
        response += "\n\n*Folgende System-Befehle unterstütze ich:*\n"
        for (name, sys_command) in sorted(__registered_commands.keys(), key=lambda nXt: nXt[0]):
            if sys_command:
                response += f"**{SYSTEM_COMMAND_SYMBOL}{name}**: " + __registered_commands[(name, sys_command)] + "\n"

    response_msg = await send(message.author, message.channel, self, response.strip())
    await delete(response_msg, self, delay=self.config.ttl, try_force=True)
