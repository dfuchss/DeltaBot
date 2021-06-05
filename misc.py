from json import loads
from random import choice
from typing import List, Optional, TypeVar, Union, Any

from discord import ChannelType, Message, User, DMChannel, TextChannel, NotFound, Client

from cognitive import NLUService, IntentResult, EntityResult
from configuration import Configuration
from datetime import datetime


class BotBase(Client):
    def __init__(self):
        super().__init__()
        self.config: Configuration = Configuration()
        self.nlu: NLUService = NLUService(self.config)

    @staticmethod
    def log(message: Message):
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

    def lookup_user(self, user_id: int) -> Optional[User]:
        """Find user by id
        :param user_id: the id of the user
        :return the found user object or None
        """
        users = list(filter(lambda u: u.id == user_id, self.users))
        if len(users) != 1:
            return None
        return users[0]

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
        return None

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


T = TypeVar('T')


def flatten(in_list: List[List[T]]) -> List[T]:
    """ Flatten a List of Lists.
    :param in_list the list of lists
    :return the new list
    """
    return [item for sublist in in_list for item in sublist]


async def send_help_message(message: Message, self: BotBase):
    responses = open("QnA/Tasks.json", "r", encoding="utf-8-sig")
    response = loads(responses.read().strip())
    responses.close()
    response = choice(response)
    await send(message.author, message.channel, self, response)
