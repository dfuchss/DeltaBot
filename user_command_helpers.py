import datetime
import re

from datefinder import DateFinder
from discord import Message


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

    return dt[0], matches[0][0]


days = [
    (r"\bheute\b", 0, "heute"),
    (r"\bmorgen\b", 1, "morgen"),
    (r"(\bübermorgen\b|\bin zwei tagen\b|\bin 2 tagen\b)", 2, "übermorgen")
]


def find_day_by_special_key(clean_msg):
    for (rgx, offset, default) in days:
        match = re.search(rgx, clean_msg, re.IGNORECASE)
        if match is None:
            continue
        return offset, match.group(), default

    return None, None, None


def find_time(message: Message):
    split = message.clean_content.split(" ", 1)
    clean_msg = ("" if len(split) < 2 else split[1]).replace("@", "").strip()

    dt_regex, dt_regex_match = find_date_by_finder(clean_msg)
    dt_special_offset, dt_special_match, _ = find_day_by_special_key(clean_msg)

    if dt_regex is None and dt_special_offset is None:
        return None, None

    h, m, s, d, M, y = (0, 0, 0, 0, 0, 0)

    result_msg = message.content[len("/reminder"):].strip()

    if dt_regex is not None:
        h = dt_regex.hour
        m = dt_regex.minute
        s = dt_regex.second
        d = dt_regex.day
        M = dt_regex.month
        y = dt_regex.year
        result_msg = result_msg.replace(dt_regex_match, "")

    if dt_special_offset is not None:
        date = datetime.datetime.now() + datetime.timedelta(days=dt_special_offset)
        d = date.day
        M = date.month
        y = date.year
        result_msg = result_msg.replace(dt_special_match, "")

    return datetime.datetime(y, M, d, h, m, s), result_msg
