from typing import Dict, List

from discord import Guild, User, Message

from bot_base import command_meta, send, delete, BotBase, NotFound
from loadable import Loadable
from utils import get_guild


class GuildState(Loadable):
    """
    This state contains the states regarding guilds
    """

    def __init__(self):
        super().__init__(path="./states/guild_state.json", version=1)
        self._guild_to_manager: Dict[str, List[int]] = {}
        self._load()

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

    async def guild_managers(self, guild: Guild, bot: BotBase) -> List[User]:
        managers = []
        for uid in self._guild_to_manager.get(str(guild.id), []):
            try:
                user = await bot.fetch_user(uid)
                managers.append(user)
            except NotFound:
                continue
        return managers


_guild_state = GuildState()
"""The one and only guild state"""


@command_meta(help_msg="Macht einen User zum Gildenleiter (oder nimmt die Macht)", params=["@Mention"])
async def __guild_manager(message: Message, bot: BotBase) -> None:
    guild: Guild = get_guild(message)
    if guild is None:
        msg = await send(message.author, message.channel, bot, "Leider gehört der Channel zu keiner Gilde")
        await delete(msg, bot, delay=10)
        return

    if len(message.mentions) == 0:
        msg = await send(message.author, message.channel, bot, "Leider habe ich keine Mentions gefunden")
        await delete(msg, bot, delay=10)
        return

    if not bot.config.is_admin(message.author) and not (_guild_state.is_guild_manager(guild, message.author)):
        msg = await send(message.author, message.channel, bot, "Du bist weder Admin noch Gildenleiter")
        await delete(msg, bot, delay=10)
        return

    for mention in message.mentions:
        u: User = mention
        if _guild_state.is_guild_manager(guild, u):
            _guild_state.remove_guild_manager(guild, u)
        else:
            _guild_state.add_guild_manager(guild, u)

    user_mentions = [manager.mention for manager in await _guild_state.guild_managers(guild, bot)]

    resp = await send(message.author, message.channel, bot,
                      f"Aktuelle Gildenleiter: {', '.join(user_mentions) if len(user_mentions) != 0 else 'Keine'}")
    await delete(resp, bot, 10)


@command_meta(help_msg="Zeigt die Gildenleiter an")
async def __show_guild_managers(message: Message, bot: BotBase) -> None:
    guild: Guild = get_guild(message)
    if guild is None:
        msg = await send(message.author, message.channel, bot, "Leider gehört der Channel zu keiner Gilde")
        await delete(msg, bot, delay=10)
        return

    if not bot.config.is_admin(message.author) and not (_guild_state.is_guild_manager(guild, message.author)):
        msg = await send(message.author, message.channel, bot, "Du bist weder Admin noch Gildenleiter")
        await delete(msg, bot, delay=10)
        return

    user_mentions = [manager.mention for manager in await _guild_state.guild_managers(guild, bot)]
    resp = await send(message.author, message.channel, bot,
                      f"Aktuelle Gildenleiter: {', '.join(user_mentions) if len(user_mentions) != 0 else 'Keine'}")
    await delete(resp, bot, 15)
