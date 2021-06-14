from typing import List

from loadable import Loadable
from discord import Message, TextChannel

from bot_base import BotBase, send, is_direct, delete, command_meta
from .helpers import find_time


class ReminderState(Loadable):
    """
    This state contains all future reminders.
    """

    def __init__(self):
        super().__init__(path="./states/reminder_state.json", version=1)
        self._reminders = []
        self._load()

    def add_reminder(self, reminder: dict) -> None:
        """
        Add a new reminder.

        :param reminder: the data as dictionary (see __reminder)
        """
        self._reminders.append(reminder)
        self._store()

    def remove_reminder(self, reminder: dict) -> None:
        """
        Remove a reminder.

        :param reminder: the data as dictionary (see __reminder)
        """
        self._reminders.remove(reminder)
        self._store()

    def reminders(self) -> List[dict]:
        """
        Get all stored reminders.

        :return: a list of reminders as dictionaries
        """
        return self._reminders


__reminder_state = ReminderState()
"""The one and only reminder_state"""


async def __execute_reminder(data: dict, bot: BotBase) -> None:
    """
    Send a reminder to the specific user.

    :param data: the data for the reminder (e.g. user, channel, text)
    :param bot: the bot itself
    """
    __reminder_state.remove_reminder(data)

    message = data["msg"]
    channel: TextChannel = None if data["cid"] is None else await bot.fetch_channel(data["cid"])
    user = await bot.fetch_user(data["target_id"])

    if channel is None:
        await user.send(message)
    else:
        await send(user, channel, bot, message, try_delete=False)


@command_meta(
    help_msg="Erzeugt eine Erinnerung zu einer gegebenen Zeit (wenn kein Datum angegeben wurde, wird heute verwendet).",
    params=["Zeit", "Text"])
async def __reminder(message: Message, bot: BotBase) -> None:
    """
    Send a user an reminder to a specific time

    :param message: the message from the user
    :param bot: the bot itself
    """
    (dt, cleanup_message) = find_time(message)

    if dt is None:
        await send(message.author, message.channel, bot, "Ich konnte weder Datum noch Zeitpunkt finden.")
        return

    if len(cleanup_message) == 0:
        await send(message.author, message.channel, bot, "Ich konnte keine Nachricht finden.")
        return

    target_id = message.author.id
    cid = None if is_direct(message) else message.channel.id

    data = {
        "ts": dt.timestamp(),
        "target_id": target_id,
        "cid": cid,
        "msg": cleanup_message
    }

    __reminder_state.add_reminder(data)

    bot.scheduler.queue(__execute_reminder(data, bot), dt.timestamp())
    resp = await send(message.author, message.channel, bot, f"Ich erinnere Dich am {dt} an **{cleanup_message}**")

    await delete(message, bot, try_force=True)
    await resp.delete(delay=15)


def _init_reminders(bot: BotBase) -> None:
    """
    Initialize the scheduler for reminders.
    :param bot: the bot itself
    """
    for r in __reminder_state.reminders():
        bot.scheduler.queue(__execute_reminder(r, bot), r["ts"])
