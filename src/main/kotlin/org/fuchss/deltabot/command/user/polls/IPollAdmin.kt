package org.fuchss.deltabot.command.user.polls

import net.dv8tion.jda.api.interactions.InteractionHook

interface IPollAdmin {
    fun createAdminArea(
        reply: InteractionHook,
        data: Poll
    )

    fun register(
        pollType: String,
        manager: IPollBase
    )
}
