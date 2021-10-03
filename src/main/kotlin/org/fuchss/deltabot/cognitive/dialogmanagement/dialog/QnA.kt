package org.fuchss.deltabot.cognitive.dialogmanagement.dialog

import net.dv8tion.jda.api.entities.Message
import org.fuchss.deltabot.Language
import org.fuchss.deltabot.cognitive.RasaService
import org.fuchss.deltabot.cognitive.dialogmanagement.Dialog
import org.fuchss.deltabot.cognitive.dialogmanagement.DialogResult
import org.fuchss.deltabot.utils.createObjectMapper
import org.fuchss.deltabot.utils.logger
import java.io.InputStream
import kotlin.random.Random

/**
 * The [Dialog] that handles questions and answers from defined json files.
 */
class QnA : Dialog(ID) {
    companion object {
        const val ID = "QnA"
    }

    override fun loadInitialSteps() {
        this.steps.add(this::qnaStep)
    }

    private fun qnaStep(message: Message, intents: List<RasaService.IntentResult>, entities: List<RasaService.EntityResult>, language: Language): DialogResult {
        val qnaName = intents[0].name.substring(4)
        val qnaFile = "/QnA/${language.locale}/$qnaName.json"
        val stream = this.javaClass.getResourceAsStream(qnaFile)
        if (stream == null) {
            message.reply("I cannot find a QnA entry for $qnaName .. please ask the admin").complete()
            return DialogResult.NEXT
        }

        val answer = getAnswer(stream)
        stream.close()
        if (answer == null) {
            message.reply("I cannot find a suitable answer for $qnaName .. please ask the admin").complete()
            return DialogResult.NEXT
        }


        message.reply(answer.enhance(message)).complete()
        return DialogResult.NEXT
    }

    private fun getAnswer(stream: InputStream): String? {
        try {
            val orm = createObjectMapper()
            var answers = mutableListOf<String>()
            answers = orm.readValue(stream, answers.javaClass)

            if (answers.isEmpty())
                return null
            return answers[Random.nextInt(answers.size)]
        } catch (e: Exception) {
            logger.error(e.message)
            return null
        }
    }
}

private fun String.enhance(request: Message): String {
    var result = this
    result = result.replace("#USER", request.author.name)
    result = result.replace("#CHANNEL", request.channel.name)
    return result
}
