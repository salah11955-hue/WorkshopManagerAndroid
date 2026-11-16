
package com.khubara.workshop

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.graphics.Typeface
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.widget.*

class MainActivity : Activity() {

    private lateinit var db: WorkshopDb
    private lateinit var root: LinearLayout
    private var activeFormDialog: AlertDialog? = null

    private val navy = Color.rgb(16, 42, 67)
    private val gold = Color.rgb(199, 154, 53)
    private val pageColor = Color.rgb(244, 247, 250)
    private val mutedColor = Color.rgb(115, 125, 135)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = WorkshopDb(this)

        if (db.hasUsers()) showLogin() else showCreateAdmin()
    }

    // إنشاء حساب المدير وتسجيل الدخول

    private fun showCreateAdmin() {
        authScreen("مرحباً بك", "أنشئ حساب المدير للبدء باستخدام نظام إدارة الورشة")

        val name = field("اسم المدير")
        val username = field("اسم المستخدم، مثال admin")
        val password = field("كلمة المرور (6 أحرف على الأقل)", "password")
        val confirm = field("تأكيد كلمة المرور", "password")

        root.addView(name, matchWidth())
        root.addView(username, matchWidth())
        root.addView(password, matchWidth())
        root.addView(confirm, matchWidth())

        addAction("إنشاء حساب المدير", gold) {
            val p = password.text.toString()

            if (name.text.isBlank() || username.text.isBlank()) {
                toast("أدخل اسم المدير واسم المستخدم")
            } else if (p.length < 6) {
                toast("كلمة المرور يجب ألا تقل عن 6 أحرف")
            } else if (p != confirm.text.toString()) {
                toast("كلمتا المرور غير متطابقتين")
            } else {
                val created = db.createAdmin(
                    username.text.toString(),
                    name.text.toString(),
                    p
                )

                if (created) {
                    toast("تم إنشاء حساب المدير بنجاح")
                    showLogin()
                } else {
                    toast("تعذر إنشاء الحساب، تحقق من اسم المستخدم")
                }
            }
        }
    }

    private fun showLogin() {
        authScreen("تسجيل الدخول", "أدخل بيانات حسابك للمتابعة")

        val username = field("اسم المستخدم")
        val password = field("كلمة المرور", "password")

        root.addView(username, matchWidth())
        root.addView(password, matchWidth())

        addAction("دخول إلى النظام", gold) {
            if (db.authenticate(username.text.toString(), password.text.toString())) {
                showDashboard()
            } else {
                toast("اسم المستخدم أو كلمة المرور غير صحيحة")
            }
        }
    }

    private fun authScreen(title: String, subtitle: String) {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(30), dp(24), dp(30))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setBackgroundColor(pageColor)
        }

        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)

        val header = TextView(this).apply {
            text = "خبراء هونداي وفورد"
            textSize = 26f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(32), dp(12), dp(32))
            background = roundedBackground(navy, 18)
        }
        root.addView(header, matchWidth())

        val titleView = TextView(this).apply {
            text = title
            textSize = 22f
            setTextColor(navy)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(25), dp(8), dp(8))
        }
        root.addView(titleView)

        val subtitleView = TextView(this).apply {
            text = subtitle
            textSize = 15f
            setTextColor(mutedColor)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(8), dp(8), dp(20))
        }
        root.addView(subtitleView)
    }

    // لوحة التحكم الرئيسية

    private fun showDashboard() {
        activeFormDialog = null

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(22))
            setBackgroundColor(pageColor)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(25), dp(18), dp(25))
            background = roundedBackground(navy, 18)
        }

        val brand = TextView(this).apply {
            text = "خبراء هونداي وفورد"
            textSize = 25f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        header.addView(brand, matchWidth())

        val headerSubtitle = TextView(this).apply {
            text = "نظام إدارة الورشة والصيانة"
            textSize = 15f
            setTextColor(Color.rgb(225, 230, 235))
            gravity = Gravity.CENTER
            setPadding(0, dp(7), 0, 0)
        }
        header.addView(headerSubtitle, matchWidth())

        val accent = View(this).apply {
            setBackgroundColor(gold)
        }
        header.addView(
            accent,
            LinearLayout.LayoutParams(dp(65), dp(3)).apply {
                gravity = Gravity.CENTER
                topMargin = dp(13)
            }
        )

        root.addView(header, matchWidth())

        val offlineNote = TextView(this).apply {
            text = "●  يعمل محلياً دون الحاجة إلى الإنترنت"
            textSize = 13f
            setTextColor(Color.rgb(55, 115, 85))
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(14), dp(8), dp(14))
        }
        root.addView(offlineNote)

        addSectionTitle("ملخص الورشة")

        val statsRow1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        statsRow1.addView(statCard("العملاء", db.count("customers"), "👥"), weightedParams())
        statsRow1.addView(statCard("السيارات", db.count("vehicles"), "🚘"), weightedParams())
        root.addView(statsRow1)

        val statsRow2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        statsRow2.addView(statCard("أوامر الإصلاح", db.count("work_orders"), "🔧"), weightedParams())
        statsRow2.addView(statCard("قطع المخزون", db.count("parts"), "📦"), weightedParams())
        root.addView(statsRow2)

        addSectionTitle("إدارة الورشة")

        addTileRow(
            "👤", "تسجيل عميل",
            "🚗", "إضافة سيارة",
            { customerForm() },
            { vehicleForm() }
        )

        addTileRow(
            "🔧", "أمر إصلاح",
            "📦", "إضافة قطعة",
            { workOrderForm() },
            { partForm() }
        )

        addSectionTitle("السجلات والحساب")

        addAction("عرض السجلات", navy) {
            recordsDialog()
        }

        addAction("تسجيل الخروج", Color.rgb(150, 45, 45)) {
            showLogin()
        }

        val footer = TextView(this).apply {
            text = "خبراء هونداي وفورد  •  الإصدار التأسيسي"
            textSize = 12f
            setTextColor(mutedColor)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(22), dp(8), dp(8))
        }
        root.addView(footer)
    }

    private fun addSectionTitle(title: String) {
        val view = TextView(this).apply {
            text = title
            textSize = 18f
            setTextColor(navy)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(3), dp(17), dp(3), dp(9))
        }
        root.addView(view, matchWidth())
    }

    private fun statCard(label: String, count: Int, symbol: String): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(15), dp(8), dp(15))
            background = roundedBackground(Color.WHITE, 14)
            elevation = dp(2).toFloat()
        }

        val icon = TextView(this).apply {
            text = symbol
            textSize = 25f
            gravity = Gravity.CENTER
        }
        card.addView(icon, matchWidth())

        val number = TextView(this).apply {
            text = count.toString()
            textSize = 25f
            setTextColor(navy)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, dp(5), 0, dp(3))
        }
        card.addView(number, matchWidth())

        val caption = TextView(this).apply {
            text = label
            textSize = 13f
            setTextColor(mutedColor)
            gravity = Gravity.CENTER
        }
        card.addView(caption, matchWidth())

        return card
    }

    private fun addTileRow(
        icon1: String,
        label1: String,
        icon2: String,
        label2: String,
        action1: () -> Unit,
        action2: () -> Unit
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        row.addView(dashboardTile(icon1, label1, action1), weightedParams())
        row.addView(dashboardTile(icon2, label2, action2), weightedParams())

        root.addView(row)
    }

    private fun dashboardTile(
        symbol: String,
        label: String,
        action: () -> Unit
    ): View {
        val tile = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(19), dp(8), dp(19))
            background = roundedBackground(Color.WHITE, 14)
            elevation = dp(2).toFloat()
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }

        val icon = TextView(this).apply {
            text = symbol
            textSize = 28f
            gravity = Gravity.CENTER
        }
        tile.addView(icon, matchWidth())

        val title = TextView(this).apply {
            text = label
            textSize = 15f
            setTextColor(navy)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, 0)
        }
        tile.addView(title, matchWidth())

        return tile
    }

    // نماذج إدخال البيانات

    private fun customerForm() {
        val name = field("اسم العميل")
        val phone = field("رقم الهاتف الأساسي (إلزامي)", "phone")
        val alt = field("أرقام بديلة، افصل بينها بفاصلة")
        val address = field("العنوان")

        formDialog("عميل جديد", listOf(name, phone, alt, address)) { dialog ->
            if (name.text.isBlank() || phone.text.isBlank()) {
                return@formDialog toast("أدخل الاسم ورقم الهاتف")
            }

            try {
                db.addCustomer(
                    name.text.toString(),
                    phone.text.toString(),
                    alt.text.toString(),
                    address.text.toString()
                )
                done(dialog)
            } catch (_: Exception) {
                toast("رقم الهاتف مسجل مسبقاً أو توجد بيانات غير صحيحة")
            }
        }
    }

    private fun vehicleForm() {
        val customers = db.customerOptions()
        if (customers.isEmpty()) return toast("سجّل العميل أولاً")

        val owner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                customers.map { it.second }
            )
        }

        val make = field("الشركة، مثال Hyundai")
        val model = field("الموديل")
        val year = field("سنة الصنع")
        val engine = field("المحرك")
        val plate = field("رقم اللوحة")
        val vin = field("رقم الهيكل VIN (إلزامي)")
        val odo = field("عداد الكيلومترات الحالي")

        formDialog(
            "إضافة سيارة",
            listOf(owner, make, model, year, engine, plate, vin, odo)
        ) { dialog ->
            if (vin.text.isBlank()) {
                return@formDialog toast("رقم الهيكل مطلوب")
            }

            try {
                db.addVehicle(
                    customers[owner.selectedItemPosition].first,
                    make.text.toString(),
                    model.text.toString(),
                    year.text.toString(),
                    engine.text.toString(),
                    plate.text.toString(),
                    vin.text.toString(),
                    odo.text.toString()
                )
                done(dialog)
            } catch (_: Exception) {
                toast("رقم الهيكل مسجل مسبقاً، راجع ملف المالك الحالي")
            }
        }
    }

    private fun workOrderForm() {
        val vehicles = db.vehicleOptions()
        if (vehicles.isEmpty()) return toast("سجّل سيارة أولاً")

        val vehicle = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                vehicles.map { it.third }
            )
        }

        val odo = field("قراءة العداد عند الزيارة")
        val complaint = field("شكوى العميل")
        val diagnosis = field("التشخيص الفني الأولي")

        formDialog(
            "أمر إصلاح جديد",
            listOf(vehicle, odo, complaint, diagnosis)
        ) { dialog ->
            try {
                val selected = vehicles[vehicle.selectedItemPosition]

                db.addWorkOrder(
                    selected.second,
                    selected.first,
                    odo.text.toString(),
                    complaint.text.toString(),
                    diagnosis.text.toString()
                )
                done(dialog)
            } catch (_: Exception) {
                toast("تعذر حفظ أمر الإصلاح")
            }
        }
    }

    private fun partForm() {
        val number = field("رقم القطعة")
        val name = field("اسم القطعة (إلزامي)")
        val qty = field("الكمية")
        val unit = field("الوحدة، مثال قطعة")
        val buy = field("سعر الشراء")
        val sell = field("سعر البيع")

        formDialog(
            "إضافة قطعة للمخزون",
            listOf(number, name, qty, unit, buy, sell)
        ) { dialog ->
            if (name.text.isBlank()) {
                return@formDialog toast("اسم القطعة مطلوب")
            }

            try {
                db.addPart(
                    number.text.toString(),
                    name.text.toString(),
                    qty.text.toString(),
                    unit.text.toString().ifBlank { "قطعة" },
                    buy.text.toString(),
                    sell.text.toString()
                )
                done(dialog)
            } catch (_: Exception) {
                toast("تعذر حفظ القطعة")
            }
        }
    }

    // عرض السجلات

    private fun recordsDialog() {
        val labels = arrayOf("العملاء", "السيارات", "أوامر الإصلاح", "المخزون")
        val tables = arrayOf("customers", "vehicles", "work_orders", "parts")

        AlertDialog.Builder(this)
            .setTitle("سجلات الورشة")
            .setItems(labels) { _, which ->
                val rows = db.recentRows(tables[which])

                val message = if (rows.isEmpty()) {
                    "لا توجد سجلات بعد"
                } else {
                    rows.joinToString("\n\n────────────\n\n")
                }

                AlertDialog.Builder(this)
                    .setTitle(labels[which])
                    .setMessage(message)
                    .setPositiveButton("إغلاق", null)
                    .show()
            }
            .setNegativeButton("إغلاق", null)
            .show()
    }

    // نافذة الإدخال والحفظ

    private fun formDialog(
        title: String,
        views: List<View>,
        save: (AlertDialog) -> Unit
    ) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(5), dp(20), dp(5))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        val scroller = ScrollView(this)
        views.forEach { view ->
            box.addView(view, matchWidth())
        }
        scroller.addView(box)

        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(scroller)
            .setPositiveButton("حفظ", null)
            .setNegativeButton("إلغاء", null)
            .create()

        activeFormDialog = dialog

        dialog.setOnDismissListener {
            if (activeFormDialog === dialog) {
                activeFormDialog = null
            }
        }

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                save(dialog)
            }
        }

        dialog.show()
    }

    // تغلق النافذة بعد نجاح الحفظ فقط.
    // عند فشل التحقق أو الحفظ تبقى النافذة مفتوحة.

    private fun done(dialog: AlertDialog) {
        dialog.dismiss()

        if (activeFormDialog === dialog) {
            activeFormDialog = null
        }

        toast("تم الحفظ بنجاح")
        showDashboard()
    }

    // عناصر مساعدة

    private fun field(hintText: String, type: String = "text"): EditText =
        EditText(this).apply {
            hint = hintText
            textSize = 15f
            singleLine = type == "phone" || hintText.contains("السنة") || hintText.contains("العداد")

            if (type == "phone") {
                inputType = InputType.TYPE_CLASS_PHONE
            }

            if (type == "password") {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }

            setPadding(dp(12), dp(8), dp(12), dp(8))
        }

    private fun addAction(label: String, color: Int, action: () -> Unit) {
        val button = Button(this).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 16f
            isAllCaps = false
            background = roundedBackground(color, 12)
            setPadding(dp(10), dp(7), dp(10), dp(7))
            setOnClickListener { action() }
        }

        root.addView(
            button,
            matchWidth().apply {
                setMargins(0, dp(6), 0, dp(6))
            }
        )
    }

    private fun roundedBackground(color: Int, radius: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }
    }

    private fun weightedParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f
        ).apply {
            setMargins(dp(5), dp(5), dp(5), dp(5))
        }
    }

    private fun matchWidth() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
