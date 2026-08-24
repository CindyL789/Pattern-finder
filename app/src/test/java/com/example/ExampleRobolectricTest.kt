package com.example

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
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
    assertEquals("Stitch Mind", appName)
  }

  @Test
  fun `privacy policy url is public and non-placeholder`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val privacyPolicyUrl = context.getString(R.string.privacy_policy_url)

    assertTrue("URL should use https", privacyPolicyUrl.startsWith("https://"))
    assertTrue("URL should not be placeholder", !privacyPolicyUrl.contains("YOUR-DOMAIN.example"))
    assertTrue("URL should include host", !Uri.parse(privacyPolicyUrl).host.isNullOrBlank())
  }
}
