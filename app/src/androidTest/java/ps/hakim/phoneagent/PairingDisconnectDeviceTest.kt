package ps.hakim.phoneagent

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device-emulator smoke tests, with deliberately fake pairing values and no external credentials.
 * They prove UI actions, not cancellation of OS actions already in flight.
 */
@RunWith(AndroidJUnit4::class)
class PairingDisconnectDeviceTest {
    private val app: Context = ApplicationProvider.getApplicationContext()
    private val store get() = app.getSharedPreferences("hakim", Context.MODE_PRIVATE)
    private val pending get() = app.getSharedPreferences("hakim_remote_pending", Context.MODE_PRIVATE)

    @Before
    fun initialState() {
        assertTrue(store.edit().clear()
            .putBoolean("pairing_disabled_by_user", true)
            .putString(HakimUnifiedRelay.KEY_APPROVAL_SESSION, "initial-session")
            .putString("pair_token", "old-fake-token")
            .commit())
        assertTrue(pending.edit().clear().commit())
    }

    private fun findDisconnect(root: View): Button? {
        if (root is Button && root.text.toString() == "فصل الاقتران") return root
        if (root is ViewGroup) for (idx in 0 until root.childCount) {
            val b = findDisconnect(root.getChildAt(idx))
            if (b != null) return b
        }
        return null
    }

    @Test
    fun disconnectButtonRevokesOldPendingAndRotatesSession() {
        val id = "old-test-request"
        assertTrue(pending.edit()
            .putString("$id.envelope", """{"request_id":"old-test-request","op":"action"}""")
            .putString("$id.result_topic", "fake-topic")
            .putString("$id.session", "initial-session")
            .commit())

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                // Do not start the remote service: enable display only, not a valid full pairing.
                assertTrue(store.edit()
                    .putString(HakimUnifiedRelay.KEY_TOPIC, "fake-local-topic")
                    .putString(HakimUnifiedRelay.KEY_RESULT_TOPIC, "fake-result-topic")
                    .putString(HakimUnifiedRelay.KEY_RELAY_KEY, "K".repeat(48))
                    .putBoolean("pairing_disabled_by_user", false)
                    .commit())
                val refresh = activity.javaClass.getDeclaredMethod("refreshPairingUi")
                refresh.isAccessible = true
                refresh.invoke(activity)
                val button = findDisconnect(activity.window.decorView)
                assertNotNull("Disconnect control must be reachable", button)
                assertTrue(button!!.performClick())
            }
        }

        assertTrue("User disconnect must persist", store.getBoolean("pairing_disabled_by_user", false))
        assertNotEquals("initial-session", store.getString(HakimUnifiedRelay.KEY_APPROVAL_SESSION, null))
        assertFalse(store.contains(HakimUnifiedRelay.KEY_TOPIC))
        assertFalse(store.contains(HakimUnifiedRelay.KEY_RESULT_TOPIC))
        assertFalse(store.contains(HakimUnifiedRelay.KEY_RELAY_KEY))
        assertFalse(store.contains("pair_token"))
        assertTrue("Old approvals must be cleared", pending.all.isEmpty())
        HakimUnifiedRelay.handleApproval(app, id, true)
        assertTrue("Old approval cannot be restored by a late callback", pending.all.isEmpty())
    }

    @Test
    fun queuedSecureResultAfterOwnerDisconnectAndRepairNeverSends() {
        // All identifiers are synthetic; the release latch keeps the result off the network.
        val oldTopic = "fakeSecureResult_ABCDEF123456789"
        val oldCommand = "fakeSecureCommand_ABCDEF12345"
        val oldKey = "K".repeat(48)
        val token = "T".repeat(48)
        assertTrue(HakimUnifiedRelay.configure(app, oldCommand, oldTopic, oldKey, null, token))
        val oldSession = store.getString(HakimUnifiedRelay.KEY_APPROVAL_SESSION, null)!!
        val send = HakimUnifiedRelay::class.java.getDeclaredMethod(
            "sendResult", Context::class.java, String::class.java, String::class.java,
            String::class.java, org.json.JSONObject::class.java, String::class.java
        )
        send.isAccessible = true
        fun oldResult(): Boolean = send.invoke(
            HakimUnifiedRelay, app, oldTopic, "delayed-fake-request",
            "ok", org.json.JSONObject().put("ok", true), oldSession
        ) as Boolean

        val worker = java.util.concurrent.Executors.newSingleThreadExecutor()
        val ready = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        try {
            val delayed = worker.submit<Boolean> {
                ready.countDown()
                check(release.await(8, java.util.concurrent.TimeUnit.SECONDS))
                oldResult()
            }
            assertTrue("The synthetic action should be queued before disconnect",
                ready.await(5, java.util.concurrent.TimeUnit.SECONDS))
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val button = findDisconnect(activity.window.decorView)
                    assertNotNull(button)
                    assertTrue(button!!.performClick())
                }
            }
            assertTrue(store.getBoolean("pairing_disabled_by_user", false))
            assertNotEquals(oldSession, store.getString(HakimUnifiedRelay.KEY_APPROVAL_SESSION, null))
            release.countDown()
            assertFalse("Delayed old-session result must be rejected before any HTTP call",
                delayed.get(6, java.util.concurrent.TimeUnit.SECONDS))

            assertTrue(HakimUnifiedRelay.configure(
                app, "newSecureCommand_ABCDEF12345", "newSecureResult_ABCDEF12345678",
                "N".repeat(48), null, token
            ))
            assertFalse("Re-pairing must not make the old result valid again", oldResult())
        } finally {
            release.countDown()
            worker.shutdownNow()
        }
    }

    @Test
    fun externalDeepLinkCancelNeverSavesPairing() {
        val uri = Uri.Builder().scheme("hakim").authority("pair")
            .appendQueryParameter("token", "T".repeat(48))
            .appendQueryParameter("relay_topic", "unitTestCommandTopic_ABCDEF12345")
            .appendQueryParameter("relay_result_topic", "unitTestResultTopic_ABCDE12345")
            .appendQueryParameter("relay_key", "K".repeat(48))
            .build()
        val intent = Intent(Intent.ACTION_VIEW, uri).setClass(app, HakimPairingActivity::class.java)
        ActivityScenario.launch<HakimPairingActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                val field = activity.javaClass.getDeclaredField("pairingDialog")
                field.isAccessible = true
                val dialog = field.get(activity) as? AlertDialog
                assertNotNull("Untrusted external pairing requires a visible dialog", dialog)
                assertTrue(dialog!!.isShowing)
                assertTrue(dialog.getButton(DialogInterface.BUTTON_NEGATIVE).performClick())
            }
        }
        assertTrue(store.getBoolean("pairing_disabled_by_user", false))
        assertEquals("old-fake-token", store.getString("pair_token", null))
        assertFalse(store.getBoolean("secure_relay_configured", false))
    }
}
