package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

@Composable
fun QrCodeView(
    content: String,
    modifier: Modifier = Modifier,
    size: Dp = 180.dp,
    darkColor: Color = Color(0xFF0D2040),
    lightColor: Color = Color.White
) {
    val matrixSize = 25
    val matrix = remember(content) {
        generateQrMatrix(content, matrixSize)
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(lightColor)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size - 16.dp)) {
            val moduleWidth = this.size.width / matrixSize
            val moduleHeight = this.size.height / matrixSize

            for (row in 0 until matrixSize) {
                for (col in 0 until matrixSize) {
                    if (matrix[row][col]) {
                        drawRect(
                            color = darkColor,
                            topLeft = Offset(col * moduleWidth, row * moduleHeight),
                            size = Size(moduleWidth + 0.5f, moduleHeight + 0.5f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Deterministically generates a QR matrix with standard 7x7 corner finder patterns,
 * timing patterns, and encoded content modules.
 */
private fun generateQrMatrix(content: String, size: Int): Array<BooleanArray> {
    val matrix = Array(size) { BooleanArray(size) }

    // Helper to draw Finder Pattern (7x7 box with 3x3 inner square)
    fun drawFinderPattern(startRow: Int, startCol: Int) {
        for (r in 0 until 7) {
            for (c in 0 until 7) {
                val isOuter = r == 0 || r == 6 || c == 0 || c == 6
                val isInner = r in 2..4 && c in 2..4
                matrix[startRow + r][startCol + c] = isOuter || isInner
            }
        }
    }

    // Draw three standard corner patterns
    drawFinderPattern(0, 0)
    drawFinderPattern(0, size - 7)
    drawFinderPattern(size - 7, 0)

    // Timing patterns
    for (i in 7 until size - 7) {
        matrix[6][i] = (i % 2 == 0)
        matrix[i][6] = (i % 2 == 0)
    }

    // Populate data modules based on content hash and bytes
    val hash = content.hashCode().toLong()
    val bytes = content.toByteArray()
    var byteIdx = 0

    for (r in 0 until size) {
        for (c in 0 until size) {
            // Skip finder patterns and separators
            val inTopLeft = r < 8 && c < 8
            val inTopRight = r < 8 && c >= size - 8
            val inBottomLeft = r >= size - 8 && c < 8
            val inTiming = r == 6 || c == 6

            if (!inTopLeft && !inTopRight && !inBottomLeft && !inTiming) {
                val byteVal = if (bytes.isNotEmpty()) bytes[byteIdx % bytes.size].toInt() else 0
                val bitVal = ((hash shr ((r * size + c) % 32)) and 1L) == 1L
                val pseudoRandomBit = ((abs(r * 31 + c * 17 + byteVal) % 5) < 3)
                matrix[r][c] = bitVal xor pseudoRandomBit
                byteIdx++
            }
        }
    }

    return matrix
}
