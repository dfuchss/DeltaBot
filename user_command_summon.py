import datetime
import re
from random import choice
from typing import List, Dict

from discord import Message, User, Role, TextChannel, RawReactionActionEvent

from loadable import Loadable
from misc import BotBase, send, delete, is_direct
from user_command_helpers import __crop_command, find_day_by_special_key, days


class SummonState(Loadable):
    def __init__(self):
        super().__init__(path="./states/summon_state.json", version=1)
        self._updates = []
        self._load()

    def add_update(self, reminder):
        self._updates.append(reminder)
        self._store()

    def remove_update(self, reminder):
        self._updates.remove(reminder)
        self._store()

    def updates(self):
        return self._updates


__summon_state = SummonState()

__summon_reactions = ['\N{Thumbs Up Sign}', '\N{Black Question Mark Ornament}', '\N{Thumbs Down Sign}']

__summon_msg = [f"###USER###: Wer wäre ###DAY### ###TIME### dabei? ###MENTION###",  #
                f"Wer hätte ###DAY### ###TIME### Lust ###MENTION### (###USER###)",  #
                f"Jemand ###DAY### ###TIME### Bock auf ###MENTION### (###USER###)",  #
                f"Finden sich ###DAY### ###TIME### Leute ###MENTION### (###USER###)"
                ]
__summon_rgx = [r"^<@!?\d+>: Wer wäre",  #
                r"\(<@!?\d+>\)$"
                ]


def __add_to_scheduler(self: BotBase, resp_message: Message, offset: int, day: str):
    if offset <= -1:
        return

    next_time = datetime.datetime.now().replace(hour=0, minute=0, second=0, microsecond=0) + datetime.timedelta(days=1)

    data = {
        "ts": next_time.timestamp(),
        "cid": resp_message.channel.id,
        "mid": resp_message.id,
        "day_value": day,
        "day_offset": offset
    }
    __summon_state.add_update(data)
    self.scheduler.queue(__execute_summon_update(data, self), data["ts"])


async def __execute_summon_update(u: dict, self: BotBase):
    __summon_state.remove_update(u)

    cid = u["cid"]
    mid = u["mid"]
    day_value = u["day_value"]
    day_offset = u["day_offset"]

    ch: TextChannel = await self.fetch_channel(cid)
    msg: Message = await ch.fetch_message(mid)

    new_day_offset = day_offset - 1
    _, _, new_day_value = (None, None, "'damals (am)'") if new_day_offset < 0 else days[new_day_offset]
    new_day_value = f"**{new_day_value}**"

    new_content = msg.content.replace(day_value, new_day_value)
    await msg.edit(content=new_content)

    # Schedule new change ..
    __add_to_scheduler(self, msg, new_day_offset, new_day_value)


async def __summon(message: Message, self: BotBase):
    if is_direct(message):
        await send(message.author, message.channel, self, "Summon funktioniert nur in Text-Channeln")
        return

    if len(message.role_mentions) == 0:
        await send(message.author, message.channel, self, f"Ich habe keine Gruppen gefunden ..")
        return

    user: User = message.author
    spec: str = __crop_command(message.content)
    roles: List[Role] = message.role_mentions

    spec = re.sub(r"<@&?\d+>", "", spec).strip()

    # Calc Day Offset ..
    offset, match, default_val = find_day_by_special_key(spec)
    if offset is None or offset == 0:
        day = "**heute**"
        offset = 0
    else:
        spec = spec.replace(match, "")
        day = f"**{default_val}**"

    spec = re.sub(r"\s+", spec, " ").strip()
    response = choice(__summon_msg)
    response = response.replace("###USER###", user.mention)
    response = response.replace("###MENTION###", " ".join([r.mention for r in roles]))
    response = response.replace("###TIME###", 'zur gewohnten Zeit' if len(spec) == 0 else spec)
    response = response.replace("###DAY###", day)

    response += "\n\nBitte Reactions zum Abstimmen benutzen:"

    channel: TextChannel = message.channel
    resp_message: Message = await channel.send(response)
    for react in __summon_reactions:
        await resp_message.add_reaction(react)

    await delete(message, self, True)

    __add_to_scheduler(self, resp_message, offset, day)


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


def _init_summon_updates(self: BotBase):
    for u in __summon_state.updates():
        self.scheduler.queue(__execute_summon_update(u, self), u["ts"])
