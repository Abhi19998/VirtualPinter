package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.ui.ClearAllConfirmationDialog
import com.example.ui.DeleteConfirmationDialog
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeleteConfirmationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun deleteConfirmationDialog_rendersAndConfirms() {
        var confirmed = false
        var dismissed = false

        composeTestRule.setContent {
            MyApplicationTheme {
                DeleteConfirmationDialog(
                    fileName = "invoice_report_2026.ps",
                    fileSizeBytes = 204800L,
                    format = "PS",
                    onConfirm = { confirmed = true },
                    onDismiss = { dismissed = true }
                )
            }
        }

        // Verify Dialog title and file name are displayed
        composeTestRule.onNodeWithText("Clear from List?").assertExists()
        composeTestRule.onNodeWithText("invoice_report_2026.ps").assertExists()

        // Perform click on confirm button
        composeTestRule.onNodeWithTag("confirm_delete_button").performClick()
        assertTrue("Confirm callback should have been invoked", confirmed)
    }

    @Test
    fun deleteConfirmationDialog_dismissesProperly() {
        var dismissed = false

        composeTestRule.setContent {
            MyApplicationTheme {
                DeleteConfirmationDialog(
                    fileName = "test_print.pdf",
                    fileSizeBytes = 51200L,
                    format = "PDF",
                    onConfirm = { },
                    onDismiss = { dismissed = true }
                )
            }
        }

        composeTestRule.onNodeWithTag("cancel_delete_button").performClick()
        assertTrue("Dismiss callback should have been invoked", dismissed)
    }

    @Test
    fun clearAllConfirmationDialog_rendersAndConfirms() {
        var confirmed = false

        composeTestRule.setContent {
            MyApplicationTheme {
                ClearAllConfirmationDialog(
                    totalCount = 5,
                    onConfirm = { confirmed = true },
                    onDismiss = { }
                )
            }
        }

        composeTestRule.onNodeWithText("Clear History from UI?").assertExists()
        composeTestRule.onNodeWithTag("confirm_clear_all_button").performClick()
        assertTrue("Confirm clear all callback should have been invoked", confirmed)
    }
}
