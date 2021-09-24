package org.fuchss.deltabot.cognitive.dialog

import net.dv8tion.jda.api.entities.Message
import org.fuchss.deltabot.Language
import org.fuchss.deltabot.cognitive.RasaService

abstract class Dialog(val dialogId: String) {

    protected val steps: MutableList<DialogStep> = mutableListOf()
    private var next = 0

    init {
        this.loadInitialSteps()
    }

    protected abstract fun loadInitialSteps()

    fun proceed(message: Message, intents: List<RasaService.IntentResult>, entities: List<RasaService.EntityResult>, language: Language): DialogResult {
        while (steps.size > this.next) {
            val result = steps[next](message, intents, entities, language)
            next++

            if (result == DialogResult.WAIT_FOR_INPUT)
                return DialogResult.WAIT_FOR_INPUT
        }
        this.reset()
        return DialogResult.NEXT
    }

    protected open fun reset() {
        steps.clear()
        next = 0
        this.loadInitialSteps()
    }
}

enum class DialogResult {
    NEXT, WAIT_FOR_INPUT
}


typealias DialogStep = (message: Message, intents: List<RasaService.IntentResult>, entities: List<RasaService.EntityResult>, language: Language) -> DialogResult
