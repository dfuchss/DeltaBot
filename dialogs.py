from calendar import timegm
from json import loads, dumps
from random import choice
from typing import List, Dict

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
        await send(message.author, message.channel, self._bot, "Das war zu viel für mich :( Ich hab das nicht verstanden")
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

    def _load_initial_steps(self):
        self.add_step(self._cleanup_step)

    async def _cleanup_step(self, message: Message, intents: List[IntentResult], entities: List[EntityResult]):
        if not self._bot.is_admin(message.author) or is_direct(message):
            return

        async for m in message.channel.history():
            if (message.author == m.author or m.author == self._bot.get_bot_user()) and m.id != message.id:
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
        response = self.__enhance(response, message)
        await send(message.author, message.channel, self._bot, response)

        return DialogResult.NEXT

    @staticmethod
    def __enhance(response: str, reference: Message) -> str:
        """ Enhances the response by information like user, channel etc.
        :param response the message to enhance
        :param reference the reference message (from the user)
        :return the new response
        """
        result = response.replace("#USER", reference.author.name)
        if is_direct(reference):
            channel_name = f"@{reference.author.name}"
        else:
            channel_name = f"@{reference.channel.name}"
        result = result.replace("#CHANNEL", channel_name)
        return result


class QnAAnswer(Dialog):
    ID = "QnAAnswer"

    def __init__(self, bot: DeltaBot):
        super().__init__(bot, QnAAnswer.ID)

    def _load_initial_steps(self):
        self.add_step(self._qna_step)

    async def _qna_step(self, message: Message, intents: List[IntentResult], entities: List[EntityResult]):
        if not self._bot.is_admin(message.author):
            await send(message.author, message.channel, self._bot, f"Dazu hast Du keine Berechtigung")
            return DialogResult.NEXT

        text: str = message.content
        # Command, qna, text
        spl = text.split(" ", 2)
        if len(spl) != 3:
            await send(message.author, message.channel, self._bot, f"Ich finde keinen gültigen Text")
            return

        name = f"QnA/{spl[1]}.json"
        if not exists(name):
            await send(message.author, message.channel, self._bot, f"Ich finde keinen Eintrag für {name}")
            return

        qna_file = open(name, "r", encoding="utf-8-sig")
        response: list = loads(qna_file.read().strip())
        qna_file.close()

        response.insert(0, spl[2])

        qna_file = open(name, "w", encoding="utf-8-sig")
        qna_file.write(dumps(response, indent=True))
        qna_file.close()

        await send(message.author, message.channel, self._bot, f"Habs hinzugefügt zu {name}")
        return DialogResult.NEXT


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
        self.add_step(self._news_step)

    async def _news_step(self, message: Message, intents: List[IntentResult], entities: List[EntityResult]):
        entities = filter(lambda e: e.group == "news", entities)
        categories = list(map(lambda e: e.name, entities))
        if not categories:
            categories = ["Allgemein"]

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
                    response += "Nix, rein gar nichts .."
                await send(message.author, message.channel, self._bot, response)

        return DialogResult.NEXT
