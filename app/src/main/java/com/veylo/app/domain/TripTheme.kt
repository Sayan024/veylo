package com.veylo.app.domain

/** Visual style of a group: picks cover art and accent colours. Stored lowercase (also on the server). */
enum class TripTheme(val key: String, val label: String, val emoji: String) {
    HILLS("hills", "Hill station", "🏔"),
    BEACH("beach", "Beach", "🏖"),
    BUSINESS("business", "Business", "💼"),
    VACATION("vacation", "Vacation", "🌴"),
    CAFE("cafe", "Café & outing", "☕");

    companion object {
        fun of(key: String?): TripTheme = entries.firstOrNull { it.key == key?.lowercase() } ?: VACATION

        private val HILL_WORDS = listOf(
            "hill", "mountain", "himalaya", "trek", "valley", "snow", "ski", "peak", "darjeeling", "shimla", "manali",
            "ooty", "munnar", "kodaikanal", "mussoorie", "nainital", "gangtok", "sikkim", "leh", "ladakh", "kashmir",
            "srinagar", "gulmarg", "pahalgam", "coorg", "mahabaleshwar", "lonavala", "khandala", "matheran", "kasol",
            "spiti", "auli", "rishikesh", "dharamshala", "mcleodganj", "shillong", "tawang", "kedarnath", "chopta",
            "mount abu", "kalimpong", "sandakphu", "everest", "nepal", "bhutan", "landour", "yercaud", "lansdowne",
        )
        private val BEACH_WORDS = listOf(
            "beach", "sea", "ocean", "coast", "island", "goa", "gokarna", "andaman", "havelock", "neil island", "puri",
            "digha", "mandarmani", "pondicherry", "puducherry", "kovalam", "varkala", "alibaug", "tarkarli", "diu",
            "daman", "lakshadweep", "marina", "juhu", "maldives", "bali", "phuket", "krabi", "boracay", "hawaii",
            "cancun", "seychelles", "mauritius", "miami", "pattaya", "langkawi", "zanzibar",
        )
        private val BUSINESS_WORDS = listOf(
            "business", "office", "conference", "client", "meeting", "work", "offsite", "summit", "expo", "corporate",
            "workshop", "training", "seminar", "sales", "team trip", "hackathon", "onsite",
        )

        /** Guesses the style from what the user typed. Business words win over places (a Goa conference is business). */
        fun detect(destination: String, name: String, isOuting: Boolean): TripTheme {
            val text = " ${destination.lowercase()} ${name.lowercase()} "
            // Whole words only (plurals allowed), so "sea" doesn't match "Seattle".
            fun hit(words: List<String>) = words.any { w -> Regex("""(^|[^a-z])${Regex.escape(w)}(s|es)?(?![a-z])""").containsMatchIn(text) }
            return when {
                hit(BUSINESS_WORDS) -> BUSINESS
                hit(BEACH_WORDS) -> BEACH
                hit(HILL_WORDS) -> HILLS
                isOuting -> CAFE
                else -> VACATION
            }
        }
    }
}
