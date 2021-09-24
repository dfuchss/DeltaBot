package org.fuchss.deltabot.cognitive.dialog

import net.dv8tion.jda.api.entities.Message
import org.fuchss.deltabot.Language
import org.fuchss.deltabot.cognitive.RasaService
import org.fuchss.deltabot.utils.createObjectMapper
import org.fuchss.deltabot.utils.logger
import java.io.InputStream
import kotlin.random.Random

class NotUnderstanding : Dialog(ID) {
    companion object {
        const val ID = "NotUnderstanding"
    }

    override fun loadInitialSteps() {
        this.steps.add(this::notUnderstanding)
    }

    private fun notUnderstanding(message: Message, intents: List<RasaService.IntentResult>, entities: List<RasaService.EntityResult>, language: Language): DialogResult {
        val qnaFile = "/QnA/${language.locale}/NotUnderstanding.json"
        val stream = this.javaClass.getResourceAsStream(qnaFile)!!
        val answer = getAnswer(stream)
        stream.close()
        message.reply(answer!!).complete()
        return DialogResult.NEXT
    }

    private fun getAnswer(stream: InputStream): String? {
        return try {
            val orm = createObjectMapper()
            var answers = mutableListOf<String>()
            answers = orm.readValue(stream, answers.javaClass)
            answers[Random.nextInt(answers.size)]
        } catch (e: Exception) {
            logger.error(e.message)
            null
        }
    }
}