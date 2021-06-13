from asyncio import iscoroutine
from enum import Enum
from typing import Union, Callable, Awaitable

from discord import Message, TextChannel

from constants import SYSTEM_COMMAND_SYMBOL
from misc import is_direct, delete, send, BotBase, command_meta


class SystemCommandCallState(Enum):
    DIRECT_MESSAGE = 0
    NO_ADMIN = 1
    VALID = 2


async def __to_users(bot: BotBase, uids):
    users = []
    for uid in uids:
        user = await bot.fetch_user(uid)
        users.append(user)
    return users


def __not_authorized(self: BotBase, message: Message):
    return send(message.author, message.channel, self, "Du bist nicht authorisiert!")


@command_meta(is_system_command=True, help_msg="Schaltet die Notwendigkeit von Mentions ab/an")
def __respond_all(state: SystemCommandCallState, bot_base: BotBase, message: Message):
    if state == SystemCommandCallState.NO_ADMIN:
        return __not_authorized(bot_base, message)

    if state == SystemCommandCallState.DIRECT_MESSAGE or state == SystemCommandCallState.VALID:
        return send(message.author, message.channel, bot_base,
                    f"Immer Antworten-Modus ist jetzt {'an' if (bot_base.config.toggle_respond_all()) else 'aus'}"  #
                    )

    raise Exception(f"Impossible system command call state: {state}")


@command_meta(is_system_command=True, help_msg="Aktiviert mein Zuhören in einem Text-Channel")
def __listen(state: SystemCommandCallState, bot_base: BotBase, message: Message):
    if state == SystemCommandCallState.NO_ADMIN:
        return __not_authorized(bot_base, message)

    if state == SystemCommandCallState.DIRECT_MESSAGE:
        return send(message.author, message.channel, bot_base, "Ich höre Dich schon!")

    if state == SystemCommandCallState.VALID:
        channel: TextChannel = message.channel
        bot_base.config.add_channel(channel.id)
        return send(message.author, channel, bot_base, f"Ich höre jetzt auf {channel.mention}")

    raise Exception(f"Impossible system command call state: {state}")


@command_meta(is_system_command=True, help_msg="Toggle für das Löschen von Nachrichten")
def __keep(state: SystemCommandCallState, bot_base: BotBase, message: Message):
    if state == SystemCommandCallState.NO_ADMIN:
        return __not_authorized(bot_base, message)

    if state == SystemCommandCallState.DIRECT_MESSAGE or state == SystemCommandCallState.VALID:
        return send(message.author, message.channel, bot_base,
                    f"Nachrichten löschen ist jetzt {'aus' if (bot_base.config.toggle_keep_messages()) else 'an'}"  #
                    )

    raise Exception(f"Impossible system command call state: {state}")


@command_meta(is_system_command=True, help_msg="Macht jeden genannten Benutzer zum Admin", params=["@Mentions"])
async def __admin(state: SystemCommandCallState, bot_base: BotBase, message: Message):
    if state == SystemCommandCallState.NO_ADMIN:
        await __not_authorized(bot_base, message)
        return

    if state == SystemCommandCallState.DIRECT_MESSAGE or state == SystemCommandCallState.VALID:
        bot_base.config.add_admins(message)
        users = await __to_users(bot_base, bot_base.config.get_admins())
        await send(message.author, message.channel, bot_base,
                   f"Admins: {', '.join(map(lambda uid: uid.mention, users))}")
        return

    raise Exception(f"Impossible system command call state: {state}")


@command_meta(is_system_command=True, help_msg="Liefert den folgenden Text mit allen IDs")
def __echo(state: SystemCommandCallState, bot_base: BotBase, message: Message):
    if state == SystemCommandCallState.NO_ADMIN:
        return __not_authorized(bot_base, message)

    if state == SystemCommandCallState.DIRECT_MESSAGE or state == SystemCommandCallState.VALID:
        return message.channel.send(message.content.replace("<", "").replace(">", ""))

    raise Exception(f"Impossible system command call state: {state}")


@command_meta(is_system_command=True, help_msg="Zeigt den Zustand des Bots an")
async def __state(state: SystemCommandCallState, bot_base: BotBase, message: Message):
    if state == SystemCommandCallState.NO_ADMIN:
        await __not_authorized(bot_base, message)
        return

    if state == SystemCommandCallState.DIRECT_MESSAGE or state == SystemCommandCallState.VALID:
        users = await __to_users(bot_base, bot_base.config.get_admins())

        msg = "Aktueller Zustand:\n"
        msg += f"NLU-Threshold: {bot_base.config.nlu_threshold}\n"
        msg += f"Entities: {bot_base.config.entity_file}\n"
        msg += f"TTL: {bot_base.config.ttl}\n"
        msg += f"Channels: {', '.join(map(str, bot_base.config.get_channels()))}\n"
        msg += f"Admins: {', '.join(map(lambda uid: uid.mention, users))}\n"
        msg += f"Debug: {bot_base.config.is_debug()}\n"
        msg += f"Respond-All: {bot_base.config.is_respond_all()}\n"
        msg += f"Keep-Messages: {bot_base.config.is_keep_messages()}"
        await send(message.author, message.channel, bot_base, msg)
        return

    raise Exception(f"Impossible system command call state: {state}")


@command_meta(is_system_command=True, help_msg="Fährt den Bot herunter")
async def __shutdown(state: SystemCommandCallState, bot_base: BotBase, message: Message):
    if state == SystemCommandCallState.NO_ADMIN:
        await __not_authorized(bot_base, message)
        return

    if state == SystemCommandCallState.DIRECT_MESSAGE or state == SystemCommandCallState.VALID:
        if bot_base.config.is_keep_messages():
            await delete(message, bot_base, try_force=True)
        await bot_base.shutdown()
        return

    raise Exception(f"Impossible system command call state: {state}")


@command_meta(is_system_command=True, help_msg="Löscht alle Nachrichten eine Kanals")
async def __erase(state: SystemCommandCallState, bot_base: BotBase, message: Message):
    if state == SystemCommandCallState.NO_ADMIN:
        await __not_authorized(bot_base, message)
        return

    if state == SystemCommandCallState.DIRECT_MESSAGE:
        await send(message.author, message.channel, bot_base, "Das ist bei DMs unmöglich.")
        return

    if state == SystemCommandCallState.VALID:
        async for m in message.channel.history():
            if m.id != message.id:
                await delete(m, bot_base, try_force=True)

        if bot_base.config.is_keep_messages():
            await delete(message, bot_base, try_force=True)

        return

    raise Exception(f"Impossible system command call state: {state}")


@command_meta(is_system_command=True, help_msg="Toggle für den Debug-Modus")
def __debug(state: SystemCommandCallState, bot_base: BotBase, message: Message):
    if state == SystemCommandCallState.NO_ADMIN:
        return __not_authorized(bot_base, message)

    if state == SystemCommandCallState.DIRECT_MESSAGE or state == SystemCommandCallState.VALID:
        return send(message.author, message.channel, bot_base,
                    f"Entwicklermodus ist jetzt: {bot_base.config.toggle_debug()}"  #
                    )

    raise Exception(f"Impossible system command call state: {state}")


def __unknown(state: SystemCommandCallState, bot_base: BotBase, message: Message):
    return send(message.author, message.channel, bot_base, "Unbekannter Befehl")


HandlingFunction = Union[  #
    Callable[[SystemCommandCallState, BotBase, Message], None],  #
    Callable[[SystemCommandCallState, BotBase, Message], Awaitable[None]]  #
]


async def __handling_template(self: BotBase, cmd: str, message: Message, handler: HandlingFunction):
    cmd = f"{SYSTEM_COMMAND_SYMBOL}{cmd}"
    if not message.content.startswith(cmd):
        return False

    if not self.config.is_admin(message.author):
        run = handler(SystemCommandCallState.NO_ADMIN, self, message)
        if iscoroutine(run):
            await run

        await delete(message, self)
        return True

    if is_direct(message):
        run = handler(SystemCommandCallState.DIRECT_MESSAGE, self, message)
        if iscoroutine(run):
            await run

        return True

    run = handler(SystemCommandCallState.VALID, self, message)
    if iscoroutine(run):
        await run

    await delete(message, self)
    return True


commands = [__respond_all, __listen, __keep, __admin, __echo, __state, __shutdown, __erase, __debug]
commands.sort(key=lambda m: len(m.__name__), reverse=True)


async def handle_system(self: BotBase, message: Message) -> bool:
    if not message.clean_content.strip().startswith(SYSTEM_COMMAND_SYMBOL):
        return False

    for command in commands:
        name = command.__name__[2:].replace("_", "-")
        if await __handling_template(self, name, message, command):
            return True

    await __handling_template(self, "", message, __unknown)
    return True
