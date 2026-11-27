package com.anggik.keyboard.engine

import kotlin.math.hypot

class GlideTypingEngine {

    private val keyCenterX = FloatArray(26) { -1f }
    private val keyCenterY = FloatArray(26) { -1f }
    private var keyUnitWidth = 40f

    fun updateKeyGeometry(charCode: Char, centerX: Float, centerY: Float, unitWidth: Float) {
        val idx = charCode.lowercaseChar().code - 'a'.code
        if (idx in 0..25) {
            keyCenterX[idx] = centerX
            keyCenterY[idx] = centerY
            if (unitWidth > 0f) keyUnitWidth = unitWidth
        }
    }

    fun decodeTrajectory(
        xs: FloatArray,
        ys: FloatArray,
        pointCount: Int,
        startChar: Char,
        endChar: Char,
        predictor: LocalWordPredictor,
        isIndonesian: Boolean,
        outCandidates: MutableList<String>
    ): String? {
        outCandidates.clear()
        if (pointCount < 2) return null
        val sChar = startChar.lowercaseChar()
        val eChar = endChar.lowercaseChar()
        if (sChar !in 'a'..'z' || eChar !in 'a'..'z') return null

        val eIdx = eChar.code - 'a'.code
        val ex = keyCenterX[eIdx]
        val ey = keyCenterY[eIdx]
        val maxEndDist = keyUnitWidth * 1.45f

        var bestWord1: String? = null
        var bestScore1 = Float.MAX_VALUE
        var bestWord2: String? = null
        var bestScore2 = Float.MAX_VALUE
        var bestWord3: String? = null
        var bestScore3 = Float.MAX_VALUE

        fun evaluateWord(word: String) {
            if (word.length < 2 || word[0] != sChar) return
            val lastIdx = word[word.length - 1].code - 'a'.code
            if (lastIdx !in 0..25) return
            val endDist = hypot(keyCenterX[lastIdx] - ex, keyCenterY[lastIdx] - ey)
            if (endDist > maxEndDist) return

            var pathCursor = 0
            var totalDist = endDist * 1.2f
            for (cIdx in 1 until word.length - 1) {
                val kIdx = word[cIdx].code - 'a'.code
                if (kIdx !in 0..25) return
                val kx = keyCenterX[kIdx]
                val ky = keyCenterY[kIdx]

                var minD = Float.MAX_VALUE
                var bestStep = pathCursor
                val searchLimit = (pathCursor + (pointCount / 2) + 4).coerceAtMost(pointCount - 1)
                for (p in pathCursor..searchLimit) {
                    val d = hypot(xs[p] - kx, ys[p] - ky)
                    if (d < minD) {
                        minD = d
                        bestStep = p
                    }
                }
                pathCursor = bestStep
                totalDist += minD
            }

            val normalizedScore = totalDist / word.length
            if (normalizedScore < bestScore1) {
                bestScore3 = bestScore2; bestWord3 = bestWord2
                bestScore2 = bestScore1; bestWord2 = bestWord1
                bestScore1 = normalizedScore; bestWord1 = word
            } else if (normalizedScore < bestScore2 && word != bestWord1) {
                bestScore3 = bestScore2; bestWord3 = bestWord2
                bestScore2 = normalizedScore; bestWord2 = word
            } else if (normalizedScore < bestScore3 && word != bestWord1 && word != bestWord2) {
                bestScore3 = normalizedScore; bestWord3 = word
            }
        }

        for (w in predictor.getLearnedWords()) evaluateWord(w)
        val dict = predictor.getLexicon(isIndonesian)
        for (w in dict) {
            if (w.isNotEmpty() && w[0] == sChar) {
                evaluateWord(w)
            }
        }

        bestWord1?.let { outCandidates.add(it) }
        bestWord2?.let { outCandidates.add(it) }
        bestWord3?.let { outCandidates.add(it) }
        return bestWord1
    }
}
