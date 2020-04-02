from asyncio import iscoroutine
from typing import Union, Callable, Awaitable

from discord import Message

from misc import is_direct, delete, send

HandlingFunction = Union[Callable[[], None], Callable[[], Awaitable[None]]]


async def __handling_template(self, cmd: str, message: Message, func_dm: HandlingFunction, func_not_admin: HandlingFunction, func: HandlingFunction):
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


async def handle_system(self, message: Message) -> bool:
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
    
    if await __handling_template(self, "\\admin", message,
                                 lambda: send(message.author, message.channel, self, f"Für DM nicht sinnvoll."),
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

    if await __handling_template(self, "\\tts", message,
                                 lambda: send(message.author, message.channel, self, f"Sprachausgabe ist jetzt {'an' if (self.config.toggle_tts()) else 'aus'}"),
                                 lambda: send(message.author, message.channel, self, "Du bist nicht authorisiert!"),
                                 lambda: send(message.author, message.channel, self, f"TTS ist jetzt: {'an' if (self.config.toggle_tts()) else 'aus'}")
                                 ):
        return True

    return False
