package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.util.CollageGenerator
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("WiFi PM Helper", appName)
    }

    @Test
    fun `collage generator produces file strictly under 1MB`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val samplePhotos = List(4) { idx ->
            Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888).apply {
                eraseColor(if (idx % 2 == 0) Color.CYAN else Color.DKGRAY)
            }
        }
        val result = CollageGenerator.create2x2CollageUnder1Mb(
            context = context,
            photos = samplePhotos,
            locationTitle = "Area R. Tunggu samping mamma roti loby T3 Inter, Terminal 3",
            dateLabel = "17 October 2026",
            speedSummary = "Up 44.6Mbps / Down 66.7Mbps"
        )
        assertTrue("Collage must be < 1 MB", result.sizeBytes in 1..999_999)
        assertTrue(result.file.exists())
    }
}
