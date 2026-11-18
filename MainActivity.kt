
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
import android.text.TextWatcher
import android.text.Editable
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

    // تسجيل الدخول وإنشاء المدير

    private fun showCreateAdmin() {
        authScreen(
            "مرحباً بك",
            "أنشئ حساب المدير للبدء باستخدام نظام إدارة الورشة"
        )

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
            if (db.authenticate(
                    username.text.toString(),
                    password.text.toString()
                )
            ) {
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

    // لوحة التحكم

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

        val subtitle = TextView(this).apply {
            text = "نظام إدارة الورشة والصيانة"
            textSize = 15f
            setTextColor(Color.rgb(225, 230, 235))
            gravity = Gravity.CENTER
            setPadding(0, dp(7), 0, 0)
        }
        header.addView(subtitle, matchWidth())

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

        addSectionTitle("ملف السيارة")

        addAction("🚘  البحث عن سيارة وفتح ملفها", navy) {
            vehicleSearchDialog()
        }

        addSectionTitle("ملخص الورشة")

        val statsRow1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        statsRow1.addView(
            statCard("العملاء", db.count("customers"), "👥", "customers"),
            weightedParams()
        )
        statsRow1.addView(
            statCard("السيارات", db.count("vehicles"), "🚘", "vehicles"),
            weightedParams()
        )
        root.addView(statsRow1)

        val statsRow2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        statsRow2.addView(
            statCard("أوامر الإصلاح", db.count("work_orders"), "🔧", "work_orders"),
            weightedParams()
        )
        statsRow2.addView(
            statCard("قطع المخزون", db.count("parts"), "📦", "parts"),
            weightedParams()
        )
        root.addView(statsRow2)

        addSectionTitle("السجلات والحساب")

        addAction("عرض السجلات", navy) {
            recordsDialog()
        }

        addAction("تسجيل الخروج", Color.rgb(150, 45, 45)) {
            showLogin()
        }

        val footer = TextView(this).apply {
            text = "خبراء هونداي وفورد  •  الإصدار 0.5.0"
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

    private fun statCard(
        label: String,
        count: Int,
        symbol: String,
        table: String
    ): View {
        val card = LinearLayout(this).apply {
            isClickable = true
            isFocusable = true
            setOnClickListener {
                showRecords(label, table)
            }
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

    // العملاء

    private fun duplicateWarning(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("حسناً", null)
            .show()
    }

    private fun customerForm() {
        val name = field("اسم العميل", suggestionKey = "customer_name")
        val phone = field(
            "رقم الهاتف الأساسي (إلزامي)",
            "phone",
            "customer_phone"
        )
        val alt = field("أرقام بديلة، افصل بينها بفاصلة")
        val address = field("العنوان")

        formDialog("عميل جديد", listOf(name, phone, alt, address)) { dialog ->

            if (name.text.isBlank() || phone.text.isBlank()) {
                return@formDialog toast("أدخل الاسم ورقم الهاتف")
            }

            val existing = db.findCustomerByPhone(phone.text.toString())

            if (existing != null) {
                return@formDialog duplicateWarning(
                    "العميل مسجل مسبقاً",
                    "رقم الهاتف الذي أدخلته مسجل باسم:\n\n$existing\n\nراجع سجل العميل قبل إضافة سجل جديد."
                )
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
                duplicateWarning(
                    "تعذر الحفظ",
                    "تعذر إضافة العميل. تحقق من رقم الهاتف والبيانات المدخلة."
                )
            }
        }
    }

    // إضافة سيارة مع التحقق من رقم الهيكل

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
        val plate = field("رقم اللوحة", suggestionKey = "plate")
        val vin = field("رقم الهيكل VIN (17 خانة)", suggestionKey = "vin")
        val odo = field("عداد الكيلومترات الحالي")

        formDialog(
            "إضافة سيارة",
            listOf(owner, make, model, year, engine, plate, vin, odo)
        ) { dialog ->

            val cleanVin = vin.text.toString().trim().uppercase()

            if (cleanVin.length != 17) {
                return@formDialog toast(
                    "رقم الهيكل يجب أن يتكون من 17 خانة بالضبط"
                )
            }

            if (!cleanVin.matches(Regex("[A-HJ-NPR-Z0-9]{17}"))) {
                return@formDialog toast(
                    "رقم الهيكل يجب أن يحتوي على أحرف وأرقام إنجليزية صحيحة، دون I أو O أو Q أو رموز"
                )
            }

            val existingVin = db.findVehicleByVin(cleanVin)

            if (existingVin != null) {
                return@formDialog duplicateWarning(
                    "السيارة مسجلة مسبقاً",
                    "رقم الهيكل هذا موجود في سجل سيارة سابقة:\n\n$existingVin\n\nلا حاجة لإضافة السيارة مرة أخرى."
                )
            }

            val existingPlate = db.findVehicleByPlate(plate.text.toString())

            if (existingPlate != null) {
                return@formDialog duplicateWarning(
                    "رقم اللوحة مسجل مسبقاً",
                    "هذه اللوحة مرتبطة بالسيارة التالية:\n\n$existingPlate\n\nراجع السجل قبل المتابعة."
                )
            }

            try {
                db.addVehicle(
                    customers[owner.selectedItemPosition].first,
                    make.text.toString(),
                    model.text.toString(),
                    year.text.toString(),
                    engine.text.toString(),
                    plate.text.toString(),
                    cleanVin,
                    odo.text.toString()
                )
                done(dialog)
            } catch (e: Exception) {
                duplicateWarning(
                    "تعذر حفظ السيارة",
                    e.message ?: "تحقق من البيانات المدخلة ورقم الهيكل."
                )
            }
        }
    }

    // أوامر الإصلاح العامة

    
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

    val odo = field("قراءة العداد عند الزيارة", type = "number")
    val complaint = field("شكوى العميل")
    val diagnosis = field("التشخيص الفني الأولي")
    val repair = field("تفاصيل الإصلاح المنفذ")

    val statuses = arrayOf("مفتوح", "قيد العمل", "مكتمل")

    val status = Spinner(this).apply {
        adapter = ArrayAdapter(
            this@MainActivity,
            android.R.layout.simple_spinner_dropdown_item,
            statuses
        )
    }

    val labor = field("أجور العمل", type = "decimal")
    val parts = field("تكلفة قطع الغيار", type = "decimal")

    formDialog(
        "أمر إصلاح جديد",
        listOf(
            vehicle, odo, complaint, diagnosis,
            repair, status, labor, parts
        )
    ) { dialog ->
        try {
            val selected = vehicles[vehicle.selectedItemPosition]

            db.addWorkOrder(
                selected.second,
                selected.first,
                odo.text.toString(),
                complaint.text.toString(),
                diagnosis.text.toString(),
                repair.text.toString(),
                status.selectedItem.toString(),
                labor.text.toString(),
                parts.text.toString()
            )

            done(dialog)
        } catch (_: Exception) {
            toast("تعذر الحفظ، تحقق من قراءة العداد والتكاليف")
        }
    }
}


    // إضافة قطعة

    private fun partForm() {
        val number = field("رقم القطعة", suggestionKey = "part_number")
        val name = field("اسم القطعة (إلزامي)", suggestionKey = "part_name")
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

            if (number.text.isNotBlank()) {
                val existingPart = db.findPartByNumber(number.text.toString())

                if (existingPart != null) {
                    return@formDialog duplicateWarning(
                        "رقم القطعة موجود مسبقاً",
                        "رقم القطعة الذي أدخلته مسجل باسم:\n\n$existingPart\n\nراجع سجل المخزون قبل إضافة قطعة أخرى."
                    )
                }
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
                duplicateWarning(
                    "تعذر الحفظ",
                    "تعذر إضافة القطعة. تحقق من البيانات المدخلة."
                )
            }
        }
    }

    // البحث عن السيارات

    private fun vehicleSearchDialog() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(8))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        val query = field("ابحث برقم الهيكل أو اللوحة أو الشركة أو الموديل أو اسم العميل")
        box.addView(query, matchWidth())

        val resultsBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        box.addView(resultsBox, matchWidth())

        val scroll = ScrollView(this)
        scroll.addView(box)

        val dialog = AlertDialog.Builder(this)
            .setTitle("البحث عن سيارة")
            .setView(scroll)
            .setNegativeButton("إغلاق", null)
            .create()

        
fun refreshResults() {
    resultsBox.removeAllViews()

    val searchText = query.text.toString()
    if (searchText.isBlank()) return

    val results = db.searchVehicles(searchText)

    if (results.isEmpty()) {
        val empty = TextView(this).apply {
            text = "لا توجد سيارات مطابقة للبحث"
            textSize = 14f
            setTextColor(mutedColor)
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(18), dp(10), dp(18))
        }
        resultsBox.addView(empty)
        return
    }

    results.forEach { vehicle ->
        val item = TextView(this).apply {
            text = "${vehicle.make} ${vehicle.model} ${vehicle.year}\n" +
                "اللوحة: ${vehicle.plate.ifBlank { "غير مسجلة" }}\n" +
                "VIN: ${vehicle.vin}\n" +
                "المالك: ${vehicle.ownerName}"
            textSize = 14f
            setTextColor(navy)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = roundedBackground(Color.WHITE, 12)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                dialog.dismiss()
                showVehicleProfile(vehicle.id)
            }
        }

        val params = matchWidth().apply {
            setMargins(0, dp(5), 0, dp(5))
        }
        resultsBox.addView(item, params)
    }
}


                
        query.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence?, start: Int, count: Int, after: Int
            ) {}

            override fun onTextChanged(
                s: CharSequence?, start: Int, before: Int, count: Int
            ) {
                refreshResults()
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        dialog.show()
    }

    // ملف السيارة

    private fun showVehicleProfile(vehicleId: Long) {
        val vehicle = db.getVehicleProfile(vehicleId)
        if (vehicle == null) {
            toast("لم يتم العثور على السيارة")
            return
        }

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(8))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        val title = TextView(this).apply {
            text = "${vehicle.make} ${vehicle.model} ${vehicle.year}"
            textSize = 21f
            setTextColor(navy)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(12), dp(8), dp(12))
        }
        box.addView(title, matchWidth())

        addInfo(box, "المالك الحالي", vehicle.ownerName)
        addInfo(box, "رقم هاتف المالك", vehicle.ownerPhone)
        addInfo(box, "رقم اللوحة", vehicle.plate)
        addInfo(box, "رقم الهيكل VIN", vehicle.vin)
        addInfo(box, "المحرك", vehicle.engine)
        addInfo(box, "سنة الصنع", vehicle.year)
        addInfo(box, "عداد الكيلومترات", vehicle.odometer.toString())
        addInfo(box, "ملاحظات", vehicle.notes)

        val scroll = ScrollView(this)
        scroll.addView(box)

        val dialog = AlertDialog.Builder(this)
            .setTitle("ملف السيارة")
            .setView(scroll)
            .setPositiveButton("إغلاق", null)
            .setNeutralButton("سجل الملاك", null)
            .setNegativeButton("سجل الزيارات", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                .setOnClickListener {
                    showOwnerHistory(vehicle.id)
                }

            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                .setOnClickListener {
                    showVehicleVisits(vehicle.id)
                }
        }

        dialog.show()

        // أزرار العمليات الإضافية في ملف السيارة
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(5), dp(18), dp(12))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        val actionDialog = AlertDialog.Builder(this)
            .setTitle("عمليات السيارة")
            .setView(actions)
            .setNegativeButton("إغلاق", null)
            .create()

        addDialogButton(actions, "تسجيل زيارة جديدة", navy) {
            actionDialog.dismiss()
            newVehicleVisit(vehicle.id)
        }

        addDialogButton(actions, "نقل ملكية السيارة", gold) {
            actionDialog.dismiss()
            transferOwnership(vehicle.id)
        }

        addDialogButton(actions, "عرض ملف السيارة", mutedColor) {
            actionDialog.dismiss()
            showVehicleProfile(vehicle.id)
        }

        // فتح العمليات عند الضغط المطول على عنوان الملف
        title.setOnLongClickListener {
            actionDialog.show()
            true
        }

        // زر واضح لفتح قائمة العمليات
        val operationsButton = Button(this).apply {
            text = "عمليات السيارة: زيارة جديدة / نقل ملكية"
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = roundedBackground(navy, 12)
            setOnClickListener {
                actionDialog.show()
            }
        }
        box.addView(operationsButton, matchWidth())
    }

    private fun addInfo(box: LinearLayout, label: String, value: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = roundedBackground(Color.WHITE, 10)
        }

        val heading = TextView(this).apply {
            text = label
            textSize = 12f
            setTextColor(mutedColor)
        }
        row.addView(heading, matchWidth())

        val content = TextView(this).apply {
            text = value.ifBlank { "غير مسجل" }
            textSize = 15f
            setTextColor(navy)
            setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(3), 0, 0)
        }
        row.addView(content, matchWidth())

        box.addView(
            row,
            matchWidth().apply {
                setMargins(0, dp(3), 0, dp(3))
            }
        )
    }

    // سجل الملاك

    private fun showOwnerHistory(vehicleId: Long) {
        val records = db.getVehicleOwnerHistory(vehicleId)

        val message = if (records.isEmpty()) {
            "لا يوجد سجل نقل ملكية لهذه السيارة.\n\nالمالك الحالي يظهر في ملف السيارة."
        } else {
            records.joinToString("\n\n────────────\n\n") {
                "من: ${it.oldOwner}\n" +
                    "إلى: ${it.newOwner}\n" +
                    "السبب: ${it.reason}\n" +
                    "التاريخ: ${it.changedAt}"
            }
        }

        AlertDialog.Builder(this)
            .setTitle("سجل ملاك السيارة")
            .setMessage(message)
            .setPositiveButton("إغلاق", null)
            .show()
    }

    // سجل زيارات السيارة

    private fun showVehicleVisits(vehicleId: Long) {
        val records = db.getVehicleWorkOrders(vehicleId)

        val message = if (records.isEmpty()) {
            "لا توجد زيارات مسجلة لهذه السيارة."
        } else {
            records.joinToString("\n\n────────────\n\n") {
                "أمر إصلاح رقم: ${it.id}\n" +
                    "العميل وقت الزيارة: ${it.customerName}\n" +
                    "التاريخ: ${it.createdAt}\n" +
                    "العداد: ${it.odometer}\n" +
                    "الشكوى: ${it.complaint}\n" +
                    "التشخيص: ${it.diagnosis}\n" +
                    "الإصلاح: ${it.repairNotes}\n" +
                    "الحالة: ${it.status}\n" +
                    "أجور العمل: ${it.laborCost}\n" +
                    "تكلفة القطع: ${it.partsCost}"
            }
        }

        AlertDialog.Builder(this)
            .setTitle("سجل زيارات السيارة")
            .setMessage(message)
            .setPositiveButton("إغلاق", null)
            .show()
    }

    // تسجيل زيارة من ملف السيارة مباشرة

    
private fun newVehicleVisit(vehicleId: Long) {
    val vehicle = db.getVehicleProfile(vehicleId) ?: return

    val odo = field("قراءة العداد عند الزيارة", type = "number")
    odo.setText(vehicle.odometer.toString())

    val complaint = field("شكوى العميل")
    val diagnosis = field("التشخيص الفني الأولي")
    val repair = field("تفاصيل الإصلاح المنفذ")

    val statuses = arrayOf("مفتوح", "قيد العمل", "مكتمل")

    val status = Spinner(this).apply {
        adapter = ArrayAdapter(
            this@MainActivity,
            android.R.layout.simple_spinner_dropdown_item,
            statuses
        )
    }

    val labor = field("أجور العمل", type = "decimal")
    val parts = field("تكلفة قطع الغيار", type = "decimal")

    formDialog(
        "زيارة جديدة — ${vehicle.make} ${vehicle.model}",
        listOf(odo, complaint, diagnosis, repair, status, labor, parts)
    ) { dialog ->
        try {
            db.addWorkOrder(
                vehicle.customerId,
                vehicle.id,
                odo.text.toString(),
                complaint.text.toString(),
                diagnosis.text.toString(),
                repair.text.toString(),
                status.selectedItem.toString(),
                labor.text.toString(),
                parts.text.toString()
            )

            dialog.dismiss()
            activeFormDialog = null
            toast("تم تسجيل الزيارة بنجاح")
            showVehicleProfile(vehicle.id)
        } catch (_: Exception) {
            toast("تعذر الحفظ، تحقق من قراءة العداد والتكاليف")
        }
    }
}


    // نقل الملكية مع حفظ المالك السابق

    private fun transferOwnership(vehicleId: Long) {
        val vehicle = db.getVehicleProfile(vehicleId) ?: return
        val customers = db.customerOptions()

        if (customers.isEmpty()) {
            return toast("لا يوجد عملاء مسجلون")
        }

        val owner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                customers.map { it.second }
            )
        }

        val reason = field("سبب نقل الملكية، اختياري")

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(8))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        val current = TextView(this).apply {
            text = "المالك الحالي: ${vehicle.ownerName}"
            textSize = 15f
            setTextColor(navy)
            setPadding(0, 0, 0, dp(12))
        }

        box.addView(current, matchWidth())
        box.addView(owner, matchWidth())
        box.addView(reason, matchWidth())

        val dialog = AlertDialog.Builder(this)
            .setTitle("نقل ملكية السيارة")
            .setView(box)
            .setPositiveButton("تأكيد النقل", null)
            .setNegativeButton("إلغاء", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val selected = customers[owner.selectedItemPosition]

                if (selected.first == vehicle.customerId) {
                    toast("هذا العميل هو المالك الحالي بالفعل")
                    return@setOnClickListener
                }

                val changed = db.transferVehicleOwnership(
                    vehicle.id,
                    selected.first,
                    reason.text.toString()
                )

                if (changed) {
                    dialog.dismiss()
                    toast("تم نقل الملكية وحفظ سجل المالك السابق")
                    showVehicleProfile(vehicle.id)
                } else {
                    toast("تعذر نقل الملكية، لم يتم تغيير البيانات")
                }
            }
        }

        dialog.show()
    }

    private fun addDialogButton(
        box: LinearLayout,
        label: String,
        color: Int,
        action: () -> Unit
    ) {
        val button = Button(this).apply {
            text = label
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = roundedBackground(color, 12)
            setOnClickListener { action() }
        }

        box.addView(
            button,
            matchWidth().apply {
                setMargins(0, dp(5), 0, dp(5))
            }
        )
    }

    // السجلات العامة

    private fun recordsDialog() {
        val labels = arrayOf("العملاء", "السيارات", "أوامر الإصلاح", "المخزون")
        val tables = arrayOf("customers", "vehicles", "work_orders", "parts")

        AlertDialog.Builder(this)
            .setTitle("سجلات الورشة")
            .setItems(labels) { _, which ->
                showRecords(labels[which], tables[which])
            }
            .setNegativeButton("إغلاق", null)
            .show()
    }

    
fun showRecords(label: String, table: String) {
    if (table != "vehicles" && table != "work_orders") {
        val rows = db.recentRows(table)

        val message = if (rows.isEmpty()) {
            "لا توجد سجلات بعد"
        } else {
            rows.joinToString("\n\n────────────\n\n")
        }

        AlertDialog.Builder(this)
            .setTitle(label)
            .setMessage(message)
            .setPositiveButton("إغلاق", null)
            .show()
        return
    }

    val records = db.recentClickableRecords(table)

    if (records.isEmpty()) {
        AlertDialog.Builder(this)
            .setTitle(label)
            .setMessage("لا توجد سجلات بعد")
            .setPositiveButton("إغلاق", null)
            .show()
        return
    }

    val listView = ListView(this).apply {
        layoutDirection = View.LAYOUT_DIRECTION_RTL
        adapter = object : android.widget.BaseAdapter() {
            override fun getCount() = records.size

            override fun getItem(position: Int) = records[position]

            override fun getItemId(position: Int) =
                records[position].id

            override fun getView(
                position: Int,
                convertView: View?,
                parent: android.view.ViewGroup?
            ): View {
                val item = records[position]

                val container = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(
                        dp(16), dp(12),
                        dp(16), dp(12)
                    )
                    layoutDirection = View.LAYOUT_DIRECTION_RTL
                }

                val titleView = TextView(this@MainActivity).apply {
                    text = item.title
                    textSize = 16f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                }

                val subtitleView = TextView(this@MainActivity).apply {
                    text = item.subtitle
                    textSize = 13f
                    setPadding(0, dp(5), 0, 0)
                }

                container.addView(titleView)
                container.addView(subtitleView)

                return container
            }
        }

        setOnItemClickListener { _, _, position, _ ->
            val item = records[position]
            val vehicleId = if (table == "vehicles") {
                item.id
            } else {
                item.vehicleId
            }

            if (vehicleId > 0L) {
                showVehicleProfile(vehicleId)
            } else {
                toast("لم يتم العثور على السيارة المرتبطة بهذا الأمر")
            }
        }
    }

    AlertDialog.Builder(this)
        .setTitle(label)
        .setView(listView)
        .setPositiveButton("إغلاق", null)
        .show()
}


    // نافذة الإدخال

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

        views.forEach { view ->
            box.addView(view, matchWidth())
        }

        val scroller = ScrollView(this)
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
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener {
                    save(dialog)
                }
        }

        dialog.show()
    }

    private fun done(dialog: AlertDialog) {
        dialog.dismiss()

        if (activeFormDialog === dialog) {
            activeFormDialog = null
        }

        toast("تم الحفظ بنجاح")
        showDashboard()
    }

    // حقول الإدخال والاقتراحات

    private fun field(
        hintText: String,
        type: String = "text",
        suggestionKey: String? = null
    ): AutoCompleteTextView {

        val input = AutoCompleteTextView(this).apply {
            hint = hintText
            textSize = 15f

            
setSingleLine(
    type == "phone" ||
        type == "number" ||
        type == "decimal" ||
        hintText.contains("السنة") ||
        hintText.contains("العداد")
)

if (type == "phone") {
    inputType = InputType.TYPE_CLASS_PHONE
}

if (type == "number") {
    inputType = InputType.TYPE_CLASS_NUMBER
}

if (type == "decimal") {
    inputType = InputType.TYPE_CLASS_NUMBER or
        InputType.TYPE_NUMBER_FLAG_DECIMAL
}

if (type == "password") {
    inputType = InputType.TYPE_CLASS_TEXT or
        InputType.TYPE_TEXT_VARIATION_PASSWORD
}

            setPadding(dp(12), dp(8), dp(12), dp(8))
            threshold = 1
        }

        if (suggestionKey != null) {
            val adapter = ArrayAdapter<String>(
                this,
                android.R.layout.simple_dropdown_item_1line,
                mutableListOf()
            )

            input.setAdapter(adapter)

            input.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {}

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int
                ) {
                    val prefix = s?.toString().orEmpty()

                    if (prefix.isBlank()) {
                        adapter.clear()
                        input.dismissDropDown()
                        return
                    }

                    val results = db.suggestions(suggestionKey, prefix)

                    adapter.clear()
                    adapter.addAll(results)
                    adapter.notifyDataSetChanged()

                    if (results.isNotEmpty() && input.hasFocus()) {
                        input.showDropDown()
                    } else {
                        input.dismissDropDown()
                    }
                }

                override fun afterTextChanged(s: Editable?) {}
            })
        }

        return input
    }

    // عناصر التصميم

    private fun addAction(
        label: String,
        color: Int,
        action: () -> Unit
    ) {
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

    private fun roundedBackground(
        color: Int,
        radius: Int
    ): GradientDrawable {
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
