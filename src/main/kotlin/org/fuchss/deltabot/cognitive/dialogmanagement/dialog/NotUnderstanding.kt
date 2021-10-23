package org.fuchss.deltabot.cognitive.dialogmanagement.dialog

import org.fuchss.deltabot.cognitive.dialogmanagement.Context
import org.fuchss.deltabot.cognitive.dialogmanagement.Dialog
import org.fuchss.deltabot.cognitive.dialogmanagement.DialogResult
import org.fuchss.deltabot.utils.extensions.createObjectMapper
import org.fuchss.deltabot.utils.extensions.logger
import java.io.InputStream
import kotlin.random.Random

/**
 * The fallback [Dialog] that simply states that something has not been understood.
 */
class NotUnderstanding : Dialog(ID) {
    companion object {
        const val ID = "NotUnderstanding"
    }

    override fun loadInitialSteps() {
        this.steps.add(this::notUnderstanding)
    }

    private fun notUnderstanding(context: Context): DialogResult {
        val qnaFile = "/QnA/${context.language.locale}/NotUnderstanding.json"
        val stream = this.javaClass.getResourceAsStream(qnaFile)!!
        val answer = getAnswer(stream)
        stream.close()
        context.message.reply(answer!!).complete()
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