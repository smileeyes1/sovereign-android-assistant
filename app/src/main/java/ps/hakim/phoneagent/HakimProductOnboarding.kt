package ps.hakim.phoneagent

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

/**
 * تهيئة أول تشغيل للمنتج.
 *
 * لا تمنح صلاحية ولا توافق نيابة عن المستخدم؛ تشرح حدود النسخة وتترك
 * الصلاحيات الحساسة لبوابات Android/حكيم المنفصلة.
 */
object HakimProductOnboarding {
    private const val PREFS = "hakim_product_onboarding"
    private const val KEY_ACCEPTED_VERSION = "accepted_version_code"

    fun showIfNeeded(activity: Activity) {
        val version = currentVersionCode(activity)
        val p = activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE)
        if (p.getLong(KEY_ACCEPTED_VERSION, -1L) == version) return

        val editionText = if (HakimProductEdition.isAdvanced) {
            "نسخة Advanced تتضمن أدوات تحكم متقدمة، وقد تطلب صلاحيات منفصلة عند الحاجة. لا تُمنح أي صلاحية حساسة من هذه الشاشة."
        } else {
            "نسخة Consumer مصممة للاستخدام اليومي بأقل صلاحيات، ولا تتضمن طبقات Termux أو VPN أو التحكم عبر إمكانية الوصول."
        }

        AlertDialog.Builder(activity)
            .setTitle("مرحبًا بك في حكيم")
            .setMessage(
                "حكيم يساعدك على إنجاز المهمة داخل التطبيق، ويعالج ما يستطيع محليًا أولًا. " +
                    "عندما تحتاج المهمة خدمة ذكاء خارجية سيطلب منك ربطها بوضوح. " +
                    "يمكنك حذف السجل المحلي وفصل الخدمات من الإعدادات في أي وقت.\n\n" +
                    editionText
            )
            .setPositiveButton("ابدأ") { _, _ ->
                p.edit().putLong(KEY_ACCEPTED_VERSION, version).apply()
            }
            .setNeutralButton("الخصوصية والإعدادات") { _, _ ->
                activity.startActivity(Intent(activity, UnifiedHomeActivity::class.java))
            }
            .setCancelable(false)
            .show()
    }

    @Suppress("DEPRECATION")
    private fun currentVersionCode(activity: Activity): Long = try {
        val info = if (Build.VERSION.SDK_INT >= 33) {
            activity.packageManager.getPackageInfo(
                activity.packageName,
                PackageManager.PackageInfoFlags.of(0)
            )
        } else {
            activity.packageManager.getPackageInfo(activity.packageName, 0)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode
        else info.versionCode.toLong()
    } catch (_: Exception) {
        0L
    }
}
