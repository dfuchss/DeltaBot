from typing import List, Optional

from discord import Message

from cognitive import IntentResult, EntityResult
from misc import BotBase
from dialog_management import Dialog, DialogResult
from misc import send, is_direct, delete


class Shutdown(Dialog):
    ID = "Shutdown"

    def __init__(self, bot: BotBase):
        super().__init__(bot, Shutdown.ID)

    def _load_initial_steps(self):
        self.add_step(self._shutdown_step)

    async def _shutdown_step(self, message: Message, intents: List[IntentResult], entities: List[EntityResult]):
        if not self._bot.config.is_admin(message.author):
            await send(message.author, message.channel, self._bot, "Du bist nicht berechtigt, mich zu deaktivieren!")
            return

        await self._bot.shutdown()


class Cleanup(Dialog):
    ID = "Cleanup"

    def __init__(self, bot: BotBase):
        super().__init__(bot, Cleanup.ID)
        self.__channel_user_msg: Optional[Message] = None

    def _load_initial_steps(self):
        self.add_step(self._ask_cleanup)
        self.add_step(self._vfy_cleanup)

    def reset(self):
        super().reset()
        self.__channel_user_msg = None

    async def _ask_cleanup(self, message: Message, intents: List[IntentResult], entities: List[EntityResult]):
        await send(message.author, message.channel, self._bot,
                   f"Soll ich alle Nachrichten von {str(message.author)} aus {str(message.channel)} und meine Nachrichten löschen? (Yes/No)?")
        self.__channel_user_msg = message
        return DialogResult.WAIT_FOR_INPUT

    async def _vfy_cleanup(self, message: Message, intents: List[IntentResult], entities: List[EntityResult]):
        if len(intents) != 0 and intents[0].name == "yes":
            self.add_step(self._cleanup_step)
        else:
            await send(message.author, message.channel, self._bot, f"Alles klar, wurde abgebrochen!")

        return DialogResult.NEXT

    async def _cleanup_step(self, message: Message, intents: List[IntentResult], entities: List[EntityResult]):
        if not self._bot.config.is_admin(message.author) or is_direct(self.__channel_user_msg):
            await send(message.author, message.channel, self._bot, f"Das kann ich leider nicht tun!")
            return

        author = self.__channel_user_msg.author
        async for m in self.__channel_user_msg.channel.history():
            if (
                    author == m.author or m.author == self._bot.get_bot_user()) and m.id != self.__channel_user_msg.id and m.id != message.id:
                await delete(m, self._bot, try_force=True)

        return DialogResult.NEXT
