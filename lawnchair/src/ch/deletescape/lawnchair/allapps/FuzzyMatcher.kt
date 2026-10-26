/*
 *     This file is part of Launcher, a fork of Lawnchair Launcher.
 *
 *     Launcher is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Launcher is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Launcher.  If not, see <https://www.gnu.org/licenses/>.
 */

package ch.deletescape.lawnchair.allapps

import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Fuzzy string matching for the app search, a compact reimplementation of the "weighted ratio"
 * of fuzzywuzzy (which was used as a library before). Scores are between 0 and 100.
 */
object FuzzyMatcher {

    private const val UNBASE_SCALE = .95
    private const val PARTIAL_SCALE = .90
    private val NON_ALPHANUMERIC = Regex("[^\\p{L}\\p{N}]+")

    fun process(s: String): String =
            NON_ALPHANUMERIC.replace(s.lowercase(Locale.ROOT), " ").trim()

    /** Levenshtein based similarity (indel distance, like python-Levenshtein's ratio). */
    fun ratio(s1: String, s2: String): Int {
        val lenSum = s1.length + s2.length
        if (lenSum == 0) return 100
        val dist = indelDistance(s1, s2)
        return ((lenSum - dist) * 100.0 / lenSum).roundToInt()
    }

    /** Best ratio of the shorter string against all equally long windows of the longer one. */
    fun partialRatio(s1: String, s2: String): Int {
        val shorter = if (s1.length <= s2.length) s1 else s2
        val longer = if (s1.length <= s2.length) s2 else s1
        if (shorter.isEmpty()) return if (longer.isEmpty()) 100 else 0
        var best = 0
        for (start in 0..(longer.length - shorter.length)) {
            val r = ratio(shorter, longer.substring(start, start + shorter.length))
            if (r > best) {
                best = r
                if (best == 100) break
            }
        }
        return best
    }

    private fun tokens(s: String) = s.split(' ').filter { it.isNotEmpty() }

    private fun sortedTokens(s: String) = tokens(s).sorted().joinToString(" ")

    private fun tokenSort(s1: String, s2: String, partial: Boolean): Int {
        val a = sortedTokens(s1)
        val b = sortedTokens(s2)
        return if (partial) partialRatio(a, b) else ratio(a, b)
    }

    private fun tokenSet(s1: String, s2: String, partial: Boolean): Int {
        val t1 = tokens(s1).toSortedSet()
        val t2 = tokens(s2).toSortedSet()
        val intersection = t1.intersect(t2).sorted().joinToString(" ")
        val diff1 = (t1 - t2).sorted().joinToString(" ")
        val diff2 = (t2 - t1).sorted().joinToString(" ")
        val combined1 = "$intersection $diff1".trim()
        val combined2 = "$intersection $diff2".trim()
        val scorer: (String, String) -> Int = if (partial) ::partialRatio else ::ratio
        return max(max(scorer(intersection, combined1), scorer(intersection, combined2)),
                scorer(combined1, combined2))
    }

    /** fuzzywuzzy's WRatio, both strings are processed first. */
    fun weightedRatio(raw1: String, raw2: String): Int {
        val s1 = process(raw1)
        val s2 = process(raw2)
        if (s1.isEmpty() || s2.isEmpty()) return 0

        val base = ratio(s1, s2).toDouble()
        val lenRatio = max(s1.length, s2.length).toDouble() / min(s1.length, s2.length)
        if (lenRatio < 1.5) {
            val tokenSortScore = tokenSort(s1, s2, false) * UNBASE_SCALE
            val tokenSetScore = tokenSet(s1, s2, false) * UNBASE_SCALE
            return maxOf(base, tokenSortScore, tokenSetScore).roundToInt()
        }
        val partialScale = if (lenRatio > 8) .6 else PARTIAL_SCALE
        val partial = partialRatio(s1, s2) * partialScale
        val partialTokenSort = tokenSort(s1, s2, true) * UNBASE_SCALE * partialScale
        val partialTokenSet = tokenSet(s1, s2, true) * UNBASE_SCALE * partialScale
        return maxOf(base, partial, partialTokenSort, partialTokenSet).roundToInt()
    }

    /** Number of insertions and deletions needed to turn [a] into [b]. */
    private fun indelDistance(a: String, b: String): Int {
        if (a == b) return 0
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            val ca = a[i - 1]
            for (j in 1..b.length) {
                cur[j] = if (ca == b[j - 1]) {
                    prev[j - 1]
                } else {
                    min(prev[j], cur[j - 1]) + 1
                }
            }
            val tmp = prev
            prev = cur
            cur = tmp
        }
        return prev[b.length]
    }
}
