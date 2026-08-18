package com.boomersolitaire.engine

import kotlin.random.Random

object WinnableDealer {

    data class Deal(val seed: Long, val state: GameState, val provenWinnable: Boolean)

    /** A random deal, no winnability guarantee. */
    fun randomDeal(drawCount: Int, random: Random = Random): Deal {
        val seed = random.nextLong()
        return Deal(seed, Dealer.deal(seed, drawCount), provenWinnable = false)
    }

    /**
     * Keep dealing until the solver proves a deal winnable. [maxAttempts] is a
     * safety net only — most deals are winnable and the solver finds a line
     * within a few attempts. Falls back to a random deal if the net is hit.
     */
    fun winnableDeal(
        drawCount: Int,
        random: Random = Random,
        maxNodesPerAttempt: Int = 200_000,
        maxAttempts: Int = 25,
    ): Deal {
        repeat(maxAttempts) {
            val seed = random.nextLong()
            val state = Dealer.deal(seed, drawCount)
            if (Solver.solve(state, maxNodesPerAttempt) == Solver.Result.SOLVED) {
                return Deal(seed, state, provenWinnable = true)
            }
        }
        return randomDeal(drawCount, random)
    }
}
