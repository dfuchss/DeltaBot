import re
from asyncio import iscoroutine
from typing import Union, Callable, Awaitable, Optional, List, Dict, Tuple

from datefinder import DateFinder
from datetime import datetime
from discord import Message, VoiceChannel, User, TextChannel, Role, RawReactionActionEvent

from loadable import Loadable
from misc import delete, send, BotBase, send_help_message, is_direct

from random import randint, shuffle, choice

USER_COMMAND_SYMBOL = "/"


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


async def __help(message, self):
    return await send_help_message(message, self)


async def __roll(message, self):
    text: str = message.content
    dice = __read_number_param(text, 6)
    rnd = randint(1, dice)
    await send(message.author, message.channel, self, f"{rnd}")


__summon_reactions = ['\N{Thumbs Up Sign}', '\N{Black Question Mark Ornament}', '\N{Thumbs Down Sign}']

__summon_msg = [f"###USER###: Wer wäre ###TIME### dabei? ###MENTION###",  #
                f"Wer hätte ###TIME### Lust ###MENTION### (###USER###)",  #
                f"Jemand ###TIME### Bock auf ###MENTION### (###USER###)",  #
                f"Finden sich ###TIME### Leute ###MENTION### (###USER###)"
                ]
__summon_rgx = [r"^<@!?\d+>: Wer wäre",  #
                r"\(<@!?\d+>\)$"
                ]


async def __summon(message: Message, self: BotBase):
    if len(message.role_mentions) == 0:
        await send(message.author, message.channel, self, f"Ich habe keine Gruppen gefunden ..")
        return

    user: User = message.author
    time: str = __crop_command(message.content)
    roles: List[Role] = message.role_mentions

    time = re.sub(r"<@&?\d+>", "", time)
    time = time.strip()

    response = choice(__summon_msg)
    response = response.replace("###USER###", user.mention)
    response = response.replace("###MENTION###", " ".join([r.mention for r in roles]))
    response = response.replace("###TIME###", 'zur gewohnten Zeit' if len(time) == 0 else time)

    response += "\n\nBitte Reactions zum Abstimmen benutzen:"

    channel: TextChannel = message.channel
    resp_message: Message = await channel.send(response)
    for react in __summon_reactions:
        await resp_message.add_reaction(react)

    await delete(message, self, True)


def __read_reactions(reactions: List[str], message_content: str):
    result = {}
    for reaction in reactions:
        result[reaction] = []

    for line in message_content.split("\n"):
        if line is None or len(line.strip()) == 0:
            continue
        split = line.split(":", 1)

        mention_type = line[0]

        if len(split) < 2 or mention_type not in reactions:
            continue

        new_mentions = [m.strip() for m in split[1].split(",")]
        for m in new_mentions:
            if len(m) != 0:
                result[mention_type].append(m)

    return result


async def __handling_reaction_summon(self: BotBase, payload: RawReactionActionEvent, message: Message,
                                     channel: TextChannel):
    if message.author != self.user:
        return False

    text = message.content
    is_summon = any([re.search(rgx, text.split("\n")[0].strip()) is not None for rgx in __summon_rgx])

    if not is_summon:
        return False

    user: User = await self.fetch_user(payload.user_id)

    react = payload.emoji.name

    if react not in __summon_reactions:
        await message.remove_reaction(payload.emoji, user)
        return True

    reactions: Dict[str, List[str]] = __read_reactions(__summon_reactions, text)

    if user.mention in reactions[react]:
        reactions[react].remove(user.mention)
    else:
        reactions[react].append(user.mention)

    result_msg = text.split("\n")[0].strip()
    summary = ""

    for reaction in reactions.keys():
        users = reactions[reaction]
        if len(users) == 0:
            continue
        summary = f"{summary}\n{reaction} `({len(users)})`: {str.join(',', users)}"

    if len(summary) != 0:
        result_msg = f"{result_msg}\n\nAktuell:\n{summary.strip()}"

    result_msg += "\n\nBitte Reactions zum Abstimmen benutzen:"

    await message.remove_reaction(payload.emoji, user)
    await message.edit(content=result_msg)
    return True


async def __teams(message: Message, self: BotBase):
    text: str = message.content
    num = __read_number_param(text, 2)

    channel: Optional[VoiceChannel] = None
    try:
        channel = message.author.voice.channel
    except Exception:
        pass

    if channel is None:
        await send(message.author, message.channel, self, "Ich finde keinen Voice Channel")
        return

    members = [await self.fetch_user(member) for member in channel.voice_states.keys()]
    members = list(map(lambda n: n.mention, filter(lambda m: m is not None, members)))
    shuffle(members)

    groups = {}
    for i in range(0, num):
        groups[i] = []

    i = 0
    for e in members:
        groups[i].append(e)
        i = (i + 1) % num

    teams = ""
    for t in groups.keys():
        teams = teams + f"{t + 1}: {groups[t]}\n"

    await send(message.author, message.channel, self, f"Zuordnung:\n{teams.strip()}", mention=False)


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


def __find_time(message: Message):
    df: DateFinder = DateFinder()

    clean_msg = message.clean_content[len("/reminder"):].replace("@", "").strip()
    dt = [d for d in df.find_dates(clean_msg)]

    if len(dt) != 1:
        return None, None

    strs = [d for d in df.extract_date_strings(clean_msg)]
    assert len(strs) == len(dt)

    clean_msg = message.content[len("/reminder"):].replace(strs[0][0], "").strip()
    return dt[0], clean_msg


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

    await message.delete()
    await resp.delete(delay=15)


def _init_reminders(self: BotBase):
    for reminder in __reminder_state.reminders():
        self.scheduler.queue(__execute_reminder(reminder, self), reminder["ts"])


def __unknown(message: Message, self: BotBase):
    return send(message.author, message.channel, self, "Unbekannter Befehl")


HandlingFunction = Union[  #
    Callable[[Message, BotBase], None],  #
    Callable[[Message, BotBase], Awaitable[None]]  #
]


async def __handling_template(self: BotBase, cmd: str, message: Message, func: HandlingFunction):
    cmd = f"{USER_COMMAND_SYMBOL}{cmd}"
    if not message.content.startswith(cmd):
        return False

    run = func(message, self)
    if iscoroutine(run):
        await run

    await delete(message, self)
    return True


commands = [__help, __roll, __teams, __summon, __reminder]
commands.sort(key=lambda m: len(m.__name__), reverse=True)


def init_user_commands(self: BotBase):
    _init_reminders(self)


async def handle_user(self: BotBase, message: Message) -> bool:
    if not message.clean_content.strip().startswith(USER_COMMAND_SYMBOL):
        return False

    for command in commands:
        name = command.__name__[2:].replace("_", "-")
        if await __handling_template(self, name, message, command):
            return True

    await __handling_template(self, "", message, __unknown)
    return True


async def handle_user_reaction(self: BotBase, payload: RawReactionActionEvent, message: Message, channel: TextChannel):
    if await __handling_reaction_summon(self, payload, message, channel):
        return True

    return False
