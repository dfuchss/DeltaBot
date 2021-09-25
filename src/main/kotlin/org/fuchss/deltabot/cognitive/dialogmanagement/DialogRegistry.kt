package org.fuchss.deltabot.cognitive.dialogmanagement

import org.fuchss.deltabot.cognitive.dialogmanagement.dialog.Clock
import org.fuchss.deltabot.cognitive.dialogmanagement.dialog.News
import org.fuchss.deltabot.cognitive.dialogmanagement.dialog.QnA

class DialogRegistry {
    companion object {
        val Intent2Dialog = sortedMapOf(
            "QnA".lowercase() to QnA.ID,
            "Clock".lowercase() to Clock.ID,
            "News".lowercase() to News.ID
        )

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
}