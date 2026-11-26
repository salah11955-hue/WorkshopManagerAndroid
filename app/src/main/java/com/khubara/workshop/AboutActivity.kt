package com.khubara.workshop

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.graphics.Color
import android.graphics.Typeface

/** صفحة تعريف عربية مختصرة بالتطبيق، يمكن فتحها عبر workshopmanager://about. */
class AboutActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "غير معروف"
        } catch (_: Exception) {
            "غير معروف"
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(48, 48, 48, 48)
            setBackgroundColor(Color.rgb(247, 249, 252))
        }

        fun addSection(title: String, body: String) {
            content.addView(TextView(this).apply {
                text = title
                textSize = 19f
                setTextColor(Color.rgb(15, 23, 42))
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.RIGHT
                setPadding(0, 24, 0, 8)
            })
            content.addView(TextView(this).apply {
                text = body
                textSize = 15f
                setTextColor(Color.rgb(51, 65, 85))
                gravity = Gravity.RIGHT
                setLineSpacing(6f, 1.15f)
            })
        }

        addSection("خبراء هونداي وفورد", "تطبيق عربي لإدارة أعمال ورشة السيارات.")
        addSection("الإصدار", version)
        addSection("عن التطبيق", "يساعد التطبيق على تنظيم العملاء والمركبات وأوامر الإصلاح والدفعات والمخزون والفواتير والصور. تُحفظ البيانات محلياً على الجهاز باستخدام قاعدة بيانات SQLite، ويعمل التطبيق دون الحاجة إلى اتصال دائم بالإنترنت.")
        addSection("اللغة والاتجاه", "واجهة التطبيق عربية بالكامل ومصممة للقراءة من اليمين إلى اليسار (RTL). لا يتوفر اختيار لغة أخرى حالياً.")
        addSection("الخصوصية", "بيانات الورشة محفوظة محلياً في مساحة التطبيق. احرص على إنشاء نسخة احتياطية آمنة، ولا تشاركها إلا مع أشخاص موثوقين. النسخة الاحتياطية الحالية ليست مشفرة.")
        addSection("ملاحظة", "بعض الأفكار المذكورة في وثائق المشروع خارطة طريق مستقبلية وليست وظائف متاحة في الإصدار الحالي.")

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(content)
        }
        setContentView(scroll)
        title = "حول التطبيق"
    }
}
