package org.fuchss.deltabot.cognitive.dialogmanagement.dialog

import com.apptastic.rssreader.Item
import com.apptastic.rssreader.RssReader
import net.dv8tion.jda.api.entities.Message
import org.fuchss.deltabot.Language
import org.fuchss.deltabot.cognitive.RasaService
import org.fuchss.deltabot.cognitive.dialogmanagement.Dialog
import org.fuchss.deltabot.cognitive.dialogmanagement.DialogResult
import org.fuchss.deltabot.language
import org.fuchss.deltabot.translate
import org.fuchss.deltabot.utils.logger
import java.time.LocalDateTime
import java.time.ZoneId

class News : Dialog(ID) {
    companion object {
        const val ID = "News"
        private val providers = mapOf(
            "General" to listOf("Tagesschau" to "https://www.tagesschau.de/xml/rss2"),
            "Sport" to listOf("Sport1" to "https://www.sport1.de/news.rss"),
            "IT" to listOf("heise online" to "https://www.heise.de/rss/heise-top-atom.xml"),
            "Netcup" to listOf("Netcup" to "https://www.netcup-sonderangebote.de/feed")
        )

        private const val maxNews = 10
    }

    override fun loadInitialSteps() {
        this.steps.add(this::selectNews)
    }

    private fun selectNews(message: Message, intents: List<RasaService.IntentResult>, entities: List<RasaService.EntityResult>, language: Language): DialogResult {
        val newsEntities = entities.filter { e -> e.group == "news" }
        val categories = newsEntities.map { e -> e.name }

        if (categories.isNotEmpty()) {
            this.steps.add(this::newsStep)
            return DialogResult.NEXT
        }

        val categoryNames = providers.keys.map { n -> n.translate(message.language()) }
        val question = "Which categories are you interested in? #".translate(message.language(), categoryNames.joinToString(", "))
        message.reply(question).complete()
        this.steps.add(this::newsStep)

        return DialogResult.WAIT_FOR_INPUT
    }

    private fun newsStep(message: Message, intents: List<RasaService.IntentResult>, entities: List<RasaService.EntityResult>, language: Language): DialogResult {
        val newsEntities = entities.filter { e -> e.group == "news" }
        val categories = newsEntities.map { e -> e.name }

        if (categories.isEmpty()) {
            val response = "Unfortunately I did not recognize any category. End dialog for now ;)".translate(message.language())
            message.reply(response).queue()
            return DialogResult.NEXT
        }

        var sent = false
        for (category in categories) {
            for (provider in providers[category] ?: continue) {
                var response = "\n**${provider.first}**\n"
                val feed = readFeed(provider.second)
                for (news in feed) {
                    var url = news.guid.get()
                    url = url.replace("https://www.", "https://")
                    url = url.replace("http://www.", "http://")
                    response += "${news.title.get()} (<$url>)\n"
                }
                if (feed.isNotEmpty()) {
                    sent = true
                    message.reply(response).queue()
                }
            }
        }
        if (!sent) {
            message.reply("No new news".translate(message.language())).queue()
        }

        return DialogResult.NEXT
    }

    private fun readFeed(provider: String): List<Item> {
        val now = LocalDateTime.now()
        val nowM24h = now.minusHours(24)
        try {
            val feed = RssReader().read(provider).toList()
            val entries = feed
                .filter { i -> i.pubDateZonedDateTime.isPresent }
                .filter { i -> i.pubDateZonedDateTime.get() >= nowM24h.atZone(ZoneId.systemDefault()) }
                .sortedByDescending { i -> i.pubDateZonedDateTime.get() }
            return entries.toList().take(maxNews).toList()
        } catch (e: Exception) {
            logger.error(e.message, e)
            return listOf()
        }
    }

}