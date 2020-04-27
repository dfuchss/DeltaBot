from calendar import timegm
from json import loads, dumps
from random import choice
from typing import List, Dict, Optional

from discord import Message
from feedparser import parse

from cognitive import IntentResult, EntityResult
from deltabot import DeltaBot
from dialog_management import Dialog, DialogResult
from misc import send, is_direct, delete
from genericpath import exists
from datetime import datetime


class NotUnderstanding(Dialog):
    ID = "NotUnderstanding"

    def __init__(self, bot: DeltaBot):
        super().__init__(bot, NotUnderstanding.ID)

    def _load_initial_steps(self):
        self.add_step(self._not_understood_step)

    async def _not_understood_step(self, message: Message, intents: List[IntentResult], entities: List[EntityResult]):
        responses = open("QnA/NotUnderstanding.json", "r", encoding="utf-8-sig")
        response = loads(responses.read().strip())
        responses.close()
        response = choice(response)
        response = self.enhance(response, message)
        await send(message.author, message.channel, self._bot, response)
        return DialogResult.NEXT


class Debug(Dialog):
    ID = "Debug"

    def __init__(self, bot: DeltaBot):
        super().__init__(bot, Debug.ID)

    def _load_initial_steps(self):
        self.add_step(self._toggle_debug_step)

    async def _toggle_debug_step(self, message: Message, intents: List[IntentResult], entities: List[EntityResult]):
        await send(message.author, message.channel, self._bot, f"Entwicklermodus ist jetzt: {self._bot.config.toggle_debug()}")
        return DialogResult.NEXT


class Shutdown(Dialog):
    ID = "Shutdown"

    def __init__(self, bot: DeltaBot):
        super().__init__(bot, Shutdown.ID)

    def _load_initial_steps(self):
        self.add_step(self._shutdown_step)

    async def _shutdown_step(self, message: Message, intents: List[IntentResult], entities: List[EntityResult]):
        if not self._bot.is_admin(message.author):
            await send(message.author, message.channel, self._bot, "Du bist nicht berechtigt, mich zu deaktivieren!")
            return

        await self._bot.shutdown()

        return DialogResult.NEXT


class Cleanup(Dialog):
    ID = "Cleanup"

    def __init__(self, bot: DeltaBot):
        super().__init__(bot, Cleanup.ID)
        self.__channel_user_msg: Optional[Message] = None

    def _load_initial_steps(self):
        self.add_step(self._ask_cleanup)
        self.add_step(self._vfy_cleanup)

    def reset(self):
        super().reset()
        self.__channel_user_msg = None

    async def _ask_cleanup(self, message: Message, intents: List[IntentResult], entities: List[EntityResult]):
        await send(message.author, message.channel, self._bot, f"Soll ich alle Nachrichten von {str(message.author)} aus {str(message.channel)} und meine Nachrichten löschen? (Yes/No)?")
        self.__channel_user_msg = message
        return DialogResult.WAIT_FOR_INPUT

    async def _vfy_cleanup(self, message: Message, intents: List[IntentResult], entities: List[EntityResult]):
        if len(intents) != 0 and intents[0].name == "yes":
            self.add_step(self._cleanup_step)
        else:
            await send(message.author, message.channel, self._bot, f"Alles klar, wurde abgebrochen!")

        return DialogResult.NEXT

    async def _cleanup_step(self, message: Message, intents: List[IntentResult], entities: List[EntityResult]):
        if not self._bot.is_admin(message.author) or is_direct(self.__channel_user_msg):
            await send(message.author, message.channel, self._bot, f"Das kann ich leider nicht tun!")
            return

        author = self.__channel_user_msg.author
        async for m in self.__channel_user_msg.channel.history():
            if (author == m.author or m.author == self._bot.get_bot_user()) and m.id != self.__channel_user_msg.id and m.id != message.id:
                await delete(m, self._bot, try_force=True)

        return DialogResult.NEXT


class QnA(Dialog):
    ID = "QnA"

    def __init__(self, bot: DeltaBot):
        super().__init__(bot, QnA.ID)

    def _load_initial_steps(self):
        self.add_step(self._qna_step)

    async def _qna_step(self, message: Message, intents: List[IntentResult], entities: List[EntityResult]):
        intent = intents[0].name[4:]
        name = f"QnA/{intent}.json"
        if not exists(name):
            await send(message.author, message.channel, self._bot, f"Ich finde keinen Eintrag für {intent}")
            return

        qna_file = open(name, "r", encoding="utf-8-sig")
        response = loads(qna_file.read().strip())
        qna_file.close()
        response = choice(response)
        response = self.enhance(response, message)
        await send(message.author, message.channel, self._bot, response)

        return DialogResult.NEXT


class QnAAnswer(Dialog):
    ID = "QnAAnswer"

    def __init__(self, bot: DeltaBot):
        super().__init__(bot, QnAAnswer.ID)
        self._qna = None
        self._text = None

    def _load_initial_steps(self):
        self.add_step(self._find_qna)

    async def _find_qna(self, message: Message, intents: List[IntentResult], entities: List[EntityResult]):
        if not self._bot.is_admin(message.author):
            await send(message.author, message.channel, self._bot, f"Dazu hast Du keine Berechtigung")
            return DialogResult.NEXT

        await send(message.author, message.channel, self._bot, f"Für welches QnA soll ein neuer Text gespeichert werden?")
        self.add_step(self._store_qna_ask_text)
        return DialogResult.WAIT_FOR_INPUT

    async def _store_qna_ask_text(self, message: Message, intents: List[IntentResult], entities: List[EntityResult]):
        name = f"QnA/{message.content}.json"
        if not exists(name):
            await send(message.author, message.channel, self._bot, f"Ich finde keinen Eintrag für {name}")
            return DialogResult.NEXT

        self._qna = name
        await send(message.author, message.channel, self._bot, f"Bitte gib jetzt Deinen Text für {name} ein:")
        self.add_step(self._store_text_ask_confirmation)
        return DialogResult.WAIT_FOR_INPUT

    async def _store_text_ask_confirmation(self, message: Message, intents: List[IntentResult], entities: List[EntityResult]):
        self._text = message.content
        await send(message.author, message.channel, self._bot, f"Soll ich in {self._qna} '{self._text}' speichern? (Y/N)")
        self.add_step(self._qna_step)
        return DialogResult.WAIT_FOR_INPUT

    async def _qna_step(self, message: Message, intents: List[IntentResult], entities: List[EntityResult]):
        if len(intents) == 0 or intents[0].name != "yes":
            await send(message.author, message.channel, self._bot, f"OK. Keine Änderung vorgenommen!")
            return DialogResult.NEXT

        qna_file = open(self._qna, "r", encoding="utf-8-sig")
        response: list = loads(qna_file.read().strip())
        qna_file.close()

        response.insert(0, self._text)

        qna_file = open(self._qna, "w", encoding="utf-8-sig")
        qna_file.write(dumps(response, indent=True))
        qna_file.close()

        await send(message.author, message.channel, self._bot, f"Habs hinzugefügt!")
        return DialogResult.NEXT

    def reset(self):
        super().reset()
        self._qna = None
        self._text = None


class Clock(Dialog):
    ID = "Clock"

    def __init__(self, bot: DeltaBot):
        super().__init__(bot, Clock.ID)

    def _load_initial_steps(self):
        self.add_step(self._time_step)

    async def _time_step(self, message: Message, intents: List[IntentResult], entities: List[EntityResult]):
        await send(message.author, message.channel, self._bot, f"{datetime.now()}")
        return DialogResult.NEXT


class News(Dialog):
    ID = "News"

    @staticmethod
    def to_date(time_tuple):
        """ Converts a time tuple to actual time. """
        return datetime.fromtimestamp(timegm(time_tuple))

    MaxNews: int = 10

    class NewsProvider:
        """ Defines a simple provider (rss) for news. """

        def __init__(self, name, url):
            """Create a new news provider
            :param name the name of the provider
            :param url the url to the rss feed of the provider
            """
            self.name = name
            self.url = url

    Providers: Dict[str, List[NewsProvider]] = {
        "Allgemein": [
            NewsProvider("Tagesschau", "https://www.tagesschau.de/xml/rss2"),
            NewsProvider("heise online", "https://www.heise.de/rss/heise-top-atom.xml")
        ],
        "Sport": [NewsProvider("Sport1", "https://www.sport1.de/news.rss")],
        "IT": [NewsProvider("heise online", "https://www.heise.de/rss/heise-top-atom.xml")],
        "Netcup": [NewsProvider("Netcup", "https://www.netcup-sonderangebote.de/feed")]
    }

    def __init__(self, bot: DeltaBot):
        super().__init__(bot, News.ID)

    def _load_initial_steps(self):
        self.add_step(self._select_news)

    async def _select_news(self, message: Message, intents: List[IntentResult], entities: List[EntityResult]):
        entities = filter(lambda e: e.group == "news", entities)
        categories = list(map(lambda e: e.name, entities))
        if len(categories) != 0:
            self.add_step(self._news_step)
            return DialogResult.NEXT

        await send(message.author, message.channel, self._bot, f"Für welche Kategorie(n) willst Du Nachrichten?\n{', '.join(self.Providers.keys())}")
        self.add_step(self._news_step)
        return DialogResult.WAIT_FOR_INPUT

    async def _news_step(self, message: Message, intents: List[IntentResult], entities: List[EntityResult]):
        entities = filter(lambda e: e.group == "news", entities)
        categories = list(map(lambda e: e.name, entities))
        if not categories:
            await send(message.author, message.channel, self._bot, "Leider habe ich keine Kategorie erkannt. Dialog erstmal beenden ;)")
            return DialogResult.NEXT

        sent = False
        for category in categories:
            for provider in self.Providers[category]:
                response = f"\n**{provider.name}**\n"
                news_feed = parse(provider.url).entries
                news_feed = filter(lambda e: self.to_date(e.published_parsed).date() == datetime.today().date(),
                                   news_feed)
                news_feed = sorted(news_feed, key=lambda e: self.to_date(e.published_parsed).time(), reverse=True)

                for idx, news in enumerate(news_feed):
                    if idx >= self.MaxNews:
                        break
                    url = news["id"]
                    url = url.replace("https://www.", "https://")
                    url = url.replace("http://www.", "http://")
                    response += news["title"]
                    response += f" (<{url}>)\n"
                if len(news_feed) == 0:
                    # response += "Nix, rein gar nichts .."
                    sent = True
                await send(message.author, message.channel, self._bot, response)

        if not sent:
            await send(message.author, message.channel, self._bot, "Keine neuen Nachrichten.")
        return DialogResult.NEXT
