import datetime
import re

from loadable import Loadable
from discord import Message, TextChannel
from datefinder import DateFinder

from misc import BotBase, send, is_direct, delete


class ReminderState(Loadable):
    def __init__(self):
        super().__init__(path="./reminder_state.json", version=1)
        self._reminders = []
        self._load()

    def add_reminder(self, reminder):
        self._reminders.append(reminder)
        self._store()

    def remove_reminder(self, reminder):
        self._reminders.remove(reminder)
        self._store()

    def reminders(self):
        return self._reminders


__reminder_state = ReminderState()

__df: DateFinder = DateFinder()


def __find_date_by_finder(clean_msg: str):
    dt = [d for d in __df.find_dates(clean_msg)]
    if len(dt) != 1:
        return None, None
    matches = [d for d in __df.extract_date_strings(clean_msg)]
    assert len(matches) == len(dt)

    return dt[0], matches[0][0]


def __find_day_by_special_key(clean_msg):
    today = r"\bheute\b", 0
    tomorrow = r"\bmorgen\b", 1
    the_day_after_tomorrow = r"\bübermorgen\b", 2

    for (rgx, offset) in [today, tomorrow, the_day_after_tomorrow]:
        match = re.search(rgx, clean_msg, re.IGNORECASE)
        if match is None:
            continue
        return offset, match.group()

    return None, None


def __find_time(message: Message):
    clean_msg = message.clean_content[len("/reminder"):].replace("@", "").strip()
    dt_regex, dt_regex_match = __find_date_by_finder(clean_msg)
    dt_special_offset, dt_special_match = __find_day_by_special_key(clean_msg)

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


async def __execute_reminder(data, self: BotBase):
    message = data["msg"]
    channel: TextChannel = None if data["cid"] is None else await self.fetch_channel(data["cid"])
    user = await self.fetch_user(data["target_id"])

    if channel is None:
        await user.send(message)
    else:
        await send(user, channel, self, message, try_delete=False)

    __reminder_state.remove_reminder(data)


async def __reminder(message: Message, self: BotBase):
    (dt, cleanup_message) = __find_time(message)
    target_id = message.author.id
    cid = None if is_direct(message) else message.channel.id

    if dt is None:
        await send(message.author, message.channel, self, "Ich konnte kein Datum oder Zeitpunkt finden.")
        return

    data = {
        "ts": dt.timestamp(),
        "target_id": target_id,
        "cid": cid,
        "msg": cleanup_message
    }

    __reminder_state.add_reminder(data)

    self.scheduler.queue(__execute_reminder(data, self), dt.timestamp())
    resp = await send(message.author, message.channel, self, f"Ich erinnere Dich daran: {dt}")

    await delete(message, self, try_force=True)
    await resp.delete(delay=15)


def _init_reminders(self: BotBase):
    for r in __reminder_state.reminders():
        self.scheduler.queue(__execute_reminder(r, self), r["ts"])
