package com.woody.booklauncher

import java.time.LocalDate

/** Short public-domain / widely attributed reading quotes. No network needed. */
object Quotes {
    private val list = listOf(
        "A reader lives a thousand lives before he dies." to "George R. R. Martin",
        "There is no friend as loyal as a book." to "Ernest Hemingway",
        "Books are a uniquely portable magic." to "Stephen King",
        "I cannot live without books." to "Thomas Jefferson",
        "A book is a dream that you hold in your hands." to "Neil Gaiman",
        "Reading is to the mind what exercise is to the body." to "Joseph Addison",
        "The more that you read, the more things you will know." to "Dr. Seuss",
        "Never trust anyone who has not brought a book with them." to "Lemony Snicket",
        "We read to know we are not alone." to "C. S. Lewis",
        "A room without books is like a body without a soul." to "Cicero",
        "So many books, so little time." to "Frank Zappa",
        "Books are the quietest and most constant of friends." to "Charles W. Eliot",
        "Reading gives us someplace to go when we have to stay where we are." to "Mason Cooley",
        "The world is a book and those who do not travel read only one page." to "Augustine",
        "Until I feared I would lose it, I never loved to read." to "Harper Lee",
        "There is more treasure in books than in all the pirate's loot." to "Walt Disney",
        "Reading is a conversation. All books talk." to "Mark Haddon",
        "A book must be the axe for the frozen sea within us." to "Franz Kafka",
        "I have always imagined that Paradise will be a kind of library." to "Jorge Luis Borges",
        "Books are mirrors: you only see in them what you already have inside you." to "Carlos Ruiz Zafón",
        "The man who does not read good books has no advantage over the man who cannot read them." to "Mark Twain",
        "Read the best books first, or you may not have a chance to read them at all." to "Henry David Thoreau",
        "A reader is someone who has a mind open to new ideas." to "Anonymous",
        "Stories are equipment for living." to "Kenneth Burke",
    )

    /** Stable for a day, different each day. */
    fun of(day: LocalDate = LocalDate.now()): Pair<String, String> = list[Math.floorMod(day.toEpochDay(), list.size.toLong()).toInt()]
}
