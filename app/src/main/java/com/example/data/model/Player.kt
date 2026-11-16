package com.example.data.model

enum class HeightClass(val label: String, val rangeDescription: String = "") {
    VERY_SMALL("Sehr klein", ""),
    SMALL("Klein", ""),
    MEDIUM("Mittel", ""),
    LARGE("Groß", "")
}

enum class Gender(val label: String) {
    MALE("Männlich"),
    FEMALE("Weiblich"),
    DIVERSE("Divers / Keine Angabe")
}

data class Player(
    val id: String,
    val name: String,
    val skill: Int, // 1..5
    val heightClass: HeightClass = HeightClass.MEDIUM,
    val gender: Gender = Gender.DIVERSE,
    val isDummy: Boolean = false,
    val tags: List<String> = emptyList(),
    val isSelectedForMixer: Boolean = false
) {
    init {
        require(skill in 1..5) { "Skill must be between 1 and 5" }
    }
}
