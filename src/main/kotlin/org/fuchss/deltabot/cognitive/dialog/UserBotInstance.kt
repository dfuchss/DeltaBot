package org.fuchss.deltabot.cognitive.dialog

import net.dv8tion.jda.api.entities.Message
import org.fuchss.deltabot.Configuration
import org.fuchss.deltabot.Language
import org.fuchss.deltabot.cognitive.RasaService
import org.fuchss.deltabot.command.user.Help
import org.fuchss.deltabot.utils.logger
import java.util.*

class UserBotInstance(private val configuration: Configuration) {
    private val rasaService = RasaService(configuration)

    private val activeDialogs: Stack<String> = Stack()

    private val dialogs: List<Dialog> = listOf(
        NotUnderstanding(),
        QnA()
    )
    private val intent2Dialog: Map<String, String> = mapOf(
        "QnA".lowercase() to QnA.ID
    )

    fun handle(message: Message, language: Language) {
        val (intents, entities) = rasaService.recognize(message.contentDisplay, language.locale)
        logger.debug("Intents & Entities: $intents & $entities")
        var dialog: String

        if (activeDialogs.isNotEmpty()) {
            dialog = activeDialogs.pop()
        } else if (intents.isEmpty()) {
            dialog = NotUnderstanding.ID
        } else {
            val intent = intents[0].name
            val score = intents[0].score

            dialog = intent2Dialog[intent] ?: NotUnderstanding.ID

            if (score <= configuration.nluThreshold) {
                dialog = NotUnderstanding.ID
            } else if (intent == "QnA-Tasks") {
                // Simply print help message ..
                val botName = if (message.isFromGuild) message.guild.selfMember.effectiveName else message.jda.selfUser.name
                // TODO Get Command List
                val reply = Help.generateText(botName, emptyList())
                message.replyEmbeds(reply).complete()
                return
            } else if (intent.startsWith("QnA")) {
                dialog = QnA.ID
            }
        }

        val instance = dialogs.find { d -> d.dialogId == dialog }!!
        val result = instance.proceed(message, intents, entities, language)
        if (result == DialogResult.WAIT_FOR_INPUT)
            activeDialogs.push(instance.dialogId)
    }
}