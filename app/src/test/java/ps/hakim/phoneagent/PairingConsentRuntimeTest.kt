package ps.hakim.phoneagent

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog

/**
 * Limited Android-framework runtime checks, not an on-device security certification.
 * No link is accepted, no external network connection is made, no phone is touched.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PairingConsentRuntimeTest {
    private val topic = "unitTestCommandTopic_ABCDEF12345"
    private val resultTopic = "unitTestResultTopic_ABCDE12345"
    private val key = "K".repeat(48)
    private val token = "T".repeat(48)

    private fun prefs() =
        RuntimeEnvironment.getApplication().getSharedPreferences("hakim", Context.MODE_PRIVATE)

    @Before
    fun resetPairing() {
        // Explicit disable prevents startup of any pre-existing relay in this test environment.
        assertTrue(
            prefs().edit()
                .clear()
                .putBoolean("pairing_disabled_by_user", true)
                .putString(HakimUnifiedRelay.KEY_TOPIC, "existingChannelStillActive")
                .putString("pair_token", "old-pair-token")
                .commit()
        )
        RuntimeEnvironment.getApplication()
            .getSharedPreferences("hakim_remote_pending", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun validLink(): Uri = Uri.Builder()
        .scheme("hakim")
        .authority("pair")
        .appendQueryParameter("token", token)
        .appendQueryParameter("relay_topic", topic)
        .appendQueryParameter("relay_result_topic", resultTopic)
        .appendQueryParameter("relay_key", key)
        .build()

    @Test
    fun untrustedLinkDoesNotPairUntilExplicitApprovalAndCancelKeepsOldState() {
        val activity = Robolectric.buildActivity(
            HakimPairingActivity::class.java, Intent(Intent.ACTION_VIEW, validLink())
        ).setup().get()

        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull("A valid external link must show a phone-local consent dialog", dialog)
        assertTrue(dialog.isShowing)
        val promptText = dialog.findViewById<TextView>(android.R.id.message)?.text?.toString().orEmpty()
        assertTrue(promptText.contains("hakim-chatgpt-bridge-production.up.railway.app"))
        assertFalse("Never show relay secret", promptText.contains(key))
        assertFalse("Never show pairing token", promptText.contains(token))
        assertEquals("existingChannelStillActive", prefs().getString(HakimUnifiedRelay.KEY_TOPIC, null))
        assertEquals("old-pair-token", prefs().getString("pair_token", null))
        assertTrue(prefs().getBoolean("pairing_disabled_by_user", false))

        dialog.getButton(DialogInterface.BUTTON_NEGATIVE).performClick()

        assertEquals("existingChannelStillActive", prefs().getString(HakimUnifiedRelay.KEY_TOPIC, null))
        assertEquals("old-pair-token", prefs().getString("pair_token", null))
        assertTrue(prefs().getBoolean("pairing_disabled_by_user", false))
        assertTrue(activity.isFinishing)
    }

    @Test
    fun invalidExternalLinkNeverChangesExistingPairing() {
        val invalid = Uri.parse("hakim://pair?token=short&relay_topic=bogus")
        val activity = Robolectric.buildActivity(
            HakimPairingActivity::class.java, Intent(Intent.ACTION_VIEW, invalid)
        ).setup().get()

        assertTrue(activity.isFinishing)
        assertEquals("existingChannelStillActive", prefs().getString(HakimUnifiedRelay.KEY_TOPIC, null))
        assertEquals("old-pair-token", prefs().getString("pair_token", null))
        assertTrue(prefs().getBoolean("pairing_disabled_by_user", false))
    }

    @Test
    fun rejectedConfigurationLeavesStoredSettingsUntouched() {
        val before = prefs().all.toMap()
        assertFalse(
            HakimUnifiedRelay.configure(
                RuntimeEnvironment.getApplication(), "bad", resultTopic, key, null, token
            )
        )
        assertEquals(before, prefs().all)
    }

    @Test
    fun approvedConfigurationWritesAllFieldsAndRevokesOldPending() {
        val app = RuntimeEnvironment.getApplication()
        val pending = app.getSharedPreferences("hakim_remote_pending", Context.MODE_PRIVATE)
        assertTrue(pending.edit().putString("oldRequestId.envelope", "{}")
            .putString("oldRequestId.session", "old-session").commit())

        assertTrue(HakimUnifiedRelay.configure(app, topic, resultTopic, key, null, token))

        assertEquals(topic, prefs().getString(HakimUnifiedRelay.KEY_TOPIC, null))
        assertEquals(resultTopic, prefs().getString(HakimUnifiedRelay.KEY_RESULT_TOPIC, null))
        assertEquals(key, prefs().getString(HakimUnifiedRelay.KEY_RELAY_KEY, null))
        assertEquals(token, prefs().getString("pair_token", null))
        assertNotNull(prefs().getString(HakimUnifiedRelay.KEY_APPROVAL_SESSION, null))
        assertTrue(prefs().getBoolean("secure_relay_configured", false))
        assertFalse(prefs().getBoolean("pairing_disabled_by_user", true))
        assertTrue(prefs().getLong("secure_pairing_at", 0) > 0)
        assertTrue("Old pending approvals must be revoked", pending.all.isEmpty())
    }
}
