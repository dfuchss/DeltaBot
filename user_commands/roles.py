from typing import Dict, Tuple, List, Union, Optional

from discord import Guild, Message, User, RawReactionActionEvent, TextChannel, Role, Member

from bot_base import command_meta, BotBase, send, delete
from loadable import Loadable
from user_commands.helpers import __crop_command
from utils import find_all_emojis
from .guild import _guild_state, get_guild


class RolesState(Loadable):
    """
    This state contains the states regarding role management.
    """

    def __init__(self):
        super().__init__(path="./states/roles_state.json", version=1)
        self._guild_to_message: Dict[str, List[int]] = {}
        self._load()

    def add_role_message(self, guild: Guild, message: Message) -> None:
        self._guild_to_message[str(guild.id)] = [message.channel.id, message.id]
        self._store()

    def remove_role_message(self, guild: Guild) -> None:
        del self._guild_to_message[str(guild.id)]
        self._store()

    def has_role_message(self, guild: Union[Guild, int]) -> bool:
        gid = str(guild.id) if isinstance(guild, Guild) else str(guild)
        return gid in self._guild_to_message.keys() \
               and self._guild_to_message[gid] is not None \
               and len(self._guild_to_message[gid]) != 0

    def get_role_message(self, guild: Union[Guild, int]) -> Tuple[int, int]:
        gid = str(guild.id) if isinstance(guild, Guild) else str(guild)
        return (None, None) if not self.has_role_message(guild) else tuple(self._guild_to_message[gid])

    def is_guild_message(self, guild_id: int, channel_id: int, message_id: int) -> bool:
        if str(guild_id) not in self._guild_to_message.keys():
            return False
        return [channel_id, message_id] == self._guild_to_message[str(guild_id)]


__roles_state = RolesState()
"""The one and only roles state"""


@command_meta(
    help_msg="Erzeugt eine Management-Nachricht für Rollen.",
    params=["[...]"],
    subcommands={
        "init": "Erzeugt eine neue Management-Nachricht",
        "add Emoji @Role": "Ordnet einer Rolle einen Emoji zu",
        "del Emoji": "Löscht ein Zuordnung anhand des Emoji",
        "reset": "Löscht die Management-Nachricht"
    })
async def __roles(message: Message, bot: BotBase) -> None:
    split_command = __crop_command(message.content).split(" ")

    if not bot.config.is_admin(message.author) and not _guild_state.is_guild_manager(message.guild, message.author):
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


__switcher_text = "**Wähle Deine Rollen auf diesem Server :)**"
__no_roles = "*Aktuell können keine Rollen gewählt werden .. warte auf den Gildenleiter :)*"


async def __ensure_guild_message(message: Message, bot: BotBase, shall_exist: bool) -> Optional[Guild]:
    guild: Guild = get_guild(message)
    if guild is None:
        msg = await send(message.author, message.channel, bot, "Leider gehört der Channel zu keiner Gilde")
        await delete(msg, bot, delay=10)
        return None

    if not shall_exist and __roles_state.has_role_message(guild):
        msg = await send(message.author, message.channel, bot,
                         "Für diese Gilde existiert schon eine Abstimmungsnachricht")
        await delete(msg, bot, delay=10)
        return None

    if shall_exist and not __roles_state.has_role_message(guild):
        msg = await send(message.author, message.channel, bot,
                         "Für diese Gilde existiert noch keine Abstimmungsnachricht")
        await delete(msg, bot, delay=10)
        return None

    return guild


async def __load_mappings(guild: Guild, bot: BotBase) -> Dict[str, str]:
    (cid, mid) = __roles_state.get_role_message(guild)

    ch: TextChannel = await bot.fetch_channel(cid)
    msg: Message = await ch.fetch_message(mid)

    mappings = {}
    for line in msg.content.split("\n"):
        data = line.strip().split("→")
        if len(data) == 2:
            mappings[data[0]] = data[1]

    return mappings


def __mapping_to_message(mappings: Dict[str, str]):
    if len(mappings.keys()) == 0:
        return f"{__switcher_text}\n\n{__no_roles}"

    res = __switcher_text + "\n\n"
    for emoji in mappings.keys():
        res += f"{emoji}→{mappings[emoji]}\n"
    res += f"\nBitte Reactions zum An- und Abwählen verwenden"
    return res.strip()


async def __role_chooser_init(message: Message, bot: BotBase):
    guild: Guild = await __ensure_guild_message(message, bot, False)
    if guild is None:
        return

    initial_text = f"{__switcher_text}\n{__no_roles}"
    resp = await send(message.author, message.channel, bot, initial_text, mention=False)
    __roles_state.add_role_message(guild, resp)


async def __role_chooser_add_role(message: Message, bot: BotBase):
    guild: Guild = await __ensure_guild_message(message, bot, True)
    if guild is None:
        return

    if len(message.role_mentions) != 1:
        msg = await send(message.author, message.channel, bot, "Du musst exakt eine Rolle nennen!")
        await delete(msg, bot, delay=10)
        return

    emojis = find_all_emojis(message.content)

    if len(emojis) != 1:
        msg = await send(message.author, message.channel, bot,
                         f"Ich konnte nicht genau einen Emoji finden :(\nIch fand insgesamt {len(emojis)} emojis")
        await delete(msg, bot, delay=10)
        return

    role = message.role_mentions[0]
    emoji = emojis[0]

    emoji_to_role_mappings: Dict[str, str] = await __load_mappings(guild, bot)

    if emoji in emoji_to_role_mappings.keys():
        msg = await send(message.author, message.channel, bot, f"Emoji ({emoji}) wird bereits verwendet")
        await delete(msg, bot, delay=10)
        return

    emoji_to_role_mappings[emoji] = role.mention

    (cid, mid) = __roles_state.get_role_message(guild)

    ch: TextChannel = await bot.fetch_channel(cid)
    guild_message: Message = await ch.fetch_message(mid)

    await guild_message.edit(content=__mapping_to_message(emoji_to_role_mappings))
    await guild_message.add_reaction(emoji)


async def __role_chooser_del_role(message: Message, bot: BotBase):
    guild: Guild = await __ensure_guild_message(message, bot, True)
    if guild is None:
        return

    emojis = find_all_emojis(message.content)
    if len(emojis) != 1:
        msg = await send(message.author, message.channel, bot,
                         f"Ich konnte nicht genau einen Emoji finden :(\nIch fand insgesamt {len(emojis)} emojis")
        await delete(msg, bot, delay=10)
        return

    emoji = emojis[0]
    emoji_to_role_mappings: Dict[str, str] = await __load_mappings(guild, bot)

    if emoji not in emoji_to_role_mappings.keys():
        msg = await send(message.author, message.channel, bot, "Emoji wird nicht verwendet")
        await delete(msg, bot, delay=10)
        return

    del emoji_to_role_mappings[emoji]

    (cid, mid) = __roles_state.get_role_message(guild)
    ch: TextChannel = await bot.fetch_channel(cid)
    guild_message: Message = await ch.fetch_message(mid)

    await guild_message.edit(content=__mapping_to_message(emoji_to_role_mappings))
    await guild_message.remove_reaction(emoji, bot.user)


async def __role_chooser_reset(message: Message, bot: BotBase):
    guild: Guild = await __ensure_guild_message(message, bot, True)
    if guild is None:
        return

    (cid, mid) = __roles_state.get_role_message(guild)

    ch: TextChannel = await bot.fetch_channel(cid)
    guild_message: Message = await ch.fetch_message(mid)

    __roles_state.remove_role_message(guild)
    await delete(guild_message, bot)


async def __handle_role_reaction(bot: BotBase, payload: RawReactionActionEvent, message: Message) -> bool:
    if not __roles_state.is_guild_message(payload.guild_id, payload.channel_id, payload.message_id):
        return False

    user: User = await bot.fetch_user(payload.user_id)
    await message.remove_reaction(payload.emoji, user)

    guild: Guild = get_guild(message)

    mappings: Dict[str, str] = await __load_mappings(guild, bot)

    # ID = <@&ID>
    role_id = mappings.get(str(payload.emoji), None)
    if role_id is None:
        msg = await send(message.author, message.channel, bot, "Emoji wird nicht verwendet")
        await delete(msg, bot, delay=10)
        return True

    roles: List[Role] = await guild.fetch_roles()
    role = list(filter(lambda r: str(r.id) in role_id, roles))
    if len(role) != 1:
        return True

    user_role: Role = role[0]
    member: Member = await guild.fetch_member(user.id)
    member_roles = [role.mention for role in member.roles]
    try:
        if user_role in member.roles:
            await member.remove_roles(user_role)
            member_roles.remove(user_role.mention)
        else:
            await member.add_roles(user_role)
            member_roles.append(user_role.mention)

        current_roles = list(filter(lambda m: m in member_roles, mappings.values()))
        resp = await send(user, message.channel, bot,
                          f"Deine von mir verwalteten Rollen sind aktuell: " +
                          f"{', '.join(current_roles) if len(current_roles) != 0 else '*NIX*'}")
        await delete(resp, bot, delay=10)
    except Exception:
        resp = await send(user, message.channel, bot, "Mir fehlen dafür wohl die Berechtigungen :(")
        await delete(resp, bot, delay=10)

    return True
