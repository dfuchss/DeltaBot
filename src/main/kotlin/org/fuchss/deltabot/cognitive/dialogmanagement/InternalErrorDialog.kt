package org.fuchss.deltabot.cognitive.dialogmanagement

import net.dv8tion.jda.api.entities.Message
import org.fuchss.deltabot.Language
import org.fuchss.deltabot.cognitive.RasaService

class InternalErrorDialog : Dialog(ID) {
    companion object {
        const val ID = "InternalError"
    }

    override fun loadInitialSteps() {
        this.steps.add(this::internalError)
    }

    private fun internalError(message: Message, intents: List<RasaService.IntentResult>, entities: List<RasaService.EntityResult>, language: Language): DialogResult {
        message.reply("Internal Error occurred .. please contact the bot admin!").queue()
        return DialogResult.NEXT
    }
}
