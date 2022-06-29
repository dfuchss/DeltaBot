package org.fuchss.deltabot.command.react

import com.vdurmont.emoji.EmojiManager
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent
import net.dv8tion.jda.api.hooks.EventListener
import org.fuchss.deltabot.utils.extensions.hide
import org.fuchss.deltabot.utils.extensions.isHidden
import org.fuchss.deltabot.utils.extensions.pinAndDelete
import org.fuchss.deltabot.utils.extensions.unhide

class ReactionHandler : EventListener {
    override fun onEvent(event: GenericEvent) {
        if (event !is MessageReactionAddEvent) {
            return
        }
        handleReactionAdd(event)
    }

    private fun handleReactionAdd(event: MessageReactionAddEvent) {
        if (!event.isFromGuild || !event.reactionEmote.isEmoji || event.user == null) {
            return
        }
        handle(event, ":pushpin:", this::handlePin)
        handle(event, ":arrow_down_small:") { m, _, _ -> handleHide(m) }
    }

    private fun handle(event: MessageReactionAddEvent, emojiName: String, handler: (Message, String, User) -> Unit) {
        val emoji = EmojiManager.getForAlias(emojiName).unicode
        if (event.reactionEmote.emoji == emoji) {
            handler(event.retrieveMessage().complete(), emoji, event.user!!)
        }
    }

    private fun handlePin(message: Message, reaction: String, user: User) {
        message.removeReaction(reaction, user).queue()
        if (message.isPinned) {
            message.unpin().queue()
        } else {
            message.pinAndDelete()
        }
    }

    private fun handleHide(message: Message) {
        if (message.author.id != message.jda.selfUser.id) {
            return
        }

        val hidden = message.isHidden()

        if (hidden) {
            message.unhide()
        } else {
            message.hide()
        }

        message.clearReactions().queue()
    }
}
