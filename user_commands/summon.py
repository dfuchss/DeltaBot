import datetime
import re
from random import choice
from typing import List, Dict

from discord import Message, User, Role, TextChannel, RawReactionActionEvent, NotFound

from constants import DAYS
from loadable import Loadable
from bot_base import BotBase, send, delete, is_direct, command_meta
from .helpers import __crop_command, find_day_by_special_rgx


class SummonState(Loadable):
    """
    This state contains the updates to summon messages from the bot (e.g. "tomorrow" -> "today" at midnight)
    """

    def __init__(self):
        super().__init__(path="./states/summon_state.json", version=1)
        self._updates = []
        self._load()

    def add_update(self, update: dict) -> None:
        """
        Add a new update.

        :param update: the data as dictionary (see __summon)
        """
        self._updates.append(update)
        self._store()

    def remove_update(self, update: dict) -> None:
        """
        Remove an update.

        :param update: the data as dictionary (see __summon)
        """
        self._updates.remove(update)
        self._store()

    def updates(self) -> List[dict]:
        """
        Return all updates as list of dictionaries

        :return: all updates
        """
        return self._updates


__summon_state = SummonState()
"""The one and only summon state"""

__summon_reactions = ['\N{Thumbs Up Sign}', '\N{Black Question Mark Ornament}', '\N{Thumbs Down Sign}']
"""All allowed reactions to a summon message from the bot"""

__summon_msg = [f"###USER###: Wer wäre ###DAY### ###TIME### dabei? ###MENTION###",  #
                f"Wer hätte ###DAY### ###TIME### Lust ###MENTION### (###USER###)",  #
                f"Jemand ###DAY### ###TIME### Bock auf ###MENTION### (###USER###)",  #
                f"Finden sich ###DAY### ###TIME### Leute ###MENTION### (###USER###)"
                ]
"""All Templates for summon messages"""

__summon_rgx = [r"^<@!?\d+>: Wer wäre",  #
                r"\(<@!?\d+>\)$"
                ]
"""All regexes to match __summon_msg"""


def __add_to_scheduler(bot: BotBase, resp_message: Message, offset: int, day: str) -> None:
    """
    Add a summon day update to the scheduler.

    :param bot: the bot itself
    :param resp_message: the message to be changed in the future
    :param offset: the current day_offset
    :param day: the current representation of the day
    """
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
    bot.scheduler.queue(__execute_summon_update(data, bot), data["ts"])


async def __execute_summon_update(u: dict, bot: BotBase) -> None:
    """
    Execute the update of a summon text from the bot.

    :param u: the data for the update as dictionary
    :param bot: the bot itself
    """
    __summon_state.remove_update(u)

    cid = u["cid"]
    mid = u["mid"]
    day_value = u["day_value"]
    day_offset = u["day_offset"]

    try:
        ch: TextChannel = await bot.fetch_channel(cid)
    except NotFound:
        return

    try:
        msg: Message = await ch.fetch_message(mid)
    except NotFound:
        return

    new_day_offset = day_offset - 1
    _, _, new_day_value = (None, None, "'damals (am/um)'") if new_day_offset < 0 else DAYS[new_day_offset]
    new_day_value = f"**{new_day_value}**"

    new_content = msg.content.replace(day_value, new_day_value)
    await msg.edit(content=new_content)

    # Schedule new change ..
    __add_to_scheduler(bot, msg, new_day_offset, new_day_value)


@command_meta(help_msg="Erzeugt eine Umfrage an alle @Mentions für eine optionale Zeit.",
              params=["@Mentions", "[Zeit]"])
async def __summon(message: Message, bot: BotBase) -> None:
    """
    Create a poll to gather mentioned groups a a certain time.

    :param message: the message from the user
    :param bot: the bot itself
    """
    if is_direct(message):
        await send(message.author, message.channel, bot, "/summon funktioniert nicht in DM channels")
        return

    if len(message.role_mentions) == 0:
        await send(message.author, message.channel, bot, f"Ich habe keine Gruppen gefunden ..")
        return

    user: User = message.author
    spec: str = __crop_command(message.content)
    roles: List[Role] = message.role_mentions

    spec = re.sub(r"<@&?\d+>", "", spec).strip()

    # Calc Day Offset ..
    offset, match_start, match_end, default_val = find_day_by_special_rgx(spec)
    if offset is None:
        day = "**heute**"
        offset = 0
    else:
        spec = spec[:match_start] + spec[(match_end + 1):]
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

    await delete(message, bot, True)

    __add_to_scheduler(bot, resp_message, offset, day)


def __read_reactions(reactions: List[str], message_content: str) -> Dict[str, List[str]]:
    """
    Read reactions from a message (reactions are stored in the message text)

    :param reactions: the allowed reactions
    :param message_content: the content of the message
    :return: a dictionary reaction->[user mentions]
    """
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


async def __handling_reaction_summon(bot: BotBase, payload: RawReactionActionEvent, message: Message) -> bool:
    """
    Handle the reactions to a summon response from the bot

    :param bot: the bot itself
    :param payload: the raw payload of the reaction add operation
    :param message: the message the user responds to
    :return: indicator whether the reaction has been handled
    """
    if message.author != bot.user:
        return False

    text = message.content
    is_summon = any([re.search(rgx, text.split("\n")[0].strip()) is not None for rgx in __summon_rgx])

    if not is_summon:
        return False

    user: User = await bot.fetch_user(payload.user_id)

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


def _init_summon_updates(bot: BotBase) -> None:
    """
    Initialize the bot's scheduler with summon updates.

    :param bot: the bot itself
    """
    for u in __summon_state.updates():
        bot.scheduler.queue(__execute_summon_update(u, bot), u["ts"])
