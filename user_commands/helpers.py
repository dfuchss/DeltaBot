import datetime
import re
from typing import Tuple

from datefinder import DateFinder
from discord import Message

from constants import DAYS


def __crop_command(raw_message: str):
    msg = raw_message.split(" ", 1)
    if len(msg) == 1:
        return ""
    return msg[1].strip()


def __read_number_param(text, default):
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


def find_date_by_finder(clean_msg: str):
    dt = [d for d in __df.find_dates(clean_msg)]
    if len(dt) != 1:
        return None, None
    matches = [d for d in __df.extract_date_strings(clean_msg)]
    assert len(matches) == len(dt)
    end_idx = matches[0][1][1]
    return dt[0], end_idx


def find_day_by_special_rgx(clean_msg) -> Tuple[int, int, int, str]:
    for (rgx, offset, default) in DAYS:
        matches = [g for g in re.finditer(rgx, clean_msg, re.IGNORECASE)]
        if len(matches) == 0:
            continue

        min_idx = min([start for (start, end) in [match.regs[0] for match in matches]])
        max_idx = max([end for (start, end) in [match.regs[0] for match in matches]])
        return offset, min_idx, max_idx, default

    return None, None, None, None


def find_time(message: Message):
    split = message.content.split(" ", 1)
    clean_msg = ("" if len(split) < 2 else split[1]).replace("@", "0").strip()

    dt_regex, dt_regex_match_idx = find_date_by_finder(clean_msg)
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
