package com.example.data.model

data class Team(
    val id: String,
    val name: String,
    val sportType: SportType,
    val fieldPlayerCount: Int,
    val squadSize: Int,
    val playerIds: List<String>,
    val isFinalized: Boolean = false
) {
    init {
        require(fieldPlayerCount > 0) { "fieldPlayerCount must be > 0" }
        require(squadSize >= fieldPlayerCount) { "squadSize must be >= fieldPlayerCount" }
    }
}
