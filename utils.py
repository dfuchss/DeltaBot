import re
from typing import List, Optional, Union

from discord import Message, Guild, Emoji
from emoji import UNICODE_EMOJI_ENGLISH


def find_all_emojis(input_message_content: str, unique=True) -> List[str]:
    content = str(input_message_content)
    emojis = []

    # Find custom emojis
    for emoji in re.findall(r"<:[A-Za-z0-9-]+:\d+>", content):
        emojis.append(emoji)
    content = re.sub(r"<:[A-Za-z0-9-]+:\d+>", "", content)

    # Find general emojis
    for char in content:
        if char in UNICODE_EMOJI_ENGLISH.keys():
            emojis.append(char)

    return list(set(emojis)) if unique else emojis


def get_guild(message: Message) -> Optional[Guild]:
    return None if message is None or not isinstance(message.guild, Guild) else message.guild


async def load_emoji(emoji: str, guild: Guild) -> Union[str, Emoji]:
    if re.match(r"<:[A-Za-z0-9-]+:\d+>", emoji) is None:
        return emoji

    emoji = emoji.split(":")[2]
    emoji = int(emoji[0:len(emoji) - 1])
    return await guild.fetch_emoji(emoji)
