package org.fuchss.deltabot.cognitive.dialogmanagement.dialog

import org.fuchss.deltabot.cognitive.dialogmanagement.Context
import org.fuchss.deltabot.cognitive.dialogmanagement.Dialog
import org.fuchss.deltabot.cognitive.dialogmanagement.DialogResult
import org.fuchss.deltabot.utils.extensions.createObjectMapper
import org.fuchss.deltabot.utils.extensions.logger
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

    private fun qnaStep(context: Context): DialogResult {
        val qnaName = context.intents[0].name.substring(4)
        val qnaFile = "/QnA/${context.language.locale}/$qnaName.json"
        val stream = this.javaClass.getResourceAsStream(qnaFile)
        if (stream == null) {
            context.message.reply("I cannot find a QnA entry for $qnaName .. please ask the admin").complete()
            return DialogResult.NEXT
        }

        val answer = getAnswer(stream)
        stream.close()
        if (answer == null) {
            context.message.reply("I cannot find a suitable answer for $qnaName .. please ask the admin").complete()
            return DialogResult.NEXT
        }


        context.message.reply(answer.enhance(context)).complete()
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

private fun String.enhance(context: Context): String {
    var result = this
    result = result.replace("#USER", context.message.author.name)
    result = result.replace("#CHANNEL", context.message.channel.name)
    return result
}
