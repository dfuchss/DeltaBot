package org.fuchss.deltabot.command.user.polls

import com.fasterxml.jackson.module.kotlin.readValue
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.Table
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.emoji.Emoji
import org.fuchss.deltabot.utils.extensions.createObjectMapper
import org.fuchss.deltabot.utils.extensions.withLast
import org.fuchss.deltabot.utils.extensions.without

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

    @Column(name = "options")
    var optionsString: String = "[]"

    @Transient
    var optionsData: MutableList<PollBase.EmojiDTO> = mutableListOf()

    @Column(name = "react2User")
    var react2UserString: String = "{}"

    @Transient
    var react2UserData: MutableMap<String, MutableList<String>> = mutableMapOf()

    @Column(name = "user2React")
    var user2ReactString: String = "{}"

    @Transient
    var user2ReactData: MutableMap<String, MutableList<String>> = mutableMapOf()

    @Transient
    private val oom = createObjectMapper()

    constructor()

    constructor(
        pollType: String,
        timestamp: Long?,
        gid: String,
        cid: String,
        mid: String,
        uid: String,
        options: List<PollBase.EmojiDTO>,
        onlyOneOption: Boolean
    ) {
        this.pollType = pollType
        this.timestamp = timestamp
        this.gid = gid
        this.cid = cid
        this.mid = mid
        this.uid = uid
        this.onlyOneOption = onlyOneOption
        this.optionsData = options.toMutableList()
        syncOptions()
    }

    fun afterCreation() {
        this.optionsData = oom.readValue(this.optionsString)
        this.react2UserData = oom.readValue(this.react2UserString)
        this.user2ReactData = oom.readValue(this.user2ReactString)
    }

    fun getEmojis(guild: Guild): List<Emoji> {
        return optionsData.map { dto -> dto.getEmoji(guild) }
    }

    fun cleanup() {
        val emptyFieldsU2R = user2ReactData.filter { e -> e.value.isEmpty() }
        val emptyFieldsR2U = react2UserData.filter { e -> e.value.isEmpty() }
        emptyFieldsU2R.forEach { e -> user2ReactData.remove(e.key) }
        emptyFieldsR2U.forEach { e -> react2UserData.remove(e.key) }

        syncUser2React2UserData()
    }

    fun addReact2User(
        react: String,
        uid: String
    ) {
        react2UserData[react] = (react2UserData[react] ?: mutableListOf()).withLast(uid).toMutableList()
        syncUser2React2UserData()
    }

    fun addUser2React(
        uid: String,
        react: String
    ) {
        user2ReactData[uid] = (user2ReactData[uid] ?: mutableListOf()).withLast(react).toMutableList()
        syncUser2React2UserData()
    }

    fun removeReact2User(
        react: String,
        uid: String
    ) {
        react2UserData[react] = (react2UserData[react] ?: mutableListOf()).without(uid).toMutableList()
        syncUser2React2UserData()
    }

    fun removeUser2React(uid: String) {
        user2ReactData.remove(uid)
        syncUser2React2UserData()
    }

    fun removeUser2React(
        uid: String,
        react: String
    ) {
        user2ReactData[uid] = (user2ReactData[uid] ?: mutableListOf()).without(react).toMutableList()
        syncUser2React2UserData()
    }

    fun setUser2React(
        uid: String,
        newReactions: MutableList<String>
    ) {
        user2ReactData[uid] = newReactions
        syncUser2React2UserData()
    }

    private fun syncOptions() {
        this.optionsString = oom.writeValueAsString(this.optionsData)
    }

    private fun syncUser2React2UserData() {
        this.user2ReactString = oom.writeValueAsString(user2ReactData)
        this.react2UserString = oom.writeValueAsString(react2UserData)
    }
}
