import re
from typing import Dict, Tuple, List

from discord import Guild, Message, User, RawReactionActionEvent, NotFound, TextChannel, Role, Member

from bot_base import command_meta, BotBase, send, delete
from loadable import Loadable
from user_commands.helpers import __crop_command


class RolesState(Loadable):
    """
    This state contains the updates to summon messages from the bot (e.g. "tomorrow" -> "today" at midnight)
    """

    def __init__(self):
        super().__init__(path="./states/roles_state.json", version=1)
        self._guild_to_message: Dict[str, Tuple[int, int]] = {}
        self._guild_to_manager: Dict[str, List[int]] = {}
        self._load()

    def add_guild_message(self, guild: Guild, message: Message) -> None:
        self._guild_to_message[str(guild.id)] = (message.channel.id, message.id)
        self._store()

    def remove_guild_message(self, guild: Guild) -> None:
        del self._guild_to_message[str(guild.id)]
        self._store()

    def guild_messages(self) -> Dict[str, Tuple[int, int]]:
        return self._guild_to_message

    def add_guild_manager(self, guild: Guild, user: User):
        if guild.id in self._guild_to_manager.keys():
            self._guild_to_manager[str(guild.id)].append(user.id)
        else:
            self._guild_to_manager[str(guild.id)] = [user.id]

        self._store()

    def remove_guild_manager(self, guild: Guild, user: User):
        if guild.id in self._guild_to_manager.keys():
            self._guild_to_manager[str(guild.id)].remove(user.id)
            self._store()

    def is_guild_manager(self, guild: Guild, user: User) -> bool:
        if guild is None or str(guild.id) not in self._guild_to_manager.keys():
            return False
        return user.id in self._guild_to_manager[str(guild.id)]

    def get_guild_managers(self, guild: Guild) -> List[int]:
        if str(guild.id) not in self._guild_to_manager.keys():
            return []
        return self._guild_to_manager[str(guild.id)]


__roles_state = RolesState()
"""The one and only roles state"""


@command_meta(help_msg="Macht einen User zum Gilden-Manager (oder nimmt die Macht)", params=["@Mention"])
async def __guild_manager(message: Message, bot: BotBase) -> None:
    guild: Guild = message.guild
    if guild is None:
        msg = await send(message.author, message.channel, bot, "Leider gehört der Channel zu keiner Gilde")
        await delete(msg, bot, delay=10)
        return

    if len(message.mentions) == 0:
        msg = await send(message.author, message.channel, bot, "Leider habe ich keine Mentions gefunden")
        await delete(msg, bot, delay=10)
        return

    if not bot.config.is_admin(message.author) and not (__roles_state.is_guild_manager(guild, message.author)):
        msg = await send(message.author, message.channel, bot, "Du bist weder Admin noch Gilden-Manager")
        await delete(msg, bot, delay=10)
        return

    for mention in message.mentions:
        u: User = mention
        if __roles_state.is_guild_manager(guild, u):
            __roles_state.remove_guild_manager(guild, u)
        else:
            __roles_state.add_guild_manager(guild, u)

    user_mentions = []
    for uid in __roles_state.get_guild_managers(guild):
        try:
            user = await bot.fetch_user(uid)
            user_mentions.append(user.mention)
        except NotFound:
            continue

    resp = await send(message.author, message.channel, bot,
                      f"Aktuelle Gilden-Manager: {', '.join(user_mentions) if len(user_mentions) != 0 else 'Keine'}")
    await delete(resp, bot, 10)


@command_meta(
    help_msg="Erzeugt eine Management-Nachricht für Rollen.",
    params=["init | add Emoji @Mention | del Emoji | reset"])
async def __roles(message: Message, bot: BotBase) -> None:
    split_command = __crop_command(message.content).split(" ")

    if not bot.config.is_admin(message.author) and not __roles_state.is_guild_manager(message.guild, message.author):
        resp = await send(message.author, message.channel, bot, "Du bist weder Admin noch Gilden-Manager")
        await delete(resp, bot, delay=10)
        return

    if len(split_command) == 0 or split_command[0] not in ["init", "add", "del", "reset"]:
        resp = await send(message.author, message.channel, bot, "Du musst einen der Sub-Befehle verwenden:\n"
                          + "* init: erzeugt eine die Abstimmungsnachricht für die Rollen\n"
                          + "* add Emoji @Mention: fügt einen Emoji für die entsprechende Rolle hinzu\n"
                          + "* del Emoji: löscht die Rollenzuordnung für ein Emoji\n"
                          + "* reset: löscht die Abstimmungsnachricht"
                          )
        await delete(resp, bot, delay=20)
        return

    switcher = {
        "init": __role_chooser_init,
        "add": __role_chooser_add_role,
        "del": __role_chooser_del_role,
        "reset": __role_chooser_reset
    }

    sub_command = switcher.get(split_command[0])
    await sub_command(message, bot)
    await delete(message, bot)


__switcher_text = "Wähle Deine Rollen in dem Server :)"
__no_roles = "*Aktuell können keine Rollen gewählt werden ..*"


async def __role_chooser_init(message: Message, bot: BotBase):
    guild: Guild = message.guild
    if guild is None:
        msg = await send(message.author, message.channel, bot, "Leider gehört der Channel zu keiner Gilde")
        await delete(msg, bot, delay=10)
        return

    guild_messages = __roles_state.guild_messages()
    if str(guild.id) in guild_messages.keys():
        msg = await send(message.author, message.channel, bot,
                         "Für diese Gilde existiert schon eine Abstimmungsnachricht")
        await delete(msg, bot, delay=10)
        return

    initial_text = f"{__switcher_text}\n{__no_roles}"
    resp = await send(message.author, message.channel, bot, initial_text, mention=False)
    __roles_state.add_guild_message(guild, resp)


async def _load_mappings(guild: Guild, bot: BotBase) -> Dict[str, str]:
    (cid, mid) = __roles_state.guild_messages()[str(guild.id)]

    ch: TextChannel = await bot.fetch_channel(cid)
    msg: Message = await ch.fetch_message(mid)

    mappings = {}
    for line in msg.content.split("\n"):
        data = line.strip().split("→")
        if len(data) == 2:
            mappings[data[0]] = data[1]

    return mappings


def _mapping_to_message(mappings: Dict[str, str]):
    if len(mappings.keys()) == 0:
        return f"{__switcher_text}\n\n{__no_roles}"

    res = __switcher_text + "\n\n"
    for emoji in mappings.keys():
        res += f"{emoji}→{mappings[emoji]}\n"
    res += f"\nBitte Reactions zum An- und Abwählen verwenden"
    return res.strip()


async def __role_chooser_add_role(message: Message, bot: BotBase):
    guild: Guild = message.guild
    if guild is None:
        msg = await send(message.author, message.channel, bot, "Leider gehört der Channel zu keiner Gilde")
        await delete(msg, bot, delay=10)
        return

    guild_messages = __roles_state.guild_messages()
    if str(guild.id) not in guild_messages.keys():
        msg = await send(message.author, message.channel, bot,
                         "Für diese Gilde existiert noch keine Abstimmungsnachricht")
        await delete(msg, bot, delay=10)
        return

    if len(message.role_mentions) != 1:
        msg = await send(message.author, message.channel, bot, "Du musst exakt eine Rolle nennen!")
        await delete(msg, bot, delay=10)
        return

    # ["add", "emoji", "role"]
    data = re.sub(r"\s+", " ", __crop_command(message.content)).strip().split(" ")
    if len(data) != 3 or (len(data[1]) != 1 and re.match(r"<:[A-Za-z0-9-]+:\d+", data[1]) is None):
        msg = await send(message.author, message.channel, bot, "Ich konnte den Emoji nicht finden :(")
        await delete(msg, bot, delay=10)
        return

    role = message.role_mentions[0]
    emoji = data[1]

    emoji_to_role_mappings: Dict[str, str] = await _load_mappings(guild, bot)

    if emoji in emoji_to_role_mappings.keys():
        msg = await send(message.author, message.channel, bot, f"Emoji ({emoji}) wird bereits verwendet")
        await delete(msg, bot, delay=10)
        return

    emoji_to_role_mappings[emoji] = role.mention

    (cid, mid) = __roles_state.guild_messages()[str(guild.id)]

    ch: TextChannel = await bot.fetch_channel(cid)
    guild_message: Message = await ch.fetch_message(mid)

    await guild_message.edit(content=_mapping_to_message(emoji_to_role_mappings))
    await guild_message.add_reaction(emoji)


async def __role_chooser_del_role(message: Message, bot: BotBase):
    guild: Guild = message.guild
    if guild is None:
        msg = await send(message.author, message.channel, bot, "Leider gehört der Channel zu keiner Gilde")
        await delete(msg, bot, delay=10)
        return

    guild_messages = __roles_state.guild_messages()
    if str(guild.id) not in guild_messages.keys():
        msg = await send(message.author, message.channel, bot,
                         "Für diese Gilde existiert noch keine Abstimmungsnachricht")
        await delete(msg, bot, delay=10)
        return

    # ["del", "emoji"]
    data = re.sub(r"\s+", " ", __crop_command(message.content)).strip().split(" ")
    if len(data) != 2 or (len(data[1]) != 1 and re.match(r"<:[A-Za-z0-9-]+:\d+", data[1]) is None):
        msg = await send(message.author, message.channel, bot, "Ich konnte den Emoji nicht finden :(")
        await delete(msg, bot, delay=10)
        return

    emoji = data[1]
    emoji_to_role_mappings: Dict[str, str] = await _load_mappings(guild, bot)

    if emoji not in emoji_to_role_mappings.keys():
        msg = await send(message.author, message.channel, bot, "Emoji wird nicht verwendet")
        await delete(msg, bot, delay=10)
        return

    del emoji_to_role_mappings[emoji]

    (cid, mid) = __roles_state.guild_messages()[str(guild.id)]
    ch: TextChannel = await bot.fetch_channel(cid)
    guild_message: Message = await ch.fetch_message(mid)

    await guild_message.edit(content=_mapping_to_message(emoji_to_role_mappings))
    await guild_message.remove_reaction(emoji, bot.user)


async def __role_chooser_reset(message: Message, bot: BotBase):
    guild: Guild = message.guild
    if guild is None:
        msg = await send(message.author, message.channel, bot, "Leider gehört der Channel zu keiner Gilde")
        await delete(msg, bot, delay=10)
        return

    guild_messages = __roles_state.guild_messages()
    if str(guild.id) not in guild_messages.keys():
        msg = await send(message.author, message.channel, bot,
                         "Für diese Gilde existiert noch keine Abstimmungsnachricht")
        await delete(msg, bot, delay=10)
        return

    (cid, mid) = __roles_state.guild_messages()[str(guild.id)]

    ch: TextChannel = await bot.fetch_channel(cid)
    guild_message: Message = await ch.fetch_message(mid)

    __roles_state.remove_guild_message(guild)
    await delete(guild_message, bot)


async def __handle_role_reaction(bot: BotBase, payload: RawReactionActionEvent, message: Message) -> bool:
    if str(payload.guild_id) not in __roles_state.guild_messages().keys():
        return False

    (cid, mid) = __roles_state.guild_messages()[str(payload.guild_id)]
    if cid != payload.channel_id or mid != payload.message_id:
        return False

    user: User = await bot.fetch_user(payload.user_id)
    await message.remove_reaction(payload.emoji, user)

    guild: Guild = message.guild

    mappings: Dict[str, str] = await _load_mappings(guild, bot)

    # ID = <@&ID>
    role_id = mappings[str(payload.emoji)]

    roles: List[Role] = await guild.fetch_roles()
    role = list(filter(lambda r: str(r.id) in role_id, roles))
    if len(role) != 1:
        return True

    user_role: Role = role[0]
    member: Member = await guild.fetch_member(user.id)
    try:
        if user_role in member.roles:
            await member.remove_roles(user_role)
        else:
            await member.add_roles(user_role)

        resp = await send(message.author, message.channel, bot, "Änderung der Rollen vorgenommen :)")
        await delete(resp, bot, delay=10)
    except Exception:
        resp = await send(message.author, message.channel, bot, "Mir fehlen dafür wohl die Berechtigungen :(")
        await delete(resp, bot, delay=10)

    return True
