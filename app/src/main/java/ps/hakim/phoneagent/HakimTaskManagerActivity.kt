package ps.hakim.phoneagent

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity

/**
 * لوحة محلية لمدير مهام حكيم.
 * لا تعرض أسرارًا أو بيانات جلسات؛ فقط المقاصد والحالات التي أنشأها حكيم محليًا.
 */
class HakimTaskManagerActivity : ComponentActivity() {
    private lateinit var root: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            gravity = Gravity.TOP or Gravity.RIGHT
            setPadding(24, 24, 24, 32)
        }
        scroll.addView(root)
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        HakimTaskManager.syncSystemTasks(this)
        render()
    }

    private fun render() {
        root.removeAllViews()

        root.addView(TextView(this).apply {
            text = "مدير مهام حكيم"
            textSize = 26f
            gravity = Gravity.RIGHT
            setPadding(8, 4, 8, 8)
        })

        root.addView(TextView(this).apply {
            text = HakimTaskManager.summaryText(this@HakimTaskManagerActivity)
            textSize = 16f
            gravity = Gravity.RIGHT
            setPadding(8, 0, 8, 16)
        })

        val tasks = HakimTaskManager.all(this)
        if (tasks.isEmpty()) {
            root.addView(TextView(this).apply {
                text = "لا توجد مهام محفوظة بعد."
                textSize = 18f
                gravity = Gravity.RIGHT
                setPadding(12, 24, 12, 24)
            })
            return
        }

        for (task in tasks) addTaskCard(task)
    }

    private fun addTaskCard(task: HakimTaskManager.Task) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(18, 16, 18, 16)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(android.graphics.Color.rgb(247, 247, 247))
                cornerRadius = 18f
                setStroke(1, android.graphics.Color.rgb(220, 220, 220))
            }
        }

        card.addView(TextView(this).apply {
            text = "${priorityLabel(task.priority)} · ${stateLabel(task.state)}\n${task.title}"
            textSize = 18f
            gravity = Gravity.RIGHT
        })

        val details = buildString {
            if (task.lastDetail.isNotBlank()) append("الحالة: ").append(task.lastDetail).append('\n')
            if (task.lastEvidence.isNotBlank()) append("آخر دليل: ").append(task.lastEvidence).append('\n')
            if (task.blocker.isNotBlank()) append("المانع: ").append(task.blocker).append('\n')
            if (task.nextAction.isNotBlank()) append("التالي: ").append(task.nextAction)
        }.trim()

        if (details.isNotBlank()) {
            card.addView(TextView(this).apply {
                text = details
                textSize = 15f
                gravity = Gravity.RIGHT
                setPadding(0, 10, 0, 8)
            })
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            gravity = Gravity.RIGHT
        }

        if (task.resumable && task.state !in setOf(
                HakimTaskManager.State.COMPLETE,
                HakimTaskManager.State.CANCELLED
            )
        ) {
            row.addView(Button(this).apply {
                text = if (task.autoResume) "استئناف الآن" else "استئناف"
                setOnClickListener { resumeTask(task) }
            })
        }

        if (task.kind == "user_goal" && task.state !in setOf(
                HakimTaskManager.State.COMPLETE,
                HakimTaskManager.State.CANCELLED
            )
        ) {
            row.addView(Button(this).apply {
                text = "إلغاء"
                setOnClickListener {
                    val current = HakimExecutiveLoop.current(this@HakimTaskManagerActivity)
                    if (current?.id == task.id) {
                        HakimExecutiveLoop.cancel(this@HakimTaskManagerActivity)
                    } else {
                        HakimTaskManager.cancel(this@HakimTaskManagerActivity, task.id)
                    }
                    render()
                }
            })
        }

        if (row.childCount > 0) card.addView(row)

        root.addView(
            card,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 14) }
        )
    }

    private fun resumeTask(task: HakimTaskManager.Task) {
        if (task.kind == "network_protection") {
            HakimTaskManager.syncSystemTasks(this)
            startActivity(Intent(this, HakimRouterAuthActivity::class.java))
            return
        }
        if (task.kind == "device_dns_protection") {
            HakimTaskManager.syncSystemTasks(this)
            if (HakimDeviceProtection.consentGranted(this)) {
                if (!HakimDeviceProtection.enabled(this)) HakimDeviceProtection.markConsentGranted(this)
                HakimDeviceProtection.ensureRunning(this)
                render()
            } else {
                startActivity(Intent(this, HakimFamilyDnsVpnActivity::class.java))
            }
            return
        }
        val requested = HakimTaskManager.requestResume(this, task.id) ?: return
        startActivity(
            Intent(this, CommandCenterActivity::class.java)
                .putExtra("hakim_resume_task_id", requested.id)
        )
        finish()
    }

    private fun stateLabel(state: HakimTaskManager.State): String = when (state) {
        HakimTaskManager.State.QUEUED -> "في الطابور"
        HakimTaskManager.State.RUNNING -> "جارية"
        HakimTaskManager.State.VERIFYING -> "قيد التحقق"
        HakimTaskManager.State.WAITING -> "بانتظار أثر"
        HakimTaskManager.State.BLOCKED -> "متوقفة عند مانع"
        HakimTaskManager.State.PAUSED -> "محفوظة للاستئناف"
        HakimTaskManager.State.COMPLETE -> "مكتملة"
        HakimTaskManager.State.FAILED -> "فشلت دون اعتماد"
        HakimTaskManager.State.CANCELLED -> "ملغاة"
    }

    private fun priorityLabel(priority: String): String = when (priority) {
        "P0" -> "أولوية قصوى"
        "P1" -> "أولوية عالية"
        "P2" -> "أولوية عادية"
        else -> "أولوية"
    }
}
