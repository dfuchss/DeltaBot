package org.fuchss.deltabot.cognitive.dialogmanagement.dialog

import net.dv8tion.jda.api.entities.Message
import org.fuchss.deltabot.Language
import org.fuchss.deltabot.cognitive.RasaService
import org.fuchss.deltabot.cognitive.dialogmanagement.Dialog
import org.fuchss.deltabot.cognitive.dialogmanagement.DialogResult
import org.fuchss.deltabot.language
import org.fuchss.deltabot.translate
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

    private fun timeStep(message: Message, intents: List<RasaService.IntentResult>, entities: List<RasaService.EntityResult>, language: Language): DialogResult {
        val time = LocalDateTime.now().timestamp()
        val timeResponse = "Current Point in Time: <t:#:F>".translate(message.language(), time)
        message.reply(timeResponse).queue()
        return DialogResult.NEXT
    }

}