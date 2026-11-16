package com.example.domain.tournament

/**
 * G-02 / I-13: Deterministischer PRNG (Mulberry32) ohne Math.random().
 * Alle Auslosungen, Ziehungen und Multi-Start-Läufe verwenden ausschließlich diesen seedbaren Generator.
 */
class Mulberry32(seed: Int) {
    private var state: UInt = seed.toUInt()

    fun nextUInt(): UInt {
        state = state + 0x6D2B79F5u
        var t = state
        t = (t xor (t shr 15)) * (t or 1u)
        t = t xor (t + (t xor (t shr 7)) * (t or 61u))
        return t xor (t shr 14)
    }

    /** Returns deterministic integer in [0, bound) without floating point arithmetic. */
    fun nextInt(bound: Int): Int {
        require(bound > 0) { "bound must be positive" }
        return (nextUInt() % bound.toUInt()).toInt()
    }

    fun <T> shuffled(items: List<T>): List<T> {
        val copy = items.toMutableList()
        for (i in copy.size - 1 downTo 1) {
            val j = nextInt(i + 1)
            val tmp = copy[i]
            copy[i] = copy[j]
            copy[j] = tmp
        }
        return copy
    }
}
