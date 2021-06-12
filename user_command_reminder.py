from loadable import Loadable
from discord import Message, TextChannel

from misc import BotBase, send, is_direct, delete
from user_command_helpers import find_time


class ReminderState(Loadable):
    def __init__(self):
        super().__init__(path="./states/reminder_state.json", version=1)
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
    (dt, cleanup_message) = find_time(message)
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
