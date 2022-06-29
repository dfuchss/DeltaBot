package org.fuchss.deltabot.cognitive.dialogmanagement

import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.User
import org.fuchss.deltabot.Language
import org.fuchss.deltabot.cognitive.RasaService

/**
 * Base class of all [Dialogs][Dialog] that handles the management of [DialogSteps][DialogStep].
 */
abstract class Dialog(val dialogId: String) {

    /**
     * The list of [DialogStep] to be executed. Only use [MutableList.add] to edit this list!
     */
    protected val steps: MutableList<DialogStep> = mutableListOf()
    private var next = 0

    init {
        this.loadInitialSteps()
    }

    /**
     * Load the initial step(s) of this dialog.
     */
    protected abstract fun loadInitialSteps()

    /**
     * Invoke the step. Will be executed by [UserBotInstance].
     * @param[message] the message to respond
     * @param[intents] all identified intents
     * @param[entities] all identified entities
     * @param[language] the language of the context
     * @return [DialogResult] that indicates whether the dialog is finished [DialogResult.NEXT] or [waits for input][DialogResult.WAIT_FOR_INPUT].
     */
    fun proceed(message: Message, intents: List<RasaService.IntentResult>, entities: List<RasaService.EntityResult>, language: Language): DialogResult {
        val context = Context(message, intents, entities, language)
        while (steps.size > this.next) {
            val result = steps[next](context)
            next++

            if (result == DialogResult.WAIT_FOR_INPUT) {
                return DialogResult.WAIT_FOR_INPUT
            }
        }
        this.reset()
        return DialogResult.NEXT
    }

    /**
     * Resets the dialog to its initial state.
     */
    protected open fun reset() {
        steps.clear()
        next = 0
        this.loadInitialSteps()
    }
}

/**
 * All possible dialog results.
 */
enum class DialogResult {
    /**
     * Dialog is finished and next can continue.
     */
    NEXT,

    /**
     * Dialog needs an input from the user.
     */
    WAIT_FOR_INPUT
}

/**
 * The [Context] of a [DialogStep].
 */
data class Context(
    /**
     * The current message of the [User].
     */
    val message: Message,
    /**
     * The current [Intents][RasaService.IntentResult].
     */
    val intents: List<RasaService.IntentResult>,
    /**
     * The current [Entities][RasaService.EntityResult].
     */
    val entities: List<RasaService.EntityResult>,
    /**
     * The current language of the [User]
     */
    val language: Language
)

/**
 * Defines a step in dialogs.
 */
typealias DialogStep = (context: Context) -> DialogResult
