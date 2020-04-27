from asyncio import iscoroutine
from typing import Union, Callable, Awaitable

from discord import Message

from misc import is_direct, delete, send, BotBase

HandlingFunction = Union[Callable[[], None], Callable[[], Awaitable[None]]]


async def __handling_template(self: BotBase, cmd: str, message: Message, func_dm: HandlingFunction, func_not_admin: HandlingFunction, func: HandlingFunction):
    if not message.content.startswith(cmd):
        return False

    if is_direct(message):
        run = func_dm()
        if iscoroutine(run):
            await run

        return True

    if not self.is_admin(message.author):
        run = func_not_admin()
        if iscoroutine(run):
            await run

        await delete(message, self)
        return True

    run = func()
    if iscoroutine(run):
        await run

    await delete(message, self)
    return True


async def __state(user, channel, bot: BotBase):
    msg = "Aktueller Zustand:\n"
    msg += f"NLU-Threshold: {bot.config.nlu_threshold}\n"
    msg += f"Entities: {bot.config.entity_file}\n"
    msg += f"TTL: {bot.config.ttl}\n"
    msg += f"Channels: {', '.join(map(str, bot.channels))}\n"
    msg += f"Admins: {', '.join(map(str, bot.admins))}\n"
    msg += f"Debug: {bot.config.debug_indicator}\n"
    msg += f"Listen-All: {bot.config.listen_all}\n"
    msg += f"Keep-Messages: {bot.config.keep_messages}"
    await send(user, channel, bot, msg)


async def handle_system(self: BotBase, message: Message) -> bool:
    if await __handling_template(self, "\\listen-all", message,
                                 lambda: send(message.author, message.channel, self, f"Immer Antworten-Modus ist jetzt {'an' if (self.config.toggle_listen_all()) else 'aus'}"),
                                 lambda: send(message.author, message.channel, self, "Du bist nicht authorisiert!"),
                                 lambda: send(message.author, message.channel, self, f"Immer Antworten-Modus ist jetzt {'an' if (self.config.toggle_listen_all()) else 'aus'}")
                                 ):
        return True

    if await __handling_template(self, "\\listen", message,
                                 lambda: send(message.author, message.channel, self, "Ich höre Dich schon!"),
                                 lambda: send(message.author, message.channel, self, "Du bist nicht authorisiert!"),
                                 lambda: self.channels.append(message.channel.id)
                                 ):
        return True

    if await __handling_template(self, "\\keep", message,
                                 lambda: send(message.author, message.channel, self, f"Nachrichten löschen ist jetzt {'aus' if (self.config.toggle_keep_messages()) else 'an'}"),
                                 lambda: send(message.author, message.channel, self, "Du bist nicht authorisiert!"),
                                 lambda: send(message.author, message.channel, self, f"Nachrichten löschen ist jetzt {'aus' if (self.config.toggle_keep_messages()) else 'an'}")
                                 ):
        return True

    if await __handling_template(self, "\\admin", message,
                                 lambda: send(message.author, message.channel, self, "Das geht nicht im Privaten channel!"),
                                 lambda: send(message.author, message.channel, self, "Du bist nicht authorisiert!"),
                                 lambda: self.add_admins(message)
                                 ):
        return True

    if await __handling_template(self, "\\echo", message,
                                 lambda: message.channel.send(message.content.replace("<", "").replace(">", "")),
                                 lambda: send(message.author, message.channel, self, "Du bist nicht authorisiert!"),
                                 lambda: message.channel.send(message.content.replace("<", "").replace(">", ""))
                                 ):
        return True

    if await __handling_template(self, "\\state", message,
                                 lambda: __state(message.author, message.channel, self),
                                 lambda: send(message.author, message.channel, self, "Du bist nicht authorisiert!"),
                                 lambda: __state(message.author, message.channel, self)
                                 ):
        return True

    return False
