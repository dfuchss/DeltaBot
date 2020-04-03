from asyncio import sleep
from os import remove
from re import sub, findall
from tempfile import NamedTemporaryFile
from typing import List, Optional, TypeVar, Union, Any, Tuple

from discord import ChannelType, FFmpegPCMAudio, Message, User, VoiceChannel, DMChannel, TextChannel, NotFound, Client

from cognitive import TextToSpeech, NLUService, IntentResult, EntityResult
from configuration import Configuration
from datetime import datetime


class BotBase(Client):
    channels: List[int] = []
    admins: List[Tuple[str, str]] = []

    def __init__(self):
        super().__init__()
        self.config = Configuration()
        self.tts = TextToSpeech(self.config)
        self.nlu = NLUService(self.config)

    @staticmethod
    def log(message: Message):
        """ Log a message to std out.
        :param message the actual message
        """
        print(f"{datetime.now()} => {message.author}[{message.channel}]: {message.content}")

    async def print_intents_entities(self, message: Message, intents: List[IntentResult], entities: List[EntityResult]) -> None:
        """ Prints the stats of classification of one message.
        :param message the message
        :param intents the intent result
        :param entities the found entities
        """
        if not self.config.debug_indicator:
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

    def is_admin(self, user: User) -> bool:
        """
        Check for Admin.
        :param user: the actual user object
        :return: indicator for administrative privileges
        """
        if len(self.admins) == 0:
            return True

        for (name, dsc) in self.admins:
            if user.name == name and user.discriminator == dsc:
                return True
        return False

    def add_admins(self, message: Message):
        for user in message.mentions:
            self.admins.append((user.name, user.discriminator))

    async def shutdown(self) -> None:
        """Shutdown the bot"""
        await self.close()
        await self.logout()


async def send(respondee: User, channel: Union[DMChannel, TextChannel], bot: BotBase, message: Any, mention: bool = True):
    """ Send a message to a channel.
    :param respondee: the user which has started the conversation
    :param channel: the target channel for sending the message
    :param bot: the bot itself
    :param message: the message to send
    :param mention: indicator for mentioning the respondee
    """

    if mention:
        msg = await channel.send(f"{respondee.mention} {message}")
    else:
        msg = await channel.send(message)
    if not channel.type == ChannelType.private:
        await delete(msg, bot, delay=bot.config.ttl)
    if bot.config.tts_indicator:
        await send_tts(respondee, message, bot, bot.tts)


def __find_voice_channel(user: User, bot: BotBase) -> Optional[VoiceChannel]:
    """ Find a voice channel by user.
    :param user the user
    :param bot the actual bot (has to be Bot and Client!)
    :return the found voice channel or None
    """
    if hasattr(user, "voice") and user.voice is not None:
        return user.voice.channel
    for guild in bot.guilds:
        for channel in guild.channels:
            if channel.type == ChannelType.voice and user in channel.members:
                return channel
    return None


async def send_tts(respondee: User, message: str, bot: BotBase, tts: TextToSpeech) -> None:
    """ Send a TextToSpeech message.
    :param respondee the user for the search for VoiceChannel
    :param message the message to be sent
    :param bot: the actual bot
    :param tts: the TextToSpeech service
    """
    vc = __find_voice_channel(respondee, bot)

    if vc is None:
        return

    temp = NamedTemporaryFile(suffix=".mp3")
    path = temp.name
    temp.close()

    # Generate TTS
    res = tts.create_tts(cleanup(message, bot), path)
    if not res:
        return

    # Play it ..
    client = await vc.connect()
    audio = FFmpegPCMAudio(path)
    client.play(audio)
    while client.is_playing():
        await sleep(1)
    client.stop()
    await client.disconnect()

    # Cleanup ..
    remove(path)


async def delete(message: Message, bot: BotBase, try_force: bool = False, delay=None) -> None:
    """ Delete a message.
    :param message the actual message
    :param bot the actual bot
    :param try_force indicates whether the bot shall try to delete even iff debug is activated
    :param delay some delay
    """
    if (bot.config.debug_indicator or bot.config.keep_messages) and not try_force:
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


def cleanup(message: str, bot: BotBase) -> str:
    """ Cleanups a message. E.g. replaces the ids of users by their names.
    :param message the message
    :param bot the bot
    :return the new message
    """
    refs = findall("<@[0-9]+>", message)
    if not refs:
        return message

    for ref in refs:
        user = int(ref[2:len(ref) - 1])
        username = bot.lookup_user(user)
        if username is None:
            username = "Unbekannt"
        else:
            username = sub(r"[^a-zA-Z0-9ÄÖÜäöüß -]", "", username.name)

        message = sub(ref, username, message)

    return message


T = TypeVar('T')


def flatten(l: List[List[T]]) -> List[T]:
    """ Flatten a List of Lists.
    :param l the list of lists
    :return the new list
    """
    return [item for sublist in l for item in sublist]
