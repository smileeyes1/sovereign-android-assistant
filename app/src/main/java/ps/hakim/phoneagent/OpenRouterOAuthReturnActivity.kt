package ps.hakim.phoneagent

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

/** Returns the one-time browser authorization flow back to the existing Hakim conversation. */
class OpenRouterOAuthReturnActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val status = intent?.data?.getQueryParameter("status").orEmpty()
        val success = status == "success" &&
            HakimSecretStore.has(this, OpenRouterFreeEngine.SECRET_OPENROUTER_KEY)

        Toast.makeText(
            this,
            if (success) "تم ربط الذكاء المجاني؛ سيستأنف حكيم مقصدك." else "لم يكتمل الربط المجاني.",
            Toast.LENGTH_LONG
        ).show()

        val target = Intent(this, CommandCenterActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("openrouter_oauth_status", if (success) "success" else "failed")
            putExtra("resume_after_free_oauth", success)
        }
        startActivity(target)
        finish()
    }
}
