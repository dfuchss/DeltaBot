from calendar import timegm
from datetime import datetime
from typing import List, Dict

from discord import Message
from feedparser import parse

from bot_base import BotBase, send, dialog_meta
from cognitive import IntentResult, EntityResult
from dialog_management import Dialog, DialogResult


@dialog_meta(dialog_info="Ich kann Nachrichten (News) liefern")
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
    "Allgemein": [NewsProvider("Tagesschau", "https://www.tagesschau.de/xml/rss2")],
    "Sport": [NewsProvider("Sport1", "https://www.sport1.de/news.rss")],
    "IT": [NewsProvider("heise online", "https://www.heise.de/rss/heise-top-atom.xml")],
    "Netcup": [NewsProvider("Netcup", "https://www.netcup-sonderangebote.de/feed")]
}


class News(Dialog):
    """This dialog provides news from RSS feeds"""

    ID = "News"
    """The ID of the Dialog"""

    @staticmethod
    def to_date(time_tuple):
        """ Converts a time tuple to actual time. """
        return datetime.fromtimestamp(timegm(time_tuple))

    MaxNews: int = 10

    def __init__(self, bot: BotBase):
        super().__init__(bot, News.ID)

    def _load_initial_steps(self):
        self.add_step(self._select_news)

    async def _select_news(self, message: Message, intents: List[IntentResult],
                           entities: List[EntityResult]) -> DialogResult:
        entities = filter(lambda e: e.group == "news", entities)
        categories = list(map(lambda e: e.name, entities))
        if len(categories) != 0:
            self.add_step(self._news_step)
            return DialogResult.NEXT

        await send(message.author, message.channel, self._bot,
                   f"Für welche Kategorie(n) willst Du Nachrichten?\n{', '.join(Providers.keys())}")
        self.add_step(self._news_step)
        return DialogResult.WAIT_FOR_INPUT

    async def _news_step(self, message: Message, intents: List[IntentResult],
                         entities: List[EntityResult]) -> DialogResult:
        entities = filter(lambda e: e.group == "news", entities)
        categories = list(map(lambda e: e.name, entities))
        if not categories:
            await send(message.author, message.channel, self._bot,
                       "Leider habe ich keine Kategorie erkannt. Dialog erstmal beenden ;)")
            return DialogResult.NEXT

        sent = False
        for category in categories:
            for provider in Providers[category]:
                response = f"\n**{provider.name}**\n"
                news_feed = parse(provider.url).entries
                news_feed = filter(lambda e: self._last24h(e), news_feed)
                news_feed = sorted(news_feed, key=lambda e: self.to_date(e.published_parsed).time(), reverse=True)

                for idx, news in enumerate(news_feed):
                    if idx >= self.MaxNews:
                        break
                    url = news["id"]
                    url = url.replace("https://www.", "https://")
                    url = url.replace("http://www.", "http://")
                    response += news["title"]
                    response += f" (<{url}>)\n"
                if len(news_feed) != 0:
                    # response += "Nix, rein gar nichts .."
                    sent = True
                await send(message.author, message.channel, self._bot, response)

        if not sent:
            await send(message.author, message.channel, self._bot, "Keine neuen Nachrichten.")
        return DialogResult.NEXT

    def _last24h(self, e) -> bool:
        published = self.to_date(e.published_parsed)
        now = datetime.now()
        diff = now - published
        return diff.total_seconds() < 24 * 60 * 60  # If less than 24 hours
