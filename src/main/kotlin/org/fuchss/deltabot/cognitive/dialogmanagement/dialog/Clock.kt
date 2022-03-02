package org.fuchss.deltabot.cognitive.dialogmanagement.dialog

import org.fuchss.deltabot.cognitive.dialogmanagement.Context
import org.fuchss.deltabot.cognitive.dialogmanagement.Dialog
import org.fuchss.deltabot.cognitive.dialogmanagement.DialogResult
import org.fuchss.deltabot.utils.extensions.language
import org.fuchss.deltabot.utils.extensions.translate
import org.fuchss.deltabot.utils.timestamp
import java.time.LocalDateTime

/**
 * A [Dialog] that can simply show the current time to you.
 */
class Clock : Dialog(ID) {
    companion object {
        const val ID = "Clock"
    }

    override fun loadInitialSteps() {
        this.steps.add(this::timeStep)
    }

    private fun timeStep(context: Context): DialogResult {
        val time = LocalDateTime.now().timestamp()
        val timeResponse = "Current Point in Time: <t:#:F>".translate(context.message.language(), time)
        context.message.reply(timeResponse).queue()
        return DialogResult.NEXT
    }
}
