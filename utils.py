import datetime
import re
from typing import List, Union, Iterator, Tuple, Optional

from datefinder import DateFinder
from discord import Message, Guild, Emoji
from discord_components import ActionRow, Button
from emoji import UNICODE_EMOJI_ENGLISH

DAYS = [
    (r"\bheute\b", 0, "heute"),
    (r"(\bmorgen\b|\bin einem tag\b|\bin 1 tag\b)", 1, "morgen"),
    (r"(\bübermorgen\b|\bin zwei tagen\b|\bin 2 tagen\b)", 2, "übermorgen")
]
"""
A definition of regexes for special days (e.g. "heute" or "morgen") as triple:
(regex, offset_to_today, default_value)
"""

__df: DateFinder = DateFinder()


def find_date_by_finder(clean_msg: str) -> Tuple[Optional[datetime.datetime], Optional[int], Optional[int]]:
    """
    Find one mentioned datetime in a text

    :param clean_msg: a message with slight cleanups (e.g. replace @ by 0, and replace more special characters)
    :return: a triple (None,None,None) if nothing or many dates has been found; otherwise (datetime, start_idx, end_idx)
    """
    dt = [d for d in __df.find_dates(clean_msg)]
    if len(dt) != 1:
        return None, None, None
    matches = [d for d in __df.extract_date_strings(clean_msg)]
    assert len(matches) == len(dt)
    start_idx = matches[0][1][0]
    end_idx = matches[0][1][1]
    return dt[0], start_idx, end_idx


def find_day_by_special_rgx(text: str) -> Tuple[Optional[int], Optional[int], Optional[int], Optional[int]]:
    """
    Find a mentioned date by regex matching.
    The result contains a day_offset (today=0, tomorrow=1, ..), a max and min index for the match,
    and the default representation of the day_offset (e.g. offset == 0 -> "heute")

    :param text: a text
    :return: a quadruple (None,None,None,None) if nothing was recognized; otherwise (day_offset,min_idx,max_idx,default)
    """
    for (rgx, offset, default) in DAYS:
        matches = [g for g in re.finditer(rgx, text, re.IGNORECASE)]
        if len(matches) == 0:
            continue

        min_idx = min([start for (start, end) in [match.regs[0] for match in matches]])
        max_idx = max([end for (start, end) in [match.regs[0] for match in matches]])
        return offset, min_idx, max_idx, default

    return None, None, None, None


def find_time(message: Message) -> Tuple[Optional[datetime.datetime], Optional[str]]:
    """
    Find a mentioned datetime in a message and create a message text without mentioned datetime.

    :param message: the message
    :return: a tuple (None,None) if no unique datetime has been found, otherwise (datetime, result_msg)
    """
    split = message.content.split(" ", 1)
    clean_msg = ("" if len(split) < 2 else split[1]).replace("@", "0").strip()

    dt_regex, _, dt_regex_match_idx = find_date_by_finder(clean_msg)
    dt_special_offset, _, dt_special_match_idx, _ = find_day_by_special_rgx(clean_msg)

    if dt_regex is None and dt_special_offset is None:
        return None, None

    h, m, s, day, month, year = (0, 0, 0, 0, 0, 0)

    result_msg = message.content.split(" ", 1)
    result_msg = ("" if len(result_msg) < 2 else result_msg[1])

    end_of_time = 0

    if dt_regex is not None:
        h = dt_regex.hour
        m = dt_regex.minute
        s = dt_regex.second
        day = dt_regex.day
        month = dt_regex.month
        year = dt_regex.year
        end_of_time = max(end_of_time, dt_regex_match_idx)

    if dt_special_offset is not None:
        date = datetime.datetime.now() + datetime.timedelta(days=dt_special_offset)
        day = date.day
        month = date.month
        year = date.year
        end_of_time = max(end_of_time, dt_special_match_idx)

    result_msg = result_msg[end_of_time:].strip()
    return datetime.datetime(year, month, day, h, m, s), result_msg


def find_all_emojis(input_message_content: str, unique: bool = True) -> List[str]:
    """
    Finds all mentioned emojis in a text (raw content of message). (See #load_emoji)

    :param input_message_content: the content of a message
    :param unique: indicator whether duplicates shall be deleted from result list
    :return: a list of all found emojis. may be a character or a reference to a custom emoji
    """
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


async def load_emoji(emoji: str, guild: Guild) -> Union[str, Emoji]:
    """
    Loads a specific emoji

    :param emoji: the identifier of an emoji (emoji character or id)
    :param guild: the guild to be used for loading custom emojis
    :return: the emoji character or the emoji object loaded from the guild (server)
    """
    if re.match(r"<:[A-Za-z0-9-]+:\d+>", emoji) is None:
        return emoji

    emoji = emoji.split(":")[2]
    emoji = int(emoji[0:len(emoji) - 1])
    return await guild.fetch_emoji(emoji)


def get_guild(message: Message) -> Optional[Guild]:
    """
    Get the guild of a message.

    :param message: the message
    :return: the found guild or None if nothing found
    """
    return None if message is None or not isinstance(message.guild, Guild) else message.guild


def get_buttons(container: Union[List, ActionRow, Button]) -> Iterator[Button]:
    """
    Find all buttons in a composite / container recursively.

    :param container: the start container
    :return: an iterator of buttons
    """
    if isinstance(container, list):
        for elem in container:
            yield from get_buttons(elem)

    if isinstance(container, ActionRow):
        yield from get_buttons(container.components)

    if isinstance(container, Button):
        yield container


def create_button_grid(buttons: List[Button], max_in_row: int = 5, try_mod_zero: bool = True) -> List[List[Button]]:
    """
    Since buttons can be arranged in rows with at max 5 entries, this method arrange the given buttons in multiple rows.

    :param buttons: the buttons to arrange
    :param max_in_row: the max number of buttons in a row
    :param try_mod_zero: indicator whether the method shall try to arrange the buttons in equal length rows
    :return: a list of rows (list of buttons)
    """
    if try_mod_zero:
        num_buttons = len(buttons)
        for i in reversed(range(3, max_in_row + 1)):
            if num_buttons % i == 0:
                max_in_row = i
                break

    rows = []
    row = []
    for b in buttons:
        if len(row) >= max_in_row:
            rows.append(row)
            row = []
        row.append(b)
    if len(row) > 0:
        rows.append(row)
    return rows
