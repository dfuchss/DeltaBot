from asyncio import sleep
from os import remove
from re import sub, findall
from tempfile import NamedTemporaryFile
from typing import List, Optional, TypeVar, Union, Any

from discord import ChannelType, FFmpegPCMAudio, Message, User, VoiceChannel, DMChannel, TextChannel

from cognitive import TextToSpeech


async def send(respondee: User, channel: Union[DMChannel, TextChannel], bot, message: Any, mention: bool = True):
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
        await msg.delete(delay=bot.config.ttl)
    if bot.config.tts_indicator:
        await send_tts(respondee, message, bot, bot.tts)


def __find_voice_channel(user: User, bot) -> Optional[VoiceChannel]:
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


async def send_tts(respondee: User, message: str, bot, tts: TextToSpeech) -> None:
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


async def delete(message: Message, bot, try_force: bool = False) -> None:
    """ Delete a message.
    :param message the actual message
    :param bot the actual bot
    :param try_force indicates whether the bot shall try to delete even iff debug is activated
    """
    if bot.config.debug_indicator and not try_force:
        return

    if is_direct(message):
        return

    await message.delete()


def is_direct(message: Message) -> bool:
    """ Indicates whether a message was sent via a DM Channel
    :param message the message
    :return the indicator
    """
    return message.channel.type == ChannelType.private


def cleanup(message: str, bot) -> str:
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
