package com.example.data.model

enum class SportType(val label: String) {
    BEACH("Beach"),
    INDOOR("Halle"),
    ANY("Beliebig")
}

enum class FieldFormat(
    val label: String,
    val fieldPlayerCount: Int,
    val squadSize: Int,
    val sportType: SportType,
    val description: String
) {
    FORMAT_2V2("2v2", 2, 2, SportType.BEACH, "Beach 2v2 (2 Feldspieler, Kader 2)"),
    FORMAT_3V3("3v3", 3, 3, SportType.ANY, "3v3 (3 Feldspieler, Kader 3)"),
    FORMAT_4V4("4v4", 4, 4, SportType.ANY, "4v4 (4 Feldspieler, Kader 4)"),
    FORMAT_5V5("5v5", 5, 5, SportType.ANY, "5v5 (5 Feldspieler, Kader 5)"),
    FORMAT_6V6("6v6", 6, 6, SportType.INDOOR, "Halle 6v6 (6 Feldspieler, Kader 6)"),
    FORMAT_6V6_PLUS_1("6v6 + 1", 6, 7, SportType.INDOOR, "Halle 6v6 + 1 Auswechselspieler (Kader 7)"),
    FORMAT_6V6_PLUS_2("6v6 + 2", 6, 8, SportType.INDOOR, "Halle 6v6 + 2 Auswechselspieler (Kader 8)"),
    FORMAT_6V6_PLUS_3("6v6 + 3", 6, 9, SportType.INDOOR, "Halle 6v6 + 3 Auswechselspieler (Kader 9)"),
    FORMAT_6V6_PLUS_4("6v6 + 4", 6, 10, SportType.INDOOR, "Halle 6v6 + 4 Auswechselspieler (Kader 10)");

    companion object {
        fun defaultFor(sportType: SportType): FieldFormat {
            return when (sportType) {
                SportType.BEACH -> FORMAT_2V2
                SportType.INDOOR -> FORMAT_6V6
                SportType.ANY -> FORMAT_4V4
            }
        }
    }
}
