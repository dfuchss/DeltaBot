package org.fuchss.deltabot.command

/**
 * The possible permissions of the bot commands.
 */
enum class CommandPermissions {
    /**
     * A command that can be used by anyone.
     */
    ALL,

    /**
     * A command that can be used by guild admins & admins.
     */
    GUILD_ADMIN,

    /**
     * A command that can be used by the global admins.
     */
    ADMIN
}