package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.ai.AiBrain
import com.example.data.ai.AssistantAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Zoya AI", appName)
    }

    @Test
    fun `test voice folder copy command parsing`() {
        val result = AiBrain.parseCommandLocally("Downloads sy Documents me folder copy kr do")
        assertTrue(result.action is AssistantAction.CopyFolder)
        val copyAction = result.action as AssistantAction.CopyFolder
        assertEquals("Downloads", copyAction.source)
        assertEquals("Documents", copyAction.target)
    }

    @Test
    fun `test phone call command parsing`() {
        val result = AiBrain.parseCommandLocally("Ammi ko call karo")
        assertTrue(result.action is AssistantAction.CallPhone)
        val callAction = result.action as AssistantAction.CallPhone
        assertEquals("Ammi", callAction.target)
    }

    @Test
    fun `test fresh news command parsing`() {
        val result = AiBrain.parseCommandLocally("AJ ki fresh news kiya ha")
        assertTrue(result.action is AssistantAction.GetNews)
    }
}
