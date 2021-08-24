import datetime
import re
from random import choice
from typing import List, Dict, Optional, Tuple

from discord import Message, User, TextChannel, NotFound
from discord_components import Select, SelectOption

from bot_base import BotBase, send, delete, is_direct, command_meta
from loadable import DictStore
from utils import find_day_by_special_rgx, get_date_representation
from .helpers import __crop_command

__summon_state = DictStore("./states/summon_state.json")
"""The one and only summon state"""

__summon_reactions = [("Sicher dabei :)", '\N{Thumbs Up Sign}'),  #
                      ("Eher dabei", '\N{Thumbs Up Sign} | \N{Black Question Mark Ornament}'),  #
                      ("Unsicher", '\N{Black Question Mark Ornament}'),  #
                      ("Eher nicht", '\N{Black Question Mark Ornament} | \N{Thumbs Down Sign}'),  #
                      ("Sicher nicht :(", '\N{Thumbs Down Sign}')  #
                      ]
"""All allowed reactions to a summon message from the bot"""
__finish_reaction = ("Umfrage beenden", '\N{Chequered Flag}')
"""Reaction to finish a summon command"""
__cancel_reaction = ("Umfrage löschen", '\N{Put Litter in Its Place Symbol}')
"""Reaction to cancel a summon command"""

__poll_finished = "*Umfrage beendet .. keine Abstimmung mehr möglich :)*"
"""Message used to indicate a finished poll for the users"""

__summon_msg = [f"###USER###: Wer wäre ###DAY### ###TIME### dabei? ###MENTION###",  #
                f"Wer hätte ###DAY### ###TIME### Lust ###MENTION### (###USER###)",  #
                f"Jemand ###DAY### ###TIME### Bock auf ###MENTION### (###USER###)",  #
                f"Finden sich ###DAY### ###TIME### Leute ###MENTION### (###USER###)"
                ]
"""All Templates for summon messages"""


def __get_selections() -> List[SelectOption]:
    text_x_value = __summon_reactions + [__finish_reaction, __cancel_reaction]
    options = [SelectOption(label=f"{v[0]} {v[1]}", value=v[1]) for v in text_x_value]
    return options


def __add_to_scheduler(bot: BotBase, user_id: int, resp_message: Message, offset: int, day: str) -> None:
    """
    Add a summon day update to the scheduler.

    :param bot: the bot itself
    :param user_id the user that opened the poll
    :param resp_message: the message to be changed in the future
    :param offset: the current day_offset
    :param day: the current representation of the day
    """
    if offset <= -1:
        return

    next_time = datetime.datetime.now().replace(hour=0, minute=0, second=0, microsecond=0) + datetime.timedelta(days=1)
    # For testing:
    # next_time = datetime.datetime.now() + datetime.timedelta(seconds=5)

    data = {
        "ts": next_time.timestamp(),
        "cid": resp_message.channel.id,
        "mid": resp_message.id,
        "uid": user_id,
        "day_value": day,
        "day_offset": offset
    }
    __summon_state.add_data(data)
    bot.scheduler.queue(__execute_summon_update(data, bot), data["ts"])


async def __terminate_summon_poll(msg: Message):
    """
    Terminates / Finalizes a summon poll

    :param msg: the message associated with the poll
    """
    new_content = msg.content
    new_content = new_content + f"\n\n{__poll_finished}"
    await msg.edit(content=new_content, components=[])

    if msg.pinned:
        await msg.unpin()


async def __execute_summon_update(u: dict, bot: BotBase) -> None:
    """
    Execute the update of a summon text from the bot.

    :param u: the data for the update as dictionary
    :param bot: the bot itself
    """
    __summon_state.remove_data(u)

    cid = u["cid"]
    mid = u["mid"]
    day_value = u["day_value"]
    day_offset = u["day_offset"]

    try:
        ch: TextChannel = await bot.fetch_channel(cid)
        msg: Message = await ch.fetch_message(mid)
    except NotFound:
        return

    new_day_offset = day_offset - 1

    if new_day_offset < 0:
        await __terminate_summon_poll(msg)
        return

    new_day_value = f"**{get_date_representation(new_day_offset)}**"
    new_content = msg.content.replace(day_value, new_day_value)

    # Create a new message
    await msg.edit(content=new_content)

    # Schedule new change ..
    __add_to_scheduler(bot, u["uid"], msg, new_day_offset, new_day_value)


async def _find_roles(message: Message, spec: str, user: User, bot: BotBase) -> Optional[Tuple[List[str], str]]:
    """
    Find mentioned roles and update specification string for response message

    :param message: the original message
    :param spec: the remaining text from the message
    :param user: the user that responded to the message
    :param bot: the bot itself
    :return: None if none role found, (mentioned roles as list, updated spec text) otherwise
    """
    if len(message.role_mentions) != 0:
        # Try to search "@Game" for non mentionable games ..
        roles: List[str] = [r.mention for r in message.role_mentions]
        for r in roles:
            spec = spec.replace(r, "")
        return roles, spec

    elif re.search(r"@[A-Za-z0-9-_]+", message.content) is not None:
        roles: List[str] = re.findall(r"@[A-Za-z0-9-_]+", message.content)
        for r in roles:
            spec = spec.replace(r, "")

        return list(map(lambda r: f"**{r}**", roles)), spec
    else:
        await send(user, message.channel, bot, f"Ich habe keine Gruppen gefunden ..")
        return None


def __read_text_reactions(reactions: List[str], message_content: str) -> Dict[str, List[str]]:
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


async def __check_cancel(emoji: str, message: Message, update: dict, user: User, bot: BotBase) -> bool:
    """
    Check whether a user wants to cancel the /summon poll.

    :param emoji: the emoji that has been used
    :param message: the message a user reacts
    :param update: the associated update object
    :param user: the user
    :param bot: the bot itself
    :return: indicator whether someone requested a cancel
    """
    if emoji != __cancel_reaction[1]:
        return False

    if user.id != update["uid"]:
        resp = await send(user, message.channel, bot, "Nur der Initiator kann die Umfrage löschen")
        await delete(resp, bot, delay=10)
    else:
        __summon_state.remove_data(update)
        await message.delete()
    return True


async def __check_finish(emoji: str, message: Message, update: dict, user: User, bot: BotBase) -> bool:
    """
    Check whether a user wants to finish the /summon poll.

    :param emoji: the emoji that has been used
    :param message: the message a user reacts
    :param update: the associated update object
    :param user: the user
    :param bot: the bot itself
    :return: indicator whether someone requested a cancel
    """
    if emoji != __finish_reaction[1]:
        return False

    if user.id != update["uid"]:
        resp = await send(user, message.channel, bot, "Nur der Initiator kann die Umfrage beenden")
        await delete(resp, bot, delay=10)
    else:
        __summon_state.remove_data(update)
        await __terminate_summon_poll(message)
    return True


async def __update_message(user: User, selections: List[str], message: Message):
    """
    Update a message according to the user reactions.

    :param user: the user that reacted
    :param selections: the selections of the user
    :param message: the message to change
    """
    text = message.content

    reactions: Dict[str, List[str]] = __read_text_reactions([r[1] for r in __summon_reactions], text)

    for emoji in reactions.keys():
        if user.mention in reactions[emoji] and emoji not in selections:
            reactions[emoji].remove(user.mention)
        if user.mention not in reactions[emoji] and emoji in selections:
            reactions[emoji].append(user.mention)

    result_msg = text.split("\n")[0].strip()
    summary = ""

    for reaction in reactions.keys():
        users = reactions[reaction]
        if len(users) == 0:
            continue
        summary = f"{summary}\n{reaction} `({len(users)})`: {str.join(',', users)}"

    if len(summary) != 0:
        result_msg = f"{result_msg}\n\nAktuell:\n{summary.strip()}"

    await message.edit(content=result_msg)


async def __delete_pinning_response(bot: BotBase, channel: TextChannel, pinned_message: Message):
    """
    Directly after pinning delete the pinning response for bot messages

    :param bot: the bot
    :param channel: the channel where a message has been pinned
    :param pinned_message: the pinned message
    """
    last_message = await channel.history(limit=1).flatten()
    if len(last_message) != 1:
        return

    last_message = last_message[0]

    if last_message.author != bot.user:
        return

    if last_message.id == pinned_message.id:
        return

    if last_message.reference is None or last_message.reference.message_id != pinned_message.id:
        return

    await last_message.delete()


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

    user: User = message.author
    spec: str = __crop_command(message.content)

    found_roles_x_new_spec = await _find_roles(message, spec, user, bot)
    if found_roles_x_new_spec is None:
        return

    roles, spec = found_roles_x_new_spec

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
    response = response.replace("###MENTION###", " ".join(roles))
    response = response.replace("###TIME###", 'zur gewohnten Zeit' if len(spec) == 0 else spec)
    response = response.replace("###DAY###", day)

    channel: TextChannel = message.channel
    resp_message: Message = await channel.send(response)

    await resp_message.edit(components=[Select(placeholder="Hier bitte wählen ..", options=__get_selections())])
    await resp_message.pin()

    # Delete "DeltaBot pinned a message to this channel. See pinned messages"
    await __delete_pinning_response(bot, channel, resp_message)

    await delete(message, bot, True)

    __add_to_scheduler(bot, message.author.id, resp_message, offset, day)


async def __handling_selection_summon(bot: BotBase, payload: dict, message: Message, selection_id: str,
                                      selections: List[str],
                                      user_id: int) -> bool:
    """
    Handle selections for summon user commands.

    :param bot: the bot itself
    :param payload: the raw payload from discord
    :param message: the message which belongs to the button
    :param selection_id: the id of the selection object
    :param selections the list of selected elements
    :param user_id: the id of the user who pressed the button
    :return: indicator whether the button was related to a user command
    """
    if message.author != bot.user:
        return False

    update = list(filter(lambda m: m["mid"] == message.id and m["cid"] == message.channel.id, __summon_state.data()))

    if len(update) == 0:
        return False

    update = update[0]
    user: User = await bot.fetch_user(user_id)

    if len(selections) == 1 and await __check_cancel(selections[0], message, update, user, bot):
        return True

    if len(selections) == 1 and await __check_finish(selections[0], message, update, user, bot):
        return True

    if any([s not in [v[1] for v in __summon_reactions] for s in selections]):
        # Do anything to clear selection ..
        await message.edit(content=message.content)
        return True

    await __update_message(user, selections, message)
    return True


def _init_summon_updates(bot: BotBase) -> None:
    """
    Initialize the bot's scheduler with summon updates.

    :param bot: the bot itself
    """
    for u in __summon_state.data():
        bot.scheduler.queue(__execute_summon_update(u, bot), u["ts"])
