package org.fuchss.deltabot.cognitive.dialogmanagement

import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.User
import org.fuchss.deltabot.DeltaBotConfiguration
import org.fuchss.deltabot.Language
import org.fuchss.deltabot.cognitive.RasaService
import org.fuchss.deltabot.cognitive.dialogmanagement.dialog.Clock
import org.fuchss.deltabot.cognitive.dialogmanagement.dialog.News
import org.fuchss.deltabot.cognitive.dialogmanagement.dialog.NotUnderstanding
import org.fuchss.deltabot.cognitive.dialogmanagement.dialog.QnA
import org.fuchss.deltabot.command.CommandRegistry
import org.fuchss.deltabot.command.user.Help
import org.fuchss.deltabot.utils.logger
import java.util.*

/**
 * This class manages a set of [Dialogs][Dialog] for a [User] and starts specific dialogs depending on the messages of the user.
 */
class UserBotInstance(private val configuration: DeltaBotConfiguration, private val commandRegistry: CommandRegistry) {
    private val rasaService = RasaService(configuration)

    private val activeDialogs: Stack<String> = Stack()

    private val dialogs: List<Dialog> = listOf(
        NotUnderstanding(),
        QnA(),
        Clock(),
        News()
    )

    /**
     * Handle a [Message] from a [User] with a certain [Language].
     */
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

            dialog = DialogRegistry.Intent2Dialog[intent] ?: NotUnderstanding.ID

            if (score <= configuration.nluThreshold) {
                dialog = NotUnderstanding.ID
            } else if (intent == "QnA-Tasks") {
                // Simply print help message ..
                val botName = if (message.isFromGuild) message.guild.selfMember.effectiveName else message.jda.selfUser.name
                val reply = Help.generateText(botName, commandRegistry.getCommands())
                message.replyEmbeds(reply).complete()
                return
            } else if (intent.startsWith("QnA")) {
                dialog = QnA.ID
            }
        }

        val instance = dialogs.find { d -> d.dialogId == dialog } ?: InternalErrorDialog()
        val result = instance.proceed(message, intents, entities, language)
        if (result == DialogResult.WAIT_FOR_INPUT)
            activeDialogs.push(instance.dialogId)
    }
}