package org.fuchss.deltabot.db.dto

import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = "guild_x_admin")
class Guild2AdminDTO {
    @Id
    @GeneratedValue
    var id: Int? = null
}
