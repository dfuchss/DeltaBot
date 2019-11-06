from calendar import timegm
from datetime import datetime
from os.path import exists
from typing import List, Dict
from random import choice

from discord import Message
from feedparser import parse
from json import loads, dumps

from cognitive import EntityResult, IntentResult
from misc import Bot, delete, is_direct, send


class Handler:
    """Defines a handler for a message event"""

    async def handle(self, bot: Bot, nlu_result: (List[IntentResult], List[EntityResult]), message: Message):
        """ Entry point to the handler.
        :param bot the bot
        :param nlu_result the result of NLU classification
        :param message the raw message
        """
        await send(message.author, message.channel, bot, f"{type(self).__name__}: NIY")


class CleanupHandler(Handler):
    """ Deletes all messages from the author and the bot itself from a channel. """

    async def handle(self, bot: Bot, nlu_result: (List[IntentResult], List[EntityResult]), message: Message):
        if not bot.is_admin(message.author) or is_direct(message):
            return

        async for m in message.channel.history():
            if (message.author == m.author or m.author == bot.get_bot_user()) and m.id != message.id:
                await delete(m, bot, try_force=True)


class TestHandler(Handler):
    """ Just for testing .."""
    pass


class NoneHandler(Handler):
    """ Handler for not classified messages. """

    async def handle(self, bot: Bot, nlu_result: (List[IntentResult], List[EntityResult]), message: Message):
        await send(message.author, message.channel, bot, "Das war zu viel für mich :( Ich hab das nicht verstanden")


class QnAHandler(Handler):
    """ Handler for QnA/Simple Responses."""

    async def handle(self, bot: Bot, nlu_result: (List[IntentResult], List[EntityResult]), message: Message):
        (intents, _) = nlu_result
        intent = intents[0].name[4:]
        name = f"QnA/{intent}.json"
        if not exists(name):
            await send(message.author, message.channel, bot, f"Ich finde keinen Eintrag für {intent}")
            return

        qna_file = open(name, "r", encoding="utf-8-sig")
        response = loads(qna_file.read().strip())
        qna_file.close()
        response = choice(response)
        await send(message.author, message.channel, bot, response)


class ClockHandler(Handler):
    """ Handler to get the current time. """

    async def handle(self, bot: Bot, nlu_result: (List[IntentResult], List[EntityResult]), message: Message):
        await send(message.author, message.channel, bot, datetime.now())


class DebugHandler(Handler):
    """ Handler which toggles Debug state. """

    async def handle(self, bot: Bot, nlu_result: (List[IntentResult], List[EntityResult]), message: Message):
        await send(message.author, message.channel, bot, f"Entwicklermodus ist jetzt: {bot.toggle_debug()}")


class NewsHandler(Handler):
    """ Handler which provides current news. """

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

    @staticmethod
    def to_date(time_tuple):
        """ Converts a time tuple to actual time. """
        return datetime.fromtimestamp(timegm(time_tuple))

    MaxNews: int = 10

    async def handle(self, bot: Bot, nlu_result: (List[IntentResult], List[EntityResult]), message: Message):
        entities = nlu_result[1]
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
                await send(message.author, message.channel, bot, response)


class LoLHandler(Handler):
    """ First skeleton (WIP) of a LoL handler which can help choosing runes and builds for different champions. """

    async def handle(self, bot: Bot, nlu_result: (List[IntentResult], List[EntityResult]), message: Message):
        entities = nlu_result[1]
        entities = list(map(lambda e: e.name, filter(lambda e: e.group == "lol", entities)))
        if len(entities) == 0:
            await send(message.author, message.channel, bot, "Du hast keinen mir bekannten Champion genannt ..")
            return

        for champion in entities:
            await self._handle_champion(champion, bot, message)

    async def _handle_champion(self, champion: str, bot: Bot, message: Message):
        """ Handle information extraction for a specific champion. """
        pass


class ShutdownHandler(Handler):
    """ Shutdown the bot. """

    async def handle(self, bot: Bot, nlu_result: (List[IntentResult], List[EntityResult]), message: Message):
        if not bot.is_admin(message.author):
            await send(message.author, message.channel, bot, "Du bist nicht berechtigt, mich zu deaktivieren!")
            return

        await bot.shutdown()


class QnAAnswerHandler(Handler):
    """ Handler which can add answers for QnA to the local files."""

    async def handle(self, bot: Bot, nlu_result: (List[IntentResult], List[EntityResult]), message: Message):
        text: str = message.content
        # Command, qna, text
        spl = text.split(" ", 2)
        if len(spl) != 3:
            await send(message.author, message.channel, bot, f"Ich finde keinen gültigen Text")
            return

        name = f"QnA/{spl[1]}.json"
        if not exists(name):
            await send(message.author, message.channel, bot, f"Ich finde keinen Eintrag für {name}")
            return

        qna_file = open(name, "r", encoding="utf-8-sig")
        response: list = loads(qna_file.read().strip())
        qna_file.close()

        response.insert(0, spl[2])

        qna_file = open(name, "w", encoding="utf-8-sig")
        qna_file.write(dumps(response, indent=True))
        qna_file.close()

        await send(message.author, message.channel, bot, f"Habs hinzugefügt zu {name}")
