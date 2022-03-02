package org.fuchss.deltabot.command.user.polls

import net.dv8tion.jda.api.entities.Emoji
import net.dv8tion.jda.api.entities.Guild
import org.fuchss.deltabot.utils.extensions.withFirst
import org.fuchss.deltabot.utils.extensions.without
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = "Poll")
class Poll {
    @Id
    @GeneratedValue
    var id: Int? = null
    var pollType: String = ""
    var timestamp: Long? = null
    var gid: String = ""
    var cid: String = ""
    var mid: String = ""
    var uid: String = ""
    var onlyOneOption: Boolean = false

    @Column(columnDefinition = "JSON")
    var options: MutableList<PollBase.EmojiDTO> = mutableListOf()

    @Column(columnDefinition = "JSON")
    var react2User: MutableMap<String, MutableList<String>> = mutableMapOf()

    @Column(columnDefinition = "JSON")
    var user2React: MutableMap<String, MutableList<String>> = mutableMapOf()

    constructor()

    constructor(pollType: String, timestamp: Long?, gid: String, cid: String, mid: String, uid: String, options: List<PollBase.EmojiDTO>, onlyOneOption: Boolean) {
        this.pollType = pollType
        this.timestamp = timestamp
        this.gid = gid
        this.cid = cid
        this.mid = mid
        this.uid = uid
        this.options = options.toMutableList()
        this.onlyOneOption = onlyOneOption
    }

    fun getEmojis(guild: Guild): List<Emoji> {
        return options.map { dto -> dto.getEmoji(guild) }
    }

    fun cleanup() {
        val emptyFieldsU2R = user2React.filter { e -> e.value.isEmpty() }
        val emptyFieldsR2U = react2User.filter { e -> e.value.isEmpty() }
        emptyFieldsU2R.forEach { e -> user2React.remove(e.key) }
        emptyFieldsR2U.forEach { e -> react2User.remove(e.key) }
    }

    fun addReact2User(react: String, uid: String) {
        react2User[react] = (react2User[react] ?: mutableListOf()).withFirst(uid).toMutableList()
    }

    fun addUser2React(uid: String, react: String) {
        user2React[uid] = (user2React[uid] ?: mutableListOf()).withFirst(react).toMutableList()
    }

    fun removeReact2User(react: String, uid: String) {
        react2User[react] = (react2User[react] ?: mutableListOf()).without(uid).toMutableList()
    }

    fun removeUser2React(uid: String) {
        user2React.remove(uid)
    }

    fun removeUser2React(uid: String, react: String) {
        user2React[uid] = (user2React[uid] ?: mutableListOf()).without(react).toMutableList()
    }

    fun setUser2React(uid: String, newReactions: MutableList<String>) {
        user2React[uid] = newReactions
    }
}
