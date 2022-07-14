package org.fuchss.deltabot.cognitive.dialogmanagement.dialog

import com.apptastic.rssreader.Item
import com.apptastic.rssreader.RssReader
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.MessageBuilder
import net.dv8tion.jda.api.entities.Message
import org.fuchss.deltabot.Constants
import org.fuchss.deltabot.cognitive.dialogmanagement.Context
import org.fuchss.deltabot.cognitive.dialogmanagement.Dialog
import org.fuchss.deltabot.cognitive.dialogmanagement.DialogResult
import org.fuchss.deltabot.utils.extensions.logger
import org.fuchss.deltabot.utils.extensions.translate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * A [Dialog] that uses [RssReader] to read RSS Feeds and present the current news.
 */
class News : Dialog(ID) {
    companion object {
        const val ID = "News"
        val providers = mapOf(
            "General" to listOf("Tagesschau" to "https://www.tagesschau.de/xml/rss2/"),
            "Sport" to listOf("Sportschau" to "https://www.sportschau.de/index~rss2.xml"),
            "IT" to listOf("heise online" to "https://www.heise.de/rss/heise-top-atom.xml")
        )

        private const val maxNews = 10

        fun createNewsMessage(providers: List<String>): List<Message> {
            val result = mutableListOf<Message>()
            for (categoryIdentifier in providers) {
                for (provider in Companion.providers[categoryIdentifier] ?: continue) {
                    val responseBuilder = EmbedBuilder().setTitle(provider.first).setColor(Constants.BLUE)
                    val feed = readFeed(provider.second)
                    for (news in feed) {
                        responseBuilder.descriptionBuilder.append("* [${news.title.get()}](${news.link.orElse(news.guid.get())})\n")
                    }
                    if (feed.isNotEmpty()) {
                        responseBuilder.descriptionBuilder.trim()
                        result += MessageBuilder().setEmbeds(responseBuilder.build()).build()
                    }
                }
            }

            return result
        }

        private fun readFeed(provider: String): List<Item> {
            val now = LocalDateTime.now()
            val nowM24h = now.minusHours(24)
            return try {
                val feed = RssReader().read(provider).toList()
                val entries = feed
                    .filter { i -> i.pubDateZonedDateTime.isPresent }
                    .filter { i -> i.pubDateZonedDateTime.get() >= nowM24h.atZone(ZoneId.systemDefault()) }
                    .sortedByDescending { i -> i.pubDateZonedDateTime.get() }
                entries.toList().take(maxNews).toList()
            } catch (e: Exception) {
                logger.error(e.message, e)
                listOf()
            }
        }
    }

    override fun loadInitialSteps() {
        this.steps.add(this::selectNews)
    }

    private fun selectNews(context: Context): DialogResult {
        val newsEntities = context.entities.filter { e -> e.group == "news" }
        val categories = newsEntities.map { e -> e.name }

        if (categories.isNotEmpty()) {
            this.steps.add(this::newsStep)
            return DialogResult.NEXT
        }

        val categoryNames = providers.keys.map { n -> n.translate(context.language) }
        val question = "Which categories are you interested in? #".translate(context.language, categoryNames.joinToString(", "))
        context.message.reply(question).complete()
        this.steps.add(this::newsStep)

        return DialogResult.WAIT_FOR_INPUT
    }

    private fun newsStep(context: Context): DialogResult {
        val newsEntities = context.entities.filter { e -> e.group == "news" }
        val categories = newsEntities.map { e -> e.name }

        if (categories.isEmpty()) {
            val response = "Unfortunately I did not recognize any category. End dialog for now ;)".translate(context.language)
            context.message.reply(response).queue()
            return DialogResult.NEXT
        }

        val messages = createNewsMessage(categories)
        messages.forEach { context.message.reply(it).queue() }
        if (messages.isEmpty()) {
            context.message.reply("No new news".translate(context.language)).queue()
        }

        return DialogResult.NEXT
    }
}
