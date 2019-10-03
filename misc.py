from asyncio import sleep
from os import remove
from re import sub, findall
from tempfile import NamedTemporaryFile
from typing import List, Optional, TypeVar, Union, Any

from discord import ChannelType, FFmpegPCMAudio, Message, User, VoiceChannel, DMChannel, TextChannel, Client

from cognitive import NLUService, TextToSpeech
from configuration import Configuration


class Bot:
    """ The base interface for the bot. """

    def __init__(self) -> None:
        self.config = Configuration()
        self.ttsIndicator = False
        self.debug = False
        self.tts = TextToSpeech(self.config)
        self._nlu = NLUService(self.config)

    def get_bot_user(self) -> Client:
        pass

    def toggle_tts(self) -> bool:
        self.ttsIndicator = not self.ttsIndicator
        return self.ttsIndicator

    def toggle_debug(self) -> bool:
        """Toggles the debug flag.

          Returns:
              bool -- the new value of the debug flag
        """
        self.debug = not self.debug
        return self.debug

    async def shutdown(self) -> None:
        """Shutdown the bot"""
        pass

    def lookup_user(self, user_id: int) -> Optional[User]:
        """Find user by id

        Arguments:
            id {int} -- the id        
        Returns:
            Optional[User] -- the user or none
        """
        pass

    @staticmethod
    def is_admin(user: User) -> bool:
        """Check whether a user is an admin

        Arguments:
            user {User} -- the user

        Returns:
            bool -- the indicator
        """
        pass


async def send(respondee: User, channel: Union[DMChannel, TextChannel], bot: Bot, message: Any, mention: bool = True):
    """ Send a message to a channel."""

    if mention:
        msg = await channel.send(f"{respondee.mention} {message}")
    else:
        msg = await channel.send(message)
    if not channel.type == ChannelType.private:
        await msg.delete(delay=bot.config.ttl)
    if bot.ttsIndicator:
        await send_tts(respondee, message, bot, bot.tts)


def get_vc(user: User) -> Optional[VoiceChannel]:
    if hasattr(user, "voice") and user.voice is not None:
        vc = user.voice.channel
    else:
        vc = None
    return vc


async def send_tts(respondee: User, message: str, bot: Bot, tts: TextToSpeech) -> None:
    vc = get_vc(respondee)
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


async def delete(message: Message, bot: Bot, try_force: bool = False) -> None:
    if bot.debug and not try_force:
        return

    if is_direct(message):
        return

    await message.delete()


def is_direct(message: Message) -> bool:
    return message.channel.type == ChannelType.private


def cleanup(message: str, bot: Bot):
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


def flatten(l: List[List[T]]) -> List[T]: return [item for sublist in l for item in sublist]
