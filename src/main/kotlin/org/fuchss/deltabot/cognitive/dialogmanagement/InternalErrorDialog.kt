package org.fuchss.deltabot.cognitive.dialogmanagement

/**
 * The fallback [Dialog] for internal errors.
 */
class InternalErrorDialog : Dialog(ID) {
    companion object {
        const val ID = "InternalError"
    }

    override fun loadInitialSteps() {
        this.steps.add(this::internalError)
    }

    private fun internalError(context: Context): DialogResult {
        context.message.reply("Internal Error occurred .. please contact the bot admin!").queue()
        return DialogResult.NEXT
    }
}
