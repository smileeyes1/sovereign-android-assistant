package ps.hakim.phoneagent

import android.content.Context

/**
 * Minimal local education role profile. No name, school, student id, or account identifier is
 * required to select a role. Managed deployments may override the local role.
 */
object HakimEducationProfile {
    const val VERSION = "EDUCATION-ROLE-2026-09-24-v1"
    private const val PREFS = "hakim_education_profile"
    private const val KEY_ROLE = "role"

    enum class Role(val wire: String, val arabicLabel: String) {
        GENERAL("general", "استخدام عام"),
        TEACHER("teacher", "معلم/ة"),
        STUDENT("student", "طالب/ة"),
        SCHOOL_ADMIN("school_admin", "إدارة مدرسية"),
        SUPERVISOR("supervisor", "إشراف تربوي"),
        STAFF("staff", "موظف/ة"),
        GUARDIAN("guardian", "ولي أمر")
    }

    fun current(context: Context): Role {
        HakimEnterprisePolicy.forcedEducationRole(context)?.let { return it }
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ROLE, Role.GENERAL.wire)
            .orEmpty()
        return Role.entries.firstOrNull { it.wire == raw } ?: Role.GENERAL
    }

    fun set(context: Context, role: Role) {
        if (HakimEnterprisePolicy.forcedEducationRole(context) != null) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ROLE, role.wire)
            .apply()
    }
}
