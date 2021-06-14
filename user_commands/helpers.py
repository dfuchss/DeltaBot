import datetime
import re
from typing import Tuple, Optional

from datefinder import DateFinder
from discord import Message

from constants import DAYS


def __crop_command(raw_message: str) -> str:
    """
    Delete the command part from a message (e.g. "/help xy" -> "xy")

    :param raw_message: the message text
    :return: the message text without the command part
    """
    msg = raw_message.split(" ", 1)
    if len(msg) == 1:
        return ""
    return msg[1].strip()


def __read_number_param(text: str, default: int) -> int:
    """
    Read exactly one number parameter from the command (e.g. "/roll 5" -> 5)

    :param text: the input text
    :param default: the default value if no number has been found
    :return: the detected int
    """
    val = default
    split = text.strip().split(" ")
    if len(split) == 2:
        try:
            val = int(split[1])
            if val < 1:
                val = default
        except Exception:
            pass
    return val


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

    h, m, s, d, M, y = (0, 0, 0, 0, 0, 0)

    result_msg = message.content.split(" ", 1)
    result_msg = ("" if len(result_msg) < 2 else result_msg[1])

    end_of_time = 0

    if dt_regex is not None:
        h = dt_regex.hour
        m = dt_regex.minute
        s = dt_regex.second
        d = dt_regex.day
        M = dt_regex.month
        y = dt_regex.year
        end_of_time = max(end_of_time, dt_regex_match_idx)

    if dt_special_offset is not None:
        date = datetime.datetime.now() + datetime.timedelta(days=dt_special_offset)
        d = date.day
        M = date.month
        y = date.year
        end_of_time = max(end_of_time, dt_special_match_idx)

    result_msg = result_msg[end_of_time:].strip()
    return datetime.datetime(y, M, d, h, m, s), result_msg
