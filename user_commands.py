import re
from asyncio import iscoroutine
from typing import Union, Callable, Awaitable, Optional, List, Dict

from discord import Message, VoiceChannel, User, TextChannel, Role, RawReactionActionEvent

from misc import delete, send, BotBase, send_help_message

from random import randint, shuffle

HandlingFunction = Union[Callable[[], None], Callable[[], Awaitable[None]]]

USER_COMMAND_SYMBOL = "/"


def __crop_command(raw_message: str):
    msg = raw_message.split(" ", 1)
    if len(msg) == 1:
        return ""
    return msg[1].strip()


async def __handling_template(self: BotBase, cmd: str, message: Message, func: HandlingFunction):
    cmd = f"{USER_COMMAND_SYMBOL}{cmd}"
    if not message.content.startswith(cmd):
        return False

    run = func()
    if iscoroutine(run):
        await run

    await delete(message, self)
    return True


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


async def __roll(message, self):
    text: str = message.content
    dice = __read_number_param(text, 6)
    rnd = randint(1, dice)
    await send(message.author, message.channel, self, f"{rnd}")


__summon_reactions = ['\N{Thumbs Up Sign}', '\N{Black Question Mark Ornament}', '\N{Thumbs Down Sign}']


async def __summon(message: Message, self: BotBase):
    if len(message.role_mentions) == 0:
        await send(message.author, message.channel, self, f"Ich habe keine Gruppen gefunden ..")
        return

    user: User = message.author
    time: str = __crop_command(message.content)
    roles: List[Role] = message.role_mentions

    time = re.sub(r"<@&?\d+>", "", time)
    time = time.strip()

    response = f"Von {user.mention}: Wer wäre {'zur gewohnten Zeit' if len(time) == 0 else time} dabei? "
    for role in roles:
        response = f"{response} {role.mention}"

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
        if len(split) < 2 or split[0].strip() not in reactions:
            continue

        new_mentions = [m.strip() for m in split[1].split(",")]
        for m in new_mentions:
            if len(m) != 0:
                result[split[0]].append(m)

    return result


async def __handling_reaction_summon(self: BotBase, payload: RawReactionActionEvent, message: Message,
                                     channel: TextChannel):
    if message.author != self.user:
        return False

    text = message.content
    is_summon = re.match(r"^Von <@!?\d+>: Wer wäre", text) is not None

    if not is_summon:
        return False

    user: User = await self.fetch_user(payload.user_id)

    react = payload.emoji.name

    if react not in __summon_reactions:
        await message.remove_reaction(payload.emoji, user)
        return True

    reactions: Dict[str, List[str]] = __read_reactions(__summon_reactions, message.content)

    if user.mention in reactions[react]:
        reactions[react].remove(user.mention)
    else:
        reactions[react].append(user.mention)

    result_msg = message.content.split("\n")[0].strip()
    summary = ""

    for reaction in reactions.keys():
        users = reactions[reaction]
        if len(users) == 0:
            continue
        summary = f"{summary}\n{reaction}: {str.join(',', users)}"

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

    await send(message.author, message.channel, self, f"Zuordnung:\n{teams.strip()}", False)


async def handle_user(self: BotBase, message: Message) -> bool:
    if await __handling_template(self, "roll", message,
                                 lambda: __roll(message, self),
                                 ):
        return True

    if await __handling_template(self, "help", message, lambda: send_help_message(message, self)):
        return True

    if await __handling_template(self, "teams", message, lambda: __teams(message, self)):
        return True

    if await __handling_template(self, "summon", message, lambda: __summon(message, self)):
        return True

    if await __handling_template(self, "", message,
                                 lambda: send(message.author, message.channel, self, "Unbekannter Befehl")):
        return True

    return False


async def handle_user_reaction(self: BotBase, payload: RawReactionActionEvent, message: Message, channel: TextChannel):
    if await __handling_reaction_summon(self, payload, message, channel):
        return True

    return False
