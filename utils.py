import re
from typing import List, Optional, Union

from discord import Message, Guild, Emoji
from discord_components import ActionRow, Button
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


def get_buttons(container: Union[List, ActionRow, Button]) -> List[Button]:
    if isinstance(container, list):
        for elem in container:
            yield from get_buttons(elem)

    if isinstance(container, ActionRow):
        yield from get_buttons(container.components)

    if isinstance(container, Button):
        yield container


def create_button_grid(buttons: List[Button], max_in_column: int = 5, try_mod_zero: bool = True) -> List[List[Button]]:
    if try_mod_zero:
        num_buttons = len(buttons)
        for i in reversed(range(3, max_in_column + 1)):
            if num_buttons % i == 0:
                max_in_column = i
                break

    rows = []
    row = []
    for b in buttons:
        if len(row) >= max_in_column:
            rows.append(row)
            row = []
        row.append(b)
    if len(row) > 0:
        rows.append(row)
    return rows
