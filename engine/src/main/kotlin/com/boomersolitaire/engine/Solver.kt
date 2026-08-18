package com.boomersolitaire.engine

/**
 * Winnability solver for Klondike (full knowledge of the deal).
 *
 * Pruned iterative DFS over [Rules]-generated moves with a transposition
 * table, a node budget, and greedy auto-play of provably safe foundation
 * moves. It is deliberately conservative: it never plays foundation-to-
 * tableau moves, so "winnable" means winnable without ever digging cards
 * back out of the foundations.
 */
object Solver {

    enum class Result { SOLVED, UNSOLVABLE, BUDGET_EXCEEDED }

    fun isWinnable(state: GameState, maxNodes: Int = 200_000): Boolean =
        solve(state, maxNodes) == Result.SOLVED

    /**
     * Search with restarts: the first attempt breaks score ties newest-first
     * (deep dives); stuck deals are retried with randomised tie-breaking,
     * which explores a different part of the tree each time.
     */
    fun solve(start: GameState, maxNodes: Int = 200_000, restarts: Int = 2): Result {
        var result = solveOnce(start, maxNodes, random = null)
        var attempt = 0
        while (result == Result.BUDGET_EXCEEDED && attempt < restarts) {
            attempt++
            result = solveOnce(start, maxNodes, random = kotlin.random.Random(attempt.toLong()))
        }
        return result
    }

    private fun solveOnce(start: GameState, maxNodes: Int, random: kotlin.random.Random?): Result {
        // Greedy best-first search: states with more progress are expanded
        // first; among equal scores the most recently discovered state wins
        // (LIFO), which keeps the search diving like a DFS instead of
        // wandering breadth-first through equivalent stock positions.
        val visited = HashSet<String>()
        var stamp = 0L
        val frontier = java.util.PriorityQueue<Node>(
            compareByDescending<Node> { it.score }.thenByDescending { it.stamp },
        )
        var nodes = 0
        fun nextStamp(): Long = random?.nextLong() ?: stamp++

        val root = normalize(start)
        if (root.isWon) return Result.SOLVED
        visited.add(encode(root))
        frontier.add(Node(root, score(root), nextStamp()))

        while (frontier.isNotEmpty()) {
            val node = frontier.poll()
            for (next in successors(node.state)) {
                if (next.isWon) return Result.SOLVED
                if (nodes++ >= maxNodes) return Result.BUDGET_EXCEEDED
                if (!visited.add(encode(next))) continue
                frontier.add(Node(next, score(next), nextStamp()))
            }
        }
        return Result.UNSOLVABLE
    }

    /**
     * Successor states. Tableau moves come from [candidateMoves]; the stock is
     * handled with macro-moves — draws never change the tableau, so instead of
     * searching through draw-by-draw states we enumerate every waste card
     * reachable by consecutive draws (recycling once if needed) and emit
     * "draw until reachable, then play it" states directly.
     */
    private fun successors(state: GameState): List<GameState> {
        val out = ArrayList<GameState>()
        for (move in candidateMoves(state)) {
            val next = Rules.apply(state, move) ?: continue
            out += normalize(next)
        }

        // Macro stock moves. Walk the draw cycle; from each configuration try
        // playing the waste top to a foundation or the tableau.
        var cursor = state
        val seenConfigs = HashSet<Int>()
        seenConfigs.add(stockConfigKey(cursor))
        while (true) {
            cursor = when {
                cursor.stock.isNotEmpty() -> Rules.apply(cursor, Move.Draw)!!
                cursor.waste.isNotEmpty() -> Rules.apply(cursor, Move.Recycle)!!
                else -> break
            }
            if (!seenConfigs.add(stockConfigKey(cursor))) break
            val card = cursor.wasteTop ?: continue
            if (Rules.canPlaceOnFoundation(cursor, card)) {
                out += normalize(Rules.apply(cursor, Move.WasteToFoundation(card.suit))!!)
            }
            cursor.tableau.forEachIndexed { i, col ->
                if (Rules.canPlaceOnTableau(col, card)) {
                    out += normalize(Rules.apply(cursor, Move.WasteToTableau(i))!!)
                }
            }
        }
        return out
    }

    /** Identity of a stock/waste configuration within the draw cycle. */
    private fun stockConfigKey(state: GameState): Int =
        state.stock.size * 64 + (state.wasteTop?.id ?: 53)

    private class Node(val state: GameState, val score: Int, val stamp: Long)

    /** Heuristic progress score: foundations and uncovered cards dominate. */
    private fun score(state: GameState): Int {
        val foundationTotal = state.foundations.sum()
        val faceDown = state.faceDownCardCount
        val emptyColumns = state.tableau.count { it.cards.isEmpty() }
        return foundationTotal * 6 + (21 - faceDown) * 20 + emptyColumns * 8
    }

    /**
     * A foundation play is safe when both opposite-colour foundations have
     * reached at least rank-1: no tableau build could ever need this card.
     */
    fun isSafeFoundationPlay(state: GameState, card: Card): Boolean {
        if (card.rank <= 2) return true
        val minOpposite = Suit.entries
            .filter { it.isRed != card.suit.isRed }
            .minOf { state.foundations[it.ordinal] }
        return minOpposite >= card.rank - 1
    }

    /** Greedily apply all safe foundation plays; never loses winnability. */
    fun normalize(start: GameState): GameState {
        var state = start
        while (true) {
            var played = false
            state.wasteTop?.let { card ->
                if (Rules.canPlaceOnFoundation(state, card) && isSafeFoundationPlay(state, card)) {
                    state = Rules.apply(state, Move.WasteToFoundation(card.suit))!!
                    played = true
                }
            }
            if (!played) {
                for (i in state.tableau.indices) {
                    val col = state.tableau[i]
                    val top = col.topCard ?: continue
                    if (col.faceDownCount == col.cards.size) continue
                    if (Rules.canPlaceOnFoundation(state, top) && isSafeFoundationPlay(state, top)) {
                        state = Rules.apply(state, Move.TableauToFoundation(i))!!
                        played = true
                        break
                    }
                }
            }
            if (!played) return state
        }
    }

    /** Pruned, priority-ordered successor moves for the search. */
    private fun candidateMoves(state: GameState): List<Move> {
        val flips = ArrayList<Move>()      // tableau moves that expose a face-down card
        val foundation = ArrayList<Move>() // unsafe foundation plays (safe ones were auto-played)
        val wastePlays = ArrayList<Move>()
        val other = ArrayList<Move>()

        state.tableau.forEachIndexed { i, col ->
            val top = col.topCard
            if (top != null && col.faceDownCount < col.cards.size && Rules.canPlaceOnFoundation(state, top)) {
                val move = Move.TableauToFoundation(i)
                if (col.cards.size == col.faceDownCount + 1 && col.faceDownCount > 0) flips += move
                else foundation += move
            }
        }
        state.wasteTop?.let { card ->
            if (Rules.canPlaceOnFoundation(state, card)) foundation += Move.WasteToFoundation(card.suit)
        }

        state.tableau.forEachIndexed { fromIdx, from ->
            if (from.cards.isEmpty()) return@forEachIndexed
            for (cardIndex in from.faceDownCount until from.cards.size) {
                val head = from.cards[cardIndex]
                val fullRun = cardIndex == from.faceDownCount
                // Partial runs only when the split lets the exposed card go to a foundation.
                if (!fullRun) {
                    val exposed = from.cards[cardIndex - 1]
                    if (!Rules.canPlaceOnFoundation(state, exposed)) continue
                }
                var emptyTargetUsed = false // empty columns are interchangeable targets
                state.tableau.forEachIndexed inner@{ toIdx, to ->
                    if (fromIdx == toIdx || !Rules.canPlaceOnTableau(to, head)) return@inner
                    if (to.cards.isEmpty()) {
                        // Moving a bare king between columns achieves nothing.
                        if (fullRun && from.faceDownCount == 0 && cardIndex == 0) return@inner
                        if (emptyTargetUsed) return@inner
                        emptyTargetUsed = true
                    }
                    val move = Move.TableauToTableau(fromIdx, cardIndex, toIdx)
                    if (fullRun && from.faceDownCount > 0) flips += move
                    else other += move
                }
            }
        }

        state.wasteTop?.let { card ->
            state.tableau.forEachIndexed { i, col ->
                if (Rules.canPlaceOnTableau(col, card)) wastePlays += Move.WasteToTableau(i)
            }
        }

        return flips + foundation + wastePlays + other
    }

    private fun encode(state: GameState): String {
        val sb = StringBuilder(72)
        for (f in state.foundations) sb.append(('A' + f))
        sb.append('|')
        for (col in state.tableau) {
            sb.append(('A' + col.faceDownCount))
            for (c in col.cards) sb.append(('0' + c.id))
            sb.append('|')
        }
        for (c in state.stock) sb.append(('0' + c.id))
        sb.append('|')
        for (c in state.waste) sb.append(('0' + c.id))
        return sb.toString()
    }
}
