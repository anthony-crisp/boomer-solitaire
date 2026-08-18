package com.boomersolitaire.engine

import org.junit.Test

/** Diagnostic bench, not a correctness test. */
class SolverBenchTest {

    @Test
    fun bench() {
        for (budget in listOf(50_000)) {
            var solved = 0
            var unsolvable = 0
            var exceeded = 0
            val t0 = System.currentTimeMillis()
            for (seed in 100L until 120L) {
                when (Solver.solve(Dealer.deal(seed, 1), budget)) {
                    Solver.Result.SOLVED -> solved++
                    Solver.Result.UNSOLVABLE -> unsolvable++
                    Solver.Result.BUDGET_EXCEEDED -> exceeded++
                }
            }
            println("budget=$budget solved=$solved unsolvable=$unsolvable exceeded=$exceeded time=${System.currentTimeMillis() - t0}ms")
        }
    }
}
