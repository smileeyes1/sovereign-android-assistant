package ps.hakim.phoneagent

import android.content.SharedPreferences

/**
 * قيم البناء الخاص تُحقن في هذه الثوابت أثناء مسار الإصدار الخاص.
 * يبقى المصدر العام بلا أسرار؛ وعند بناء المصدر مباشرة تبقى placeholders ولا تُستخدم.
 */
object PairingDefaults {
    private const val COMMAND_TOPIC = "__HAKIM_COMMAND_TOPIC__"
    private const val RESULT_TOPIC = "__HAKIM_RESULT_TOPIC__"
    private const val AUTH_KEY = "__HAKIM_AUTH_KEY__"

    fun ensure(prefs: SharedPreferences) {
        // سيادة المستخدم: إذا فصل الاقتران بنفسه فلا يُعاد تلقائيًا عند الإقلاع أو التحديث.
        if (prefs.getBoolean("pairing_disabled_by_user", false)) return
        if (COMMAND_TOPIC.startsWith("__HAKIM_") || RESULT_TOPIC.startsWith("__HAKIM_")) return

        val edit = prefs.edit()
        if (prefs.getString("command_topic", "").orEmpty().isBlank()) {
            edit.putString("command_topic", COMMAND_TOPIC)
        }
        if (prefs.getString("result_topic", "").orEmpty().isBlank()) {
            edit.putString("result_topic", RESULT_TOPIC)
        }
        if (!AUTH_KEY.startsWith("__HAKIM_") && AUTH_KEY.isNotBlank()) {
            // يُحدَّث عند تحديث التطبيق ما دام المستخدم لم يفصل الاقتران يدويًا.
            edit.putString("auth_key", AUTH_KEY)
        }
        edit.apply()
    }
}
