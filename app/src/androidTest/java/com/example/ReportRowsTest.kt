package com.example

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import com.example.ui.screens.ReportRows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class ReportRowsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersMoreThanFiveRows() {
        composeRule.setContent {
            ReportRows((1..100).map { listOf("row-$it") })
        }

        composeRule.onNodeWithTag("report-rows")
            .performScrollToNode(hasText("row-100"))
    }
}
