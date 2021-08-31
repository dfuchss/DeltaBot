import datetime
import re
from random import choice
from typing import List, Dict, Optional, Tuple, Union

from discord import Message, User, TextChannel, NotFound, Guild, Emoji
from discord_components import Button, ButtonStyle

from bot_base import BotBase, send, delete, is_direct, command_meta
from loadable import DictStore
from utils import find_day_by_special_rgx, get_date_representation, get_guild, create_button_grid
from .helpers import __crop_command

__summon_state = DictStore("./states/summon_state.json")
"""The one and only summon state"""

__summon_reactions_default = ['\N{Thumbs Up Sign}', '\N{Thinking Face}',  #
                              '\N{Black Question Mark Ornament}',
                              '\N{Pensive Face}', '\N{Thumbs Down Sign}'  #
                              ]
"""All allowed reactions to a summon message from the bot"""

__summon_reactions_mid_filenames = ["./user_commands/assets/ThumbsUp45.png", "./user_commands/assets/ThumbsUp135.png"]
"""Special images for position 1 and 3 in __summon_reactions_default"""

__summon_reactions_colors = [ButtonStyle.green, ButtonStyle.grey, ButtonStyle.grey, ButtonStyle.grey, ButtonStyle.red]
"""All reactions colors to a summon message from the bot"""

__finish_reaction = '\N{Octagonal Sign}'
"""Reaction to finish a summon command"""
__cancel_reaction = '\N{Put Litter in Its Place Symbol}'
"""Reaction to cancel a summon command"""

__poll_request = "Bitte die Buttons zum Abstimmen benutzen:"
"""Message used to indicate a poll for the users"""
__poll_finished = "*Umfrage beendet .. keine Abstimmung mehr möglich :)*"
"""Message used to indicate a finished poll for the users"""

__summon_msg = [f"###USER###: Wer wäre ###DAY### ###TIME### dabei? ###MENTION###",  #
                f"Wer hätte ###DAY### ###TIME### Lust ###MENTION### (###USER###)",  #
                f"Jemand ###DAY### ###TIME### Bock auf ###MENTION### (###USER###)",  #
                f"Finden sich ###DAY### ###TIME### Leute ###MENTION### (###USER###)"
                ]
"""All Templates for summon messages"""


def __to_emoji_ids(reactions: Union[List[Union[str, Emoji]], Union[str, Emoji]]):
    if isinstance(reactions, List):
        return [r if isinstance(r, str) else r.name for r in reactions]

    return reactions if isinstance(reactions, str) else reactions.name


async def __register_emojis(guild: Guild, found_summon_emojis: List[Emoji], summon_emojis: List[str],
                            __summon_reactions_mid_filenames: List[str]):
    print(f"Registering Summon Emojis in {guild.name}")

    for (emoji, path) in zip(summon_emojis, __summon_reactions_mid_filenames):
        if emoji in [e.name for e in found_summon_emojis]:
            continue
        with open(path, "rb") as emoji_file:
            data = emoji_file.read()
        await guild.create_custom_emoji(name=emoji, image=data)


async def __get_summon_reactions(guild: Guild) -> List[Union[str, Emoji]]:
    if guild is None:
        return __summon_reactions_default

    try:
        all_emojis: List[Emoji] = await guild.fetch_emojis()
        summon_emojis = [e.split(".")[-2].split("/")[-1] for e in __summon_reactions_mid_filenames]
        found_summon_emojis = list(filter(lambda e: e.name in summon_emojis, all_emojis))
        if len(found_summon_emojis) != len(summon_emojis):
            await __register_emojis(guild, found_summon_emojis, summon_emojis, __summon_reactions_mid_filenames)
            all_emojis = await guild.fetch_emojis()
            found_summon_emojis = list(filter(lambda e: e.name in summon_emojis, all_emojis))

        # Registered Emojis
        emojis = list(__summon_reactions_default)
        # Replace with custom emojis
        emojis[1] = found_summon_emojis[0]
        emojis[3] = found_summon_emojis[1]

        return emojis

    except Exception as e:
        print(f"Missing Permissions to add Emojis, or other error for Guild {guild.name}", e)
        return __summon_reactions_default


async def __get_buttons(guild: Guild) -> List[List[Button]]:
    summon_reactions = await __get_summon_reactions(guild)
    buttons = [Button(emoji=emoji, custom_id=__to_emoji_ids(emoji), style=style)  #
               for emoji, style in zip(summon_reactions, __summon_reactions_colors)]

    buttons.append(Button(emoji=__finish_reaction, custom_id=__finish_reaction))
    buttons.append(Button(emoji=__cancel_reaction, custom_id=__cancel_reaction))

    return create_button_grid(buttons, try_mod_zero=False)


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
    new_content = new_content.replace(__poll_request, __poll_finished)
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


def __find_mention_type(line: str, reactions: List[str]):
    reactions_sorted = list(sorted(reactions, key=lambda r: len(r) if r else -1, reverse=True))
    for reaction in reactions_sorted:
        if line.startswith(reaction):
            return reaction
    return None


def __read_text_reactions(reactions: List[Union[str, Emoji]], message_content: str) -> Dict[str, List[str]]:
    """
    Read reactions from a message (reactions are stored in the message text)

    :param reactions: the allowed reactions
    :param message_content: the content of the message
    :return: a dictionary reaction->[user mentions], if reaction is an emoji object, the name will be used as identifier
    """

    reaction_ids = [r if isinstance(r, str) else f"{r.name}" for r in reactions]

    # Cleanup Emoji stuff
    for emoji in reactions:
        if isinstance(emoji, str):
            continue
        message_content = message_content.replace(f"{emoji}", emoji.name)

    result = {}
    for reaction in reaction_ids:
        result[reaction] = []

    for line in message_content.split("\n"):
        if line is None or len(line.strip()) == 0:
            continue
        split = line.split(":", 1)

        mention_type = __find_mention_type(line, reaction_ids)

        if len(split) < 2 or mention_type is None:
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
    if emoji != __cancel_reaction:
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
    if emoji != __finish_reaction:
        return False

    if user.id != update["uid"]:
        resp = await send(user, message.channel, bot, "Nur der Initiator kann die Umfrage beenden")
        await delete(resp, bot, delay=10)
    else:
        __summon_state.remove_data(update)
        await __terminate_summon_poll(message)
    return True


async def __update_message(user: User, emoji_id: str, message: Message):
    """
    Update a message according to the user reactions.

    :param user: the user that reacted
    :param emoji_id: the emoji the user has used
    :param message: the message to change
    """
    text = message.content

    all_reactions = await __get_summon_reactions(get_guild(message))
    reactions: Dict[str, List[str]] = __read_text_reactions(all_reactions, text)

    for emoji in reactions.keys():
        if emoji != emoji_id and user.mention in reactions[emoji]:
            # Remove at any other place
            reactions[emoji].remove(user.mention)

        if emoji != emoji_id:
            continue

        if user.mention in reactions[emoji_id]:
            reactions[emoji_id].remove(user.mention)
        else:
            reactions[emoji_id].append(user.mention)

    result_msg = text.split("\n")[0].strip()
    summary = ""

    for reaction in reactions.keys():
        users = reactions[reaction]
        if len(users) == 0:
            continue
        emoji = reaction if reaction in all_reactions else next(
            filter(lambda r: isinstance(r, Emoji) and r.name == reaction, all_reactions))
        summary = f"{summary}\n{emoji} `({len(users)})`: {str.join(',', users)}"

    if len(summary) != 0:
        result_msg = f"{result_msg}\n\nAktuell:\n{summary.strip()}"

    result_msg += f"\n\n{__poll_request}"
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

    response += f"\n\n{__poll_request}"

    channel: TextChannel = message.channel
    resp_message: Message = await channel.send(response)

    await resp_message.edit(components=await __get_buttons(get_guild(message)))
    await resp_message.pin()

    # Delete "DeltaBot pinned a message to this channel. See pinned messages"
    await __delete_pinning_response(bot, channel, resp_message)

    await delete(message, bot, True)

    __add_to_scheduler(bot, message.author.id, resp_message, offset, day)


async def __handling_button_summon(bot: BotBase, payload: dict, message: Message, button_id: str, user_id: int) -> bool:
    """
    Handle pressed buttons for summon user commands.

    :param bot: the bot itself
    :param payload: the raw payload from discord
    :param message: the message which belongs to the button
    :param button_id: the id of the pressed button
    :param user_id: the id of the user who pressed the button
    :return: indicator whether the button was related to this command
    """
    if message.author != bot.user:
        return False

    update = list(filter(lambda m: m["mid"] == message.id and m["cid"] == message.channel.id, __summon_state.data()))

    if len(update) == 0:
        return False

    update = update[0]
    user: User = await bot.fetch_user(user_id)

    if await __check_cancel(button_id, message, update, user, bot):
        return True

    if await __check_finish(button_id, message, update, user, bot):
        return True

    reactions = await __get_summon_reactions(get_guild(message))
    if button_id not in __to_emoji_ids(reactions):
        return True

    await __update_message(user, button_id, message)
    return True


def _init_summon_updates(bot: BotBase) -> None:
    """
    Initialize the bot's scheduler with summon updates.

    :param bot: the bot itself
    """
    for u in __summon_state.data():
        bot.scheduler.queue(__execute_summon_update(u, bot), u["ts"])
