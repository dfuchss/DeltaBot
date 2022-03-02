package org.fuchss.deltabot.cognitive.dialogmanagement

import org.fuchss.deltabot.cognitive.RasaService.IntentResult
import org.fuchss.deltabot.cognitive.dialogmanagement.dialog.Clock
import org.fuchss.deltabot.cognitive.dialogmanagement.dialog.News
import org.fuchss.deltabot.cognitive.dialogmanagement.dialog.QnA

/**
 * This class only contains a mapping for [IntentResult.name]<->[Dialog.dialogId]. It also contains the descriptions of the dialogs.
 */
class DialogRegistry private constructor() {
    companion object {
        /**
         * Maps intents to [Dialog.dialogId].
         */
        val Intent2Dialog = sortedMapOf(
            "QnA".lowercase() to QnA.ID,
            "Clock".lowercase() to Clock.ID,
            "News".lowercase() to News.ID
        )

        /**
         * Maps [Dialog.dialogId] to a list of descriptions for a certain dialog.
         */
        val DialogToDescription = sortedMapOf(
            QnA.ID to listOf(
                "I can tell Jokes",
                "I can greet",
                "I can tell you, what I can do :)"
            ),
            Clock.ID to listOf("I can tell you the current time"),
            News.ID to listOf("I can inform you about the current news")
        )
    }

    init {
        throw IllegalAccessError()
    }
}
