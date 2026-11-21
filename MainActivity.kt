
package com.khubara.workshop

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.app.AlertDialog
import android.os.Bundle
import android.graphics.Color
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class MainActivity : Activity() {

    private lateinit var db: WorkshopDb
    private lateinit var root: LinearLayout
    private lateinit var pageRoot: LinearLayout
    private var loggedUser: AuthUser? = null
    private var activeFormDialog: AlertDialog? = null
    private var pendingAttachmentVehicleId: Long? = null
    private var pendingAttachmentCategory: String = "general"
    private val attachmentRequestCode = 7412

    // هوية خبراء هونداي وفورد: كحلي عميق، فضي، أبيض ولمسات ذهبية
    private val blue = Color.rgb(17, 35, 61)
    private val blueMid = Color.rgb(28, 55, 88)
    private val gold = Color.rgb(198, 157, 72)
    private val silver = Color.rgb(222, 228, 236)
    private val light = Color.rgb(242, 245, 249)
    private val dark = Color.rgb(31, 41, 55)
    private val muted = Color.rgb(105, 117, 132)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = WorkshopDb(this)

        if (db.hasUsers()) showLogin() else showCreateAdmin()
    }

    override fun onDestroy() {
        super.onDestroy()
        db.close()
    }

    // ---------------------------------------------------------
    // أساسيات الواجهة
    // ---------------------------------------------------------

    private fun page(title: String, subtitle: String = "") {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            setBackgroundColor(light)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        val heading = TextView(this).apply {
            text = title
            textSize = 23f
            setTextColor(blue)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(12))
        }
        root.addView(heading, matchWidth())

        if (subtitle.isNotBlank()) {
            val sub = TextView(this).apply {
                text = subtitle
                textSize = 14f
                setTextColor(Color.DKGRAY)
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, dp(12))
            }
            root.addView(sub, matchWidth())
        }

        pageRoot = root
        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)
    }

    private fun addText(
        text: String,
        size: Float = 15f,
        color: Int = dark,
        bold: Boolean = false
    ) {
        val view = TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(5), dp(7), dp(5), dp(7))
        }
        root.addView(view, matchWidth())
    }
private fun addAction(
    label: String,
    action: () -> Unit,
    color: Int = blue
) {
    val button = Button(this).apply {
        text = label
        textSize = 15f
        isAllCaps = false
        setTextColor(Color.WHITE)
        background = rounded(color)
        setPadding(dp(10), dp(8), dp(10), dp(8))
        setOnClickListener { action() }
    }

    root.addView(
        button,
        matchWidth().apply {
            setMargins(0, dp(5), 0, dp(5))
        }
    )
}
private fun addAction(
    label: String,
    color: Int = blue,
    action: () -> Unit
) {
    addAction(label, action, color)
}
    private fun rounded(color: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(12).toFloat()
    }

    private fun field(
        hint: String,
        type: String = "text",
        suggestionKeys: List<String> = emptyList()
    ): AutoCompleteTextView {
        return AutoCompleteTextView(this).apply {
            this.hint = hint
            textSize = 15f
            setTextColor(dark)
            setHintTextColor(Color.GRAY)
            setSingleLine(type != "multiline")
            inputType = when (type) {
                "phone" -> InputType.TYPE_CLASS_PHONE
                "number" -> InputType.TYPE_CLASS_NUMBER
                "decimal" -> InputType.TYPE_CLASS_NUMBER or
                    InputType.TYPE_NUMBER_FLAG_DECIMAL
                "password" -> InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_VARIATION_PASSWORD
                "multiline" -> InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_MULTI_LINE
                else -> InputType.TYPE_CLASS_TEXT
            }
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(10).toFloat()
                setStroke(dp(1), Color.rgb(220, 225, 232))
            }

            if (suggestionKeys.isNotEmpty() &&
                type != "password" && type != "number" &&
                type != "decimal" && type != "multiline"
            ) {
                threshold = 1
                val suggestionsAdapter = ArrayAdapter<String>(
                    this@MainActivity,
                    android.R.layout.simple_dropdown_item_1line,
                    mutableListOf()
                )
                setAdapter(suggestionsAdapter)
                setDropDownVerticalOffset(dp(4))

                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(
                        text: CharSequence?, start: Int, count: Int, after: Int
                    ) = Unit

                    override fun onTextChanged(
                        text: CharSequence?, start: Int, before: Int, count: Int
                    ) = Unit

                    override fun afterTextChanged(editable: Editable?) {
                        val prefix = editable?.toString().orEmpty().trim()
                        if (prefix.isBlank()) {
                            suggestionsAdapter.clear()
                            dismissDropDown()
                            return
                        }

                        val matches = suggestionKeys
                            .flatMap { key -> db.suggestions(key, prefix) }
                            .distinct()
                            .take(10)

                        suggestionsAdapter.clear()
                        suggestionsAdapter.addAll(matches)
                        suggestionsAdapter.notifyDataSetChanged()

                        if (matches.isNotEmpty() && hasFocus()) {
                            post { if (hasFocus()) showDropDown() }
                        } else {
                            dismissDropDown()
                        }
                    }
                })
            }
        }
    }

    private fun matchWidth() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun dp(value: Int) =
        (value * resources.displayMetrics.density).toInt()

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun allowed(module: String, action: String): Boolean {
        val ok = db.hasPermission(module, action)
        if (!ok) toast("ليس لديك صلاحية تنفيذ هذه العملية")
        return ok
    }

    private fun showMessage(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("موافق", null)
            .show()
    }

    // ---------------------------------------------------------
    // إنشاء المدير وتسجيل الدخول
    // ---------------------------------------------------------

    private fun showCreateAdmin() {
        page("خبراء هونداي وفورد", "إنشاء حساب المدير الأول")

        val username = field("اسم المستخدم")
        val name = field("اسم المدير")
        val password = field("كلمة المرور", "password")
        val confirm = field("تأكيد كلمة المرور", "password")

        root.addView(username, matchWidth())
        root.addView(name, matchWidth())
        root.addView(password, matchWidth())
        root.addView(confirm, matchWidth())

        addAction("إنشاء حساب المدير") {
            if (username.text.isBlank() ||
                name.text.isBlank() ||
                password.text.length < 6
            ) {
                toast("أكمل البيانات، وكلمة المرور 6 أحرف على الأقل")
                return@addAction
            }

            if (password.text.toString() != confirm.text.toString()) {
                toast("كلمتا المرور غير متطابقتين")
                return@addAction
            }

            if (db.createAdmin(
                    username.text.toString(),
                    name.text.toString(),
                    password.text.toString()
                )
            ) {
                loggedUser = db.currentUser()
                if (loggedUser == null) {
                    toast("تم إنشاء الحساب، يرجى تسجيل الدخول")
                    showLogin()
                } else {
                    showDashboard()
                }
            } else {
                toast("تعذر إنشاء الحساب. تحقق من البيانات")
            }
        }
    }

    private fun showLogin() {
        db.clearActiveUser()
        loggedUser = null

        page("خبراء هونداي وفورد", "تسجيل الدخول")

        val username = field("اسم المستخدم")
        val password = field("كلمة المرور", "password")

        root.addView(username, matchWidth())
        root.addView(password, matchWidth())

        addAction("دخول") {
            if (db.authenticate(
                    username.text.toString(),
                    password.text.toString()
                )
            ) {
                val user = db.currentUser()
                if (user == null) {
                    db.clearActiveUser()
                    toast("تعذر تحميل بيانات المستخدم")
                    return@addAction
                }

                loggedUser = user
                showDashboard()
            } else {
                toast("اسم المستخدم أو كلمة المرور غير صحيحة")
            }
        }
    }

    private fun logout() {
        db.clearActiveUser()
        loggedUser = null
        showLogin()
    }

    // ---------------------------------------------------------
    // لوحة التحكم
    // ---------------------------------------------------------

    private fun dashboardCard(title: String, subtitle: String = "") {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), silver)
            }
            elevation = dp(2).toFloat()
        }
        val heading = TextView(this).apply {
            text = title
            textSize = 17f
            setTextColor(gold)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.RIGHT
            setPadding(0, dp(3), 0, dp(3))
        }
        card.addView(heading, matchWidth())
        if (subtitle.isNotBlank()) {
            card.addView(TextView(this).apply {
                text = subtitle
                textSize = 12f
                setTextColor(muted)
                gravity = Gravity.RIGHT
            }, matchWidth())
        }
        pageRoot.addView(card, matchWidth().apply { setMargins(0, dp(7), 0, dp(7)) })
        // اجعل البطاقة هي الحاوية النشطة لإضافة أزرارها
        root = card
    }

    private fun dashboardAction(label: String, action: () -> Unit, accent: Boolean = false) {
        val button = Button(this).apply {
            text = label
            textSize = 14f
            isAllCaps = false
            setTextColor(if (accent) blue else Color.WHITE)
            background = rounded(if (accent) Color.rgb(232, 218, 187) else blueMid)
            setPadding(dp(10), dp(7), dp(10), dp(7))
            minHeight = dp(46)
            setOnClickListener { action() }
        }
        root.addView(button, matchWidth().apply { setMargins(0, dp(4), 0, dp(4)) })
    }

    private fun showDashboard() {
        val user = db.currentUser()
        if (user == null) {
            showLogin()
            return
        }
        loggedUser = user
        page("خبراء هونداي وفورد", "لوحة إدارة الورشة")

        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(20), dp(18), dp(20))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(blue, blueMid)
            ).apply { cornerRadius = dp(20).toFloat() }
        }
        hero.addView(TextView(this).apply {
            text = "خبراء هونداي وفورد"
            textSize = 23f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }, matchWidth())
        hero.addView(TextView(this).apply {
            text = "مرحباً ${user.fullName}  •  ${user.role}"
            textSize = 14f
            setTextColor(Color.rgb(232, 218, 187))
            gravity = Gravity.CENTER
            setPadding(0, dp(7), 0, 0)
        }, matchWidth())
        root.addView(hero, matchWidth().apply { setMargins(0, 0, 0, dp(12)) })

        dashboardCard("ملخص الورشة", "نظرة سريعة على سجلات العمل")
        val metrics = mutableListOf<Pair<String, String>>()
        if (db.hasPermission("customers", "view")) metrics += "العملاء" to db.count("customers").toString()
        if (db.hasPermission("vehicles", "view")) metrics += "السيارات" to db.count("vehicles").toString()
        if (db.hasPermission("work_orders", "view")) metrics += "أوامر الإصلاح" to db.count("work_orders").toString()
        if (db.hasPermission("parts", "view")) metrics += "قطع الغيار" to db.count("parts").toString()
        metrics.forEach { (label, value) ->
            val line = TextView(this).apply {
                text = "$label     $value"
                textSize = 15f
                setTextColor(blue)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.RIGHT
                setPadding(dp(5), dp(6), dp(5), dp(6))
            }
            root.addView(line, matchWidth())
        }

        dashboardCard("الاستقبال", "البحث والتحقق من بيانات العميل والسيارة")
        dashboardAction("استقبال سيارة / البحث عن عميل أو سيارة", {
            if (allowed("vehicles", "view")) receptionScreen()
        }, true)

        dashboardCard("إدارة الورشة", "إضافة البيانات والانتقال إلى ملفاتها")
        if (db.hasPermission("customers", "add")) dashboardAction("إضافة عميل", { customerForm() })
        if (db.hasPermission("vehicles", "add")) dashboardAction("إضافة سيارة", { vehicleForm() })
        if (db.hasPermission("work_orders", "add")) dashboardAction("أمر إصلاح / زيارة جديدة", { workOrderForm() })
        if (db.hasPermission("parts", "add")) dashboardAction("إضافة قطعة غيار", { partForm() })
        if (db.hasPermission("customers", "view")) dashboardAction("بحث عن عميل", { customerSearchDialog() }, true)
        if (db.hasPermission("vehicles", "view")) dashboardAction("بحث عن سيارة", { vehicleSearchDialog() }, true)

        dashboardCard("السجلات", "استعراض بيانات الورشة المسجلة")
        if (db.hasPermission("customers", "view")) dashboardAction("سجل العملاء", { recordsDialog("customers", "العملاء") })
        if (db.hasPermission("vehicles", "view")) dashboardAction("سجل السيارات", { recordsDialog("vehicles", "السيارات") })
        if (db.hasPermission("work_orders", "view")) dashboardAction("سجل أوامر الإصلاح", { recordsDialog("work_orders", "أوامر الإصلاح") })
        if (db.hasPermission("parts", "view")) dashboardAction("سجل قطع الغيار", { recordsDialog("parts", "قطع الغيار") })

        if (user.role == "مدير") {
            dashboardCard("إدارة النظام", "الموظفون والصلاحيات")
            dashboardAction("إدارة الموظفين والصلاحيات", { usersScreen() })
        }
        dashboardAction("تسجيل الخروج", { logout() }, true)
        addText("الإصدار 0.7.0", 12f, muted)
    }

    // ---------------------------------------------------------
    // نماذج الإدخال
    // ---------------------------------------------------------

    private fun formDialog(
        title: String,
        views: List<View>,
        save: (AlertDialog) -> Unit
    ) {
        activeFormDialog?.dismiss()

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(5), dp(18), dp(5))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        views.forEach { view ->
            box.addView(
                view,
                matchWidth().apply {
                    setMargins(0, dp(4), 0, dp(7))
                }
            )
        }

        val scroll = ScrollView(this)
        scroll.addView(box)

        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(scroll)
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
        if (activeFormDialog === dialog) activeFormDialog = null
        toast("تم الحفظ بنجاح")
        showDashboard()
    }

    private fun customerForm() {
        if (!allowed("customers", "add")) return

        val name = field("اسم العميل", suggestionKeys = listOf("customer_name"))
        val phone = field("رقم الهاتف", "phone", listOf("customer_phone"))
        val alt = field("هاتف إضافي", "phone", listOf("customer_phone"))
        val address = field("العنوان")
        val notes = field("ملاحظات", "multiline")

        formDialog(
            "إضافة عميل",
            listOf(name, phone, alt, address, notes)
        ) { dialog ->
            try {
                db.addCustomer(
                    name.text.toString(),
                    phone.text.toString(),
                    alt.text.toString(),
                    address.text.toString(),
                    notes.text.toString()
                )
                done(dialog)
            } catch (e: Exception) {
                toast(e.message ?: "تعذر حفظ العميل")
            }
        }
    }

    private fun vehicleForm(customerId: Long? = null) {
        if (!allowed("vehicles", "add")) return

        val customers = db.customerOptions()
        if (customers.isEmpty() && customerId == null) {
            toast("أضف عميلاً أولاً")
            return
        }

        val customer = Spinner(this)
        val customerLabels = customers.map { it.second }
        customer.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            customerLabels
        )

        if (customerId != null) {
            val index = customers.indexOfFirst { it.first == customerId }
            if (index >= 0) customer.setSelection(index)
        }

        val make = field("الشركة / الصانع")
        val model = field("طراز السيارة")
        val year = field("سنة الصنع", "number")
        val engine = field("نوع المحرك")
        val plate = field("رقم اللوحة", suggestionKeys = listOf("plate"))
        val vin = field("رقم الهيكل VIN", suggestionKeys = listOf("vin"))
        val odometer = field("قراءة العداد", "number")
        val notes = field("ملاحظات السيارة", "multiline")

        val views = mutableListOf<View>()
        if (customerId == null) {
            views.add(TextView(this).apply { text = "العميل" })
            views.add(customer)
        }
        views.addAll(listOf(make, model, year, engine, plate, vin, odometer, notes))

        formDialog("إضافة سيارة", views) { dialog ->
            try {
                val selectedId = customerId
                    ?: customers.getOrNull(customer.selectedItemPosition)?.first
                    ?: throw IllegalArgumentException("اختر العميل")

                db.addVehicle(
                    selectedId,
                    make.text.toString(),
                    model.text.toString(),
                    year.text.toString(),
                    engine.text.toString(),
                    plate.text.toString(),
                    vin.text.toString(),
                    odometer.text.toString(),
                    notes.text.toString()
                )
                done(dialog)
            } catch (e: Exception) {
                toast(e.message ?: "تعذر حفظ السيارة")
            }
        }
    }

    private fun workOrderForm(
        selectedCustomerId: Long? = null,
        selectedVehicleId: Long? = null
    ) {
        if (!allowed("work_orders", "add")) return

        val customers = db.customerOptions()
        if (customers.isEmpty()) {
            toast("أضف عميلاً أولاً")
            return
        }

        val customerSpinner = Spinner(this)
        customerSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            customers.map { it.second }
        )

        if (selectedCustomerId != null) {
            val index = customers.indexOfFirst { it.first == selectedCustomerId }
            if (index >= 0) customerSpinner.setSelection(index)
            customerSpinner.isEnabled = false
        }

        var vehicles = db.vehicleOptions(selectedCustomerId)
        val vehicleSpinner = Spinner(this)
        val vehicleAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            vehicles.map { it.third }.toMutableList()
        )
        vehicleSpinner.adapter = vehicleAdapter

        fun loadVehicles(customerId: Long?, preferredVehicleId: Long? = null) {
            vehicles = db.vehicleOptions(customerId)
            vehicleAdapter.clear()
            vehicleAdapter.addAll(vehicles.map { it.third })
            vehicleAdapter.notifyDataSetChanged()

            val preferredIndex = preferredVehicleId?.let { id ->
                vehicles.indexOfFirst { it.first == id }
            } ?: -1
            if (preferredIndex >= 0) vehicleSpinner.setSelection(preferredIndex)

            vehicleSpinner.isEnabled = vehicles.isNotEmpty()
        }

        if (selectedCustomerId == null) {
            customerSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) {
                    loadVehicles(null)
                }

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    val customerId = customers.getOrNull(position)?.first
                    loadVehicles(customerId)
                }
            }
            loadVehicles(customers.getOrNull(customerSpinner.selectedItemPosition)?.first)
        } else {
            loadVehicles(selectedCustomerId, selectedVehicleId)
        }

        val odometer = field("قراءة العداد", "number")
        val complaint = field("شكوى العميل", "multiline")
        val diagnosis = field("التشخيص", "multiline")
        val notes = field("ملاحظات الإصلاح", "multiline")
        val labor = field("أجور العمل", "decimal")
        val parts = field("تكلفة القطع", "decimal")

        formDialog(
            "أمر إصلاح / زيارة جديدة",
            listOf(
                TextView(this).apply { text = "العميل" },
                customerSpinner,
                TextView(this).apply { text = "السيارة" },
                vehicleSpinner,
                odometer, complaint, diagnosis, notes, labor, parts
            )
        ) { dialog ->
            try {
                val customerId = selectedCustomerId
                    ?: customers.getOrNull(customerSpinner.selectedItemPosition)?.first
                    ?: throw IllegalArgumentException("اختر العميل")

                val vehicle = vehicles.getOrNull(vehicleSpinner.selectedItemPosition)
                    ?: throw IllegalArgumentException("لا توجد سيارة مرتبطة بالعميل المحدد")

                require(vehicle.second == customerId) {
                    "السيارة لا تتبع العميل المحدد"
                }

                db.addWorkOrder(
                    customerId,
                    vehicle.first,
                    odometer.text.toString(),
                    complaint.text.toString(),
                    diagnosis.text.toString(),
                    notes.text.toString(),
                    "مفتوح",
                    labor.text.toString(),
                    parts.text.toString()
                )
                done(dialog)
            } catch (e: Exception) {
                toast(e.message ?: "تعذر حفظ أمر الإصلاح")
            }
        }
    }

    private fun partForm() {
        if (!allowed("parts", "add")) return

        val number = field("رقم القطعة", suggestionKeys = listOf("part_number"))
        val name = field("اسم القطعة", suggestionKeys = listOf("part_name"))
        val qty = field("الكمية", "decimal")
        val unit = field("الوحدة")
        val buy = field("سعر الشراء", "decimal")
        val sell = field("سعر البيع", "decimal")

        formDialog(
            "إضافة قطعة غيار",
            listOf(number, name, qty, unit, buy, sell)
        ) { dialog ->
            try {
                db.addPart(
                    number.text.toString(),
                    name.text.toString(),
                    qty.text.toString(),
                    unit.text.toString(),
                    buy.text.toString(),
                    sell.text.toString()
                )
                done(dialog)
            } catch (e: Exception) {
                toast(e.message ?: "تعذر حفظ القطعة")
            }
        }
    }

    // ---------------------------------------------------------
    // إدارة المستخدمين والصلاحيات
    // ---------------------------------------------------------

    private fun usersScreen() {
        if (db.currentUser()?.role != "مدير") {
            toast("هذه الصفحة للمدير فقط")
            return
        }

        page("إدارة المستخدمين", "إنشاء الحسابات وتحديد صلاحياتها")

        addAction("إنشاء مستخدم جديد") { createUserForm() }

        db.listUsers().forEach { user ->
            val label = "${user.fullName} — ${user.username} (${user.role})"
            addAction(label) { employeeOptions(user) }
        }

        addAction("رجوع", { showDashboard() })
    }

    private fun employeeOptions(user: AuthUser) {
        val options = if (user.role == "مدير") {
            arrayOf("عرض بيانات المدير")
        } else {
            arrayOf("الملف الشخصي وتعديل البيانات", "تغيير كلمة المرور", "الصلاحيات", "حذف الموظف")
        }
        AlertDialog.Builder(this)
            .setTitle(user.fullName)
            .setItems(options) { _, index ->
                when (options[index]) {
                    "عرض بيانات المدير" -> showMessage("حساب المدير", "اسم المستخدم: ${user.username}\nالاسم: ${user.fullName}\nالدور: ${user.role}\nلا يمكن تعديل أو حذف حساب المدير من هذه الشاشة.")
                    "الملف الشخصي وتعديل البيانات" -> editEmployeeForm(user)
                    "تغيير كلمة المرور" -> changeEmployeePasswordForm(user)
                    "الصلاحيات" -> permissionsScreen(user)
                    "حذف الموظف" -> confirmDeleteEmployee(user)
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun editEmployeeForm(user: AuthUser) {
        val username = field("اسم المستخدم")
        username.setText(user.username)
        val name = field("الاسم الكامل")
        name.setText(user.fullName)
        val role = Spinner(this)
        val roles = listOf("فني", "موظف استقبال", "محاسب", "مخزن")
        role.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, roles)
        role.setSelection(roles.indexOf(user.role).coerceAtLeast(0))
        formDialog("تعديل بيانات الموظف", listOf(username, name, role)) { dialog ->
            if (db.updateUserProfile(user.id, username.text.toString(), name.text.toString(), role.selectedItem.toString())) {
                done(dialog)
                usersScreen()
            } else toast("تعذر التعديل. تحقق من اسم المستخدم وعدم تكراره")
        }
    }

    private fun changeEmployeePasswordForm(user: AuthUser) {
        val password = field("كلمة المرور الجديدة", "password")
        val confirm = field("تأكيد كلمة المرور", "password")
        formDialog("تغيير كلمة مرور ${user.fullName}", listOf(password, confirm)) { dialog ->
            if (password.text.length < 6) {
                toast("كلمة المرور يجب ألا تقل عن 6 أحرف")
                return@formDialog
            }
            if (password.text.toString() != confirm.text.toString()) {
                toast("كلمتا المرور غير متطابقتين")
                return@formDialog
            }
            if (db.changeUserPassword(user.id, password.text.toString())) {
                done(dialog)
                usersScreen()
            } else toast("تعذر تغيير كلمة المرور")
        }
    }

    private fun confirmDeleteEmployee(user: AuthUser) {
        AlertDialog.Builder(this)
            .setTitle("تأكيد حذف الموظف")
            .setMessage("سيتم حذف حساب ${user.fullName} وصلاحياته فقط. لن يتم حذف سجلات الورشة.")
            .setPositiveButton("حذف") { _, _ ->
                if (db.deleteUser(user.id)) {
                    toast("تم حذف حساب الموظف")
                    usersScreen()
                } else toast("تعذر الحذف. الحساب محمي أو مرتبط بعملية نشطة")
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun createUserForm() {
        if (db.currentUser()?.role != "مدير") return

        val username = field("اسم المستخدم")
        val name = field("الاسم الكامل")
        val password = field("كلمة المرور", "password")
        val role = Spinner(this)
        role.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            listOf("فني", "موظف استقبال", "محاسب", "مخزن")
        )

        formDialog(
            "إنشاء مستخدم",
            listOf(username, name, password, role)
        ) { dialog ->
            val success = db.createUser(
                username.text.toString(),
                name.text.toString(),
                password.text.toString(),
                role.selectedItem.toString()
            )

            if (success) {
                done(dialog)
                usersScreen()
            } else {
                toast("تعذر إنشاء المستخدم. تأكد من البيانات وأن كلمة المرور 6 أحرف على الأقل")
            }
        }
    }

    private fun permissionsScreen(user: AuthUser) {
        if (db.currentUser()?.role != "مدير") return

        val modules = listOf(
            "customers" to "العملاء",
            "vehicles" to "السيارات",
            "work_orders" to "أوامر الإصلاح",
            "parts" to "قطع الغيار",
            "users" to "المستخدمون"
        )

        val permissions = db.getPermissions(user.id)
        val checks = mutableListOf<Triple<String, String, CheckBox>>()

        page("صلاحيات المستخدم", user.fullName)

        modules.forEach { (module, title) ->
            addText(title, 16f, blue, true)

            val p = permissions.firstOrNull { it.module == module }

            listOf(
                Triple("view", "عرض", p?.canView ?: false),
                Triple("add", "إضافة", p?.canAdd ?: false),
                Triple("edit", "تعديل", p?.canEdit ?: false),
                Triple("delete", "حذف", p?.canDelete ?: false)
            ).forEach { (action, label, enabled) ->
                val check = CheckBox(this).apply {
                    text = label
                    isChecked = enabled
                    textSize = 14f
                }
                root.addView(check, matchWidth())
                checks.add(Triple(module, action, check))
            }
        }

        addAction("حفظ الصلاحيات") {
            var success = true

            checks.forEach { (module, action, check) ->
                if (!db.setPermission(
                        user.id,
                        module,
                        action,
                        check.isChecked
                    )
                ) {
                    success = false
                }
            }

            if (success) {
                toast("تم حفظ الصلاحيات")
                usersScreen()
            } else {
                toast("تعذر حفظ بعض الصلاحيات")
            }
        }

        addAction("رجوع", { usersScreen() })
    }

    // ---------------------------------------------------------
    // الاستقبال والتحقق من الملكية
    // ---------------------------------------------------------

    private fun receptionScreen() {
        if (!allowed("vehicles", "view")) return

        page("استقبال سيارة", "ابحث برقم الهاتف أو رقم الهيكل أو اللوحة")

        val phone = field("رقم هاتف العميل", "phone", listOf("customer_phone"))
        val identifier = field("رقم الهيكل VIN أو رقم اللوحة", suggestionKeys = listOf("vin", "plate"))

        root.addView(phone, matchWidth())
        root.addView(identifier, matchWidth())

        addAction("بحث والتحقق من البيانات") {
            receptionSearch(
                phone.text.toString(),
                identifier.text.toString()
            )
        }

        addAction("إلغاء", { showDashboard() })
    }

    private fun receptionSearch(phone: String, identifier: String) {
        val cleanPhone = WorkshopDb.normalizePhone(phone)
        val cleanIdentifier = identifier.trim()

        if (cleanPhone.isBlank() && cleanIdentifier.isBlank()) {
            toast("أدخل رقم الهاتف أو رقم الهيكل أو رقم اللوحة")
            return
        }

        // مطابقة مستقلة ودقيقة لكل مدخل؛ لا نسمح لنتيجة جزئية أو اسم سيارة
        // بأن تتحول إلى سجل جديد أو أن تخفي السجل الموجود.
        val customers = if (cleanPhone.isBlank()) emptyList()
            else db.findCustomersByPhone(cleanPhone).distinctBy { it.id }
        val vehicles = if (cleanIdentifier.isBlank()) emptyList()
            else db.findVehiclesByIdentifier(cleanIdentifier).distinctBy { it.id }

        if (cleanPhone.isNotBlank() && cleanIdentifier.isNotBlank()) {
            when {
                customers.size == 1 && vehicles.size == 1 -> {
                    val customer = customers.first()
                    val vehicle = vehicles.first()
                    if (vehicle.customerId == customer.id) {
                        receptionChoices(customer.id, vehicle.id)
                    } else {
                        ownershipConflict(customer.id, vehicle.id)
                    }
                    return
                }
                customers.size > 1 -> {
                    AlertDialog.Builder(this)
                        .setTitle("رقم الهاتف مرتبط بأكثر من نتيجة")
                        .setItems(customers.map { "${it.name} — ${it.phone}" }.toTypedArray()) { _, index ->
                            val selected = customers[index]
                            val owned = vehicles.filter { it.customerId == selected.id }
                            val candidates = if (owned.isNotEmpty()) owned else vehicles
                            when (candidates.size) {
                                0 -> showCustomerProfile(selected.id)
                                1 -> {
                                    val match = candidates.first()
                                    if (match.customerId == selected.id) receptionChoices(selected.id, match.id)
                                    else ownershipConflict(selected.id, match.id)
                                }
                                else -> AlertDialog.Builder(this).setTitle("اختر السيارة المطابقة")
                                    .setItems(candidates.map { "${it.make} ${it.model} — ${it.plate}" }.toTypedArray()) { _, vehicleIndex ->
                                        val match = candidates[vehicleIndex]
                                        if (match.customerId == selected.id) receptionChoices(selected.id, match.id)
                                        else ownershipConflict(selected.id, match.id)
                                    }.setNegativeButton("إلغاء", null).show()
                            }
                        }
                        .setNegativeButton("إلغاء", null).show()
                    return
                }
                customers.size == 1 && vehicles.isEmpty() -> {
                    val customer = customers.first()
                    val customerVehicles = db.getCustomerVehicles(customer.id)
                    val message = if (customerVehicles.isEmpty())
                        "العميل موجود، لكن رقم السيارة المدخل غير مسجل له."
                    else
                        "العميل موجود، لكن رقم السيارة المدخل لا يطابق سياراته المسجلة. لن ننشئ سجلاً مكرراً."
                    AlertDialog.Builder(this)
                        .setTitle("العميل موجود — ${customer.name}")
                        .setMessage(message)
                        .setPositiveButton("فتح ملف العميل") { _, _ -> showCustomerProfile(customer.id) }
                        .setNeutralButton("إضافة سيارة لهذا العميل") { _, _ ->
                            if (db.hasPermission("vehicles", "add")) vehicleForm(customer.id)
                            else toast("ليس لديك صلاحية إضافة سيارة")
                        }
                        .setNegativeButton("إلغاء", null).show()
                    return
                }
                customers.isEmpty() && vehicles.size == 1 -> {
                    val vehicle = vehicles.first()
                    AlertDialog.Builder(this)
                        .setTitle("السيارة موجودة بالفعل")
                        .setMessage("السيارة مسجلة باسم ${vehicle.ownerName}، هاتف المالك: ${vehicle.ownerPhone}. رقم الهاتف المدخل لا يطابق بيانات المالك. لن ننشئ عميلاً جديداً تلقائياً.")
                        .setPositiveButton("فتح ملف السيارة") { _, _ -> showVehicleProfile(vehicle.id) }
                        .setNeutralButton("فتح ملف المالك الحالي") { _, _ ->
                            showCustomerProfile(vehicle.customerId)
                        }
                        .setNegativeButton("إلغاء", null).show()
                    return
                }
                customers.isEmpty() && vehicles.isEmpty() -> {
                    AlertDialog.Builder(this)
                        .setTitle("لا توجد سجلات مطابقة")
                        .setMessage("لم نعثر على العميل أو السيارة. يمكنك تسجيلهما بعد التأكد من صحة الرقمين.")
                        .setPositiveButton("تسجيل عميل وسيارة") { _, _ ->
                            if (db.hasPermission("customers", "add") && db.hasPermission("vehicles", "add"))
                                newCustomerAndVehicleForm(cleanPhone, cleanIdentifier)
                            else toast("ليس لديك صلاحية تسجيل عميل وسيارة")
                        }
                        .setNegativeButton("إلغاء", null).show()
                    return
                }
                customers.isNotEmpty() && vehicles.isNotEmpty() -> {
                    val selectCustomer: (Int) -> Unit = { customerIndex ->
                        val selectedCustomer = customers[customerIndex]
                        val ownedMatches = vehicles.filter { it.customerId == selectedCustomer.id }
                        val candidates = if (ownedMatches.isNotEmpty()) ownedMatches else vehicles
                        if (candidates.size == 1) {
                            val selectedVehicle = candidates.first()
                            if (selectedVehicle.customerId == selectedCustomer.id)
                                receptionChoices(selectedCustomer.id, selectedVehicle.id)
                            else ownershipConflict(selectedCustomer.id, selectedVehicle.id)
                        } else {
                            AlertDialog.Builder(this).setTitle("اختر السيارة المطابقة")
                                .setItems(candidates.map { "${it.make} ${it.model} — ${it.plate}" }.toTypedArray()) { _, vehicleIndex ->
                                    val selectedVehicle = candidates[vehicleIndex]
                                    if (selectedVehicle.customerId == selectedCustomer.id)
                                        receptionChoices(selectedCustomer.id, selectedVehicle.id)
                                    else ownershipConflict(selectedCustomer.id, selectedVehicle.id)
                                }.setNegativeButton("إلغاء", null).show()
                        }
                    }
                    if (customers.size == 1) selectCustomer(0)
                    else AlertDialog.Builder(this).setTitle("اختر العميل")
                        .setItems(customers.map { "${it.name} — ${it.phone}" }.toTypedArray()) { _, index -> selectCustomer(index) }
                        .setNegativeButton("إلغاء", null).show()
                    return
                }
                else -> {
                    toast("تعذر تحديد سجل واحد؛ راجع رقم الهاتف أو رقم السيارة")
                    return
                }
            }
        }

        // البحث برقم الهاتف وحده يعرض العميل وسياراته مباشرة.
        if (cleanIdentifier.isBlank()) {
            when (customers.size) {
                0 -> showMessage("لا توجد نتائج", "لم نعثر على عميل بهذا الرقم.")
                1 -> showCustomerProfile(customers.first().id)
                else -> AlertDialog.Builder(this).setTitle("اختر العميل")
                    .setItems(customers.map { "${it.name} — ${it.phone}" }.toTypedArray()) { _, index ->
                        showCustomerProfile(customers[index].id)
                    }.setNegativeButton("إلغاء", null).show()
            }
            return
        }

        // البحث برقم اللوحة أو الهيكل وحده يعرض السيارة ومالكها.
        when (vehicles.size) {
            0 -> showMessage("لا توجد نتائج", "لم نعثر على سيارة بهذا الرقم.")
            1 -> showVehicleProfile(vehicles.first().id)
            else -> AlertDialog.Builder(this).setTitle("اختر السيارة")
                .setItems(vehicles.map { "${it.make} ${it.model} — ${it.plate}\nVIN: ${it.vin}" }.toTypedArray()) { _, index ->
                    showVehicleProfile(vehicles[index].id)
                }.setNegativeButton("إلغاء", null).show()
        }
    }

    private fun receptionChoices(customerId: Long, vehicleId: Long) {
        AlertDialog.Builder(this)
            .setTitle("متابعة الاستقبال")
            .setItems(
                arrayOf("فتح ملف السيارة", "بدء زيارة / أمر إصلاح")
            ) { _, which ->
                when (which) {
                    0 -> showVehicleProfile(vehicleId)
                    1 -> workOrderForm(customerId, vehicleId)
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun ownershipConflict(
        newCustomerId: Long,
        vehicleId: Long
    ) {
        val vehicle = db.getVehicleProfile(vehicleId) ?: return

        AlertDialog.Builder(this)
            .setTitle("تعارض في بيانات الملكية")
            .setMessage(
                "السيارة مسجلة حالياً باسم: ${vehicle.ownerName}\n" +
                    "وهناك عميل آخر برقم الهاتف المدخل.\n\n" +
                    "لن يتم تغيير الملكية تلقائياً."
            )
            .setItems(
                arrayOf(
                    "الإبقاء على المالك الحالي ومتابعة الاستقبال",
                    "نقل الملكية إلى العميل الجديد",
                    "إلغاء العملية"
                )
            ) { _, which ->
                when (which) {
                    0 -> {
                        val owner = db.getCustomerProfile(vehicle.customerId)
                        if (owner != null) {
                            receptionChoices(owner.id, vehicleId)
                        }
                    }
                    1 -> transferOwnershipDialog(vehicleId, newCustomerId)
                    else -> Unit
                }
            }
            .show()
    }

    private fun transferOwnershipDialog(
        vehicleId: Long,
        newCustomerId: Long
    ) {
        if (!allowed("vehicles", "edit")) return

        val reason = field("سبب نقل الملكية", "multiline")

        AlertDialog.Builder(this)
            .setTitle("تأكيد نقل الملكية")
            .setMessage("سيتم تسجيل المالك السابق والجديد وسبب النقل في سجل الملكية.")
            .setView(reason)
            .setPositiveButton("نقل الملكية", null)
            .setNegativeButton("إلغاء", null)
            .create()
            .also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                        .setOnClickListener {
                            if (reason.text.isBlank()) {
                                toast("أدخل سبب نقل الملكية")
                                return@setOnClickListener
                            }

                            val success = db.transferVehicleOwnership(
                                vehicleId,
                                newCustomerId,
                                reason.text.toString()
                            )

                            if (success) {
                                dialog.dismiss()
                                toast("تم نقل الملكية وتسجيل العملية")
                                receptionChoices(newCustomerId, vehicleId)
                            } else {
                                toast("تعذر نقل الملكية")
                            }
                        }
                }
                dialog.show()
            }
    }

    private fun newCustomerForExistingVehicle(vehicleId: Long) {
        if (!allowed("customers", "add")) return

        val name = field("اسم العميل الجديد", suggestionKeys = listOf("customer_name"))
        val phone = field("رقم الهاتف", "phone", listOf("customer_phone"))
        val alt = field("هاتف إضافي", "phone", listOf("customer_phone"))
        val address = field("العنوان")

        formDialog(
            "تسجيل العميل الجديد",
            listOf(name, phone, alt, address)
        ) { dialog ->
            try {
                val id = db.addCustomer(
                    name.text.toString(),
                    phone.text.toString(),
                    alt.text.toString(),
                    address.text.toString()
                )
                dialog.dismiss()
                if (activeFormDialog === dialog) activeFormDialog = null
                toast("تم تسجيل العميل. يرجى تحديد إجراء الملكية")
                ownershipConflict(id, vehicleId)
            } catch (e: Exception) {
                toast(e.message ?: "تعذر إضافة العميل")
            }
        }
    }

    private fun newCustomerAndVehicleForm(
        phoneValue: String,
        identifierValue: String
    ) {
        if (!allowed("customers", "add") ||
            !allowed("vehicles", "add")
        ) return

        val name = field("اسم العميل", suggestionKeys = listOf("customer_name"))
        val phone = field("رقم الهاتف", "phone", listOf("customer_phone"))
        phone.setText(phoneValue)
        val alt = field("هاتف إضافي", "phone", listOf("customer_phone"))
        val address = field("العنوان")
        val customerNotes = field("ملاحظات العميل", "multiline")
        val make = field("الشركة / الصانع")
        val model = field("طراز السيارة")
        val year = field("سنة الصنع", "number")
        val engine = field("نوع المحرك")
        val plate = field("رقم اللوحة", suggestionKeys = listOf("plate"))
        val vin = field("رقم الهيكل VIN", suggestionKeys = listOf("vin"))
        val odometer = field("قراءة العداد", "number")
        val vehicleNotes = field("ملاحظات السيارة", "multiline")

        if (identifierValue.length == 17) vin.setText(identifierValue)
        else plate.setText(identifierValue)

        formDialog(
            "عميل وسيارة جديدة",
            listOf(
                name, phone, alt, address, customerNotes,
                make, model, year, engine, plate, vin, odometer, vehicleNotes
            )
        ) { dialog ->
            if (name.text.isBlank() ||
                phone.text.isBlank() ||
                make.text.isBlank() ||
                model.text.isBlank() ||
                vin.text.isBlank()
            ) {
                toast("أكمل بيانات العميل والسيارة، ورقم الهيكل VIN مطلوب")
                return@formDialog
            }

            val plateValue = plate.text.toString().trim()
            if (plateValue.isNotBlank()) {
                val duplicatePlate = db.readableDatabase.rawQuery(
                    "SELECT id FROM vehicles WHERE TRIM(plate)=? COLLATE NOCASE LIMIT 1",
                    arrayOf(plateValue)
                ).use { it.moveToFirst() }
                if (duplicatePlate) {
                    toast("رقم اللوحة مسجل مسبقاً لسيارة أخرى")
                    return@formDialog
                }
            }

            val result = db.addCustomerAndVehicle(
                name.text.toString(),
                phone.text.toString(),
                alt.text.toString(),
                address.text.toString(),
                make.text.toString(),
                model.text.toString(),
                year.text.toString(),
                engine.text.toString(),
                plate.text.toString(),
                vin.text.toString(),
                odometer.text.toString(),
                customerNotes.text.toString(),
                vehicleNotes.text.toString()
            )

            if (result == null) {
                toast("تعذر الحفظ. تحقق من رقم الهيكل والبيانات وعدم تكرارها")
            } else {
                dialog.dismiss()
                activeFormDialog = null
                toast("تم تسجيل العميل والسيارة معاً")
                receptionChoices(result.first, result.second)
            }
        }
    }

    // ---------------------------------------------------------
    // البحث وملفات العملاء والسيارات
    // ---------------------------------------------------------

    private fun searchCustomerRecords(query: String): List<ReceptionSearchResult> {
        val clean = query.trim()
        if (clean.isBlank()) return emptyList()
        val phone = WorkshopDb.normalizePhone(clean)
        val namePattern = "%$clean%"
        val phonePattern = if (phone.isBlank()) "%__NO_PHONE_MATCH__%" else "%$phone%"
        val results = mutableListOf<ReceptionSearchResult>()
        db.readableDatabase.rawQuery(
            """SELECT id, name, phone FROM customers
               WHERE name LIKE ? COLLATE NOCASE OR phone LIKE ?
               ORDER BY id DESC LIMIT 30""",
            arrayOf(namePattern, phonePattern)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                results += ReceptionSearchResult(
                    type = "customer",
                    id = cursor.getLong(0),
                    title = cursor.getString(1) ?: "",
                    subtitle = "عميل — هاتف: ${cursor.getString(2) ?: ""}"
                )
            }
        }
        return results
    }

    private fun customerSearchDialog() {
        if (!allowed("customers", "view")) return
        val query = field("رقم هاتف العميل أو اسمه", suggestionKeys = listOf("customer_name", "customer_phone"))

        AlertDialog.Builder(this)
            .setTitle("بحث عن عميل")
            .setView(query)
            .setPositiveButton("بحث") { _, _ ->
                val results = searchCustomerRecords(query.text.toString())

                if (results.isEmpty()) {
                    toast("لم يتم العثور على عميل")
                } else {
                    AlertDialog.Builder(this)
                        .setTitle("نتائج البحث")
                        .setItems(results.map {
                            "${it.title}\n${it.subtitle}"
                        }.toTypedArray()) { _, index ->
                            showCustomerProfile(results[index].id)
                        }
                        .show()
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun vehicleSearchDialog() {
        if (!allowed("vehicles", "view")) return
        val query = field("رقم الهيكل أو اللوحة", suggestionKeys = listOf("vin", "plate"))

        AlertDialog.Builder(this)
            .setTitle("بحث عن سيارة")
            .setView(query)
            .setPositiveButton("بحث") { _, _ ->
                val results = db.searchVehicles(query.text.toString())

                if (results.isEmpty()) {
                    toast("لم يتم العثور على سيارة")
                } else {
                    AlertDialog.Builder(this)
                        .setTitle("نتائج البحث")
                        .setItems(results.map {
                            "${it.make} ${it.model} — ${it.plate}\nVIN: ${it.vin}"
                        }.toTypedArray()) { _, index ->
                            showVehicleProfile(results[index].id)
                        }
                        .show()
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun showVehicleProfile(vehicleId: Long) {
        if (!allowed("vehicles", "view")) return
        val v = db.getVehicleProfile(vehicleId) ?: return
        val attachments = db.getVehicleAttachments(vehicleId)

        val message =
            "السيارة: ${v.make} ${v.model}\n" +
            "السنة: ${v.year}\n" +
            "المحرك: ${v.engine}\n" +
            "اللوحة: ${v.plate}\n" +
            "رقم الهيكل: ${v.vin}\n" +
            "العداد: ${v.odometer}\n\n" +
            "المالك الحالي: ${v.ownerName}\n" +
            "الهاتف: ${v.ownerPhone}\n\n" +
            "ملاحظات: ${v.notes}\n\n" +
            "المرفقات والصور: ${attachments.size}"

        val actions = mutableListOf<Pair<String, () -> Unit>>()
        if (db.hasPermission("customers", "view")) {
            actions += "فتح ملف العميل المالك" to { showCustomerProfile(v.customerId) }
        }
        if (db.hasPermission("vehicles", "view")) {
            actions += "عرض صور السيارة والعداد (${attachments.size})" to { showVehicleAttachments(vehicleId) }
        }
        if (db.hasPermission("vehicles", "edit") || db.hasPermission("vehicles", "add")) {
            actions += "إضافة صورة للسيارة / مؤشر الوقود" to { chooseVehicleAttachmentCategory(vehicleId) }
        }
        if (db.hasPermission("work_orders", "view")) {
            actions += "سجل زيارات السيارة" to { showVehicleVisits(vehicleId) }
        }
        actions += "سجل ملاك السيارة" to { showOwnerHistory(vehicleId) }
        if (db.hasPermission("work_orders", "add")) {
            actions += "زيارة جديدة" to { workOrderForm(v.customerId, vehicleId) }
        }
        AlertDialog.Builder(this).setTitle("ملف السيارة").setMessage(message)
            .setItems(actions.map { it.first }.toTypedArray()) { _, which -> actions[which].second.invoke() }
            .setPositiveButton("إغلاق", null).show()
    }

    private fun chooseVehicleAttachmentCategory(vehicleId: Long) {
        AlertDialog.Builder(this).setTitle("نوع الصورة")
            .setItems(arrayOf("صور السيارة العامة", "صورة مؤشر عداد البترول / الوقود")) { _, which ->
                pendingAttachmentVehicleId = vehicleId
                pendingAttachmentCategory = if (which == 1) "fuel_gauge" else "vehicle_photo"
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "image/*"
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                }
                try { startActivityForResult(intent, attachmentRequestCode) }
                catch (_: Exception) { toast("تعذر فتح معرض الصور") }
            }.setNegativeButton("إلغاء", null).show()
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != attachmentRequestCode || resultCode != RESULT_OK || data == null) return
        val vehicleId = pendingAttachmentVehicleId ?: return
        val uris = mutableListOf<Uri>()
        val clip = data.clipData
        if (clip != null) {
            for (i in 0 until clip.itemCount) uris += clip.getItemAt(i).uri
        } else {
            data.data?.let { uris.add(it) }
        }
        if (uris.isEmpty()) return
        var saved = 0
        uris.forEach { uri -> if (saveVehicleAttachment(vehicleId, uri, pendingAttachmentCategory)) saved++ }
        toast(if (saved > 0) "تم حفظ $saved صورة داخل ملف السيارة" else "تعذر حفظ الصور")
        pendingAttachmentVehicleId = null
        showVehicleAttachments(vehicleId)
    }

    private fun saveVehicleAttachment(vehicleId: Long, uri: Uri, category: String): Boolean {
        return try {
            val mime = contentResolver.getType(uri) ?: "image/jpeg"
            val displayName = runCatching {
                contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }
            }.getOrNull().orEmpty().ifBlank { "image_${System.currentTimeMillis()}.jpg" }
            val extension = displayName.substringAfterLast('.', "jpg").filter { it.isLetterOrDigit() }.ifBlank { "jpg" }
            val folder = File(filesDir, "vehicle_attachments/$vehicleId")
            if (!folder.exists() && !folder.mkdirs()) return false
            val file = File(folder, "${UUID.randomUUID()}.$extension")
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output -> input.copyTo(output) }
            } ?: return false
            val relative = "vehicle_attachments/$vehicleId/${file.name}"
            db.addVehicleAttachment(vehicleId, category, displayName, mime, relative)
            true
        } catch (_: Exception) { false }
    }

    private fun loadAttachmentBitmap(file: File): android.graphics.Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

    private fun showVehicleAttachments(vehicleId: Long) {
        val attachments = db.getVehicleAttachments(vehicleId)
        if (attachments.isEmpty()) {
            AlertDialog.Builder(this).setTitle("صور السيارة")
                .setMessage("لا توجد صور محفوظة لهذه السيارة بعد. يمكنك إضافة صور السيارة أو صورة مؤشر الوقود من ملف السيارة.")
                .setPositiveButton("إغلاق", null).show()
            return
        }
        val labels = attachments.map {
            val category = if (it.category == "fuel_gauge") "مؤشر الوقود" else "صور السيارة"
            "$category — ${it.fileName}"
        }.toTypedArray()
        AlertDialog.Builder(this).setTitle("صور السيارة ومؤشر الوقود")
            .setItems(labels) { _, index ->
                val file = File(filesDir, attachments[index].relativePath)
                if (!file.exists()) { toast("ملف الصورة غير موجود") ; return@setItems }
                val image = ImageView(this).apply {
                    setImageBitmap(loadAttachmentBitmap(file))
                    adjustViewBounds = true
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setPadding(dp(8), dp(8), dp(8), dp(8))
                }
                AlertDialog.Builder(this).setTitle(labels[index]).setView(ScrollView(this).apply { addView(image) })
                    .setPositiveButton("إغلاق", null).show()
            }.setNegativeButton("إغلاق", null).show()
    }

    private fun showCustomerProfile(customerId: Long) {
        if (!allowed("customers", "view")) return
        val c = db.getCustomerProfile(customerId) ?: return

        val customerVehicles = db.getCustomerVehicles(customerId)
        val vehicleSummary = if (customerVehicles.isEmpty()) {
            "لا توجد سيارات مرتبطة بهذا العميل"
        } else {
            customerVehicles.joinToString("\n") { vehicle ->
                "• ${vehicle.make} ${vehicle.model} — اللوحة: ${vehicle.plate.ifBlank { "غير مسجلة" }}"
            }
        }
        val message =
            "الاسم: ${c.name}\n" +
            "الهاتف: ${c.phone}\n" +
            "هاتف إضافي: ${c.alternatePhones}\n" +
            "العنوان: ${c.address}\n" +
            "ملاحظات: ${c.notes}\n\n" +
            "السيارات المرتبطة (${customerVehicles.size}):\n$vehicleSummary"

        val actions = mutableListOf<Pair<String, () -> Unit>>()
        if (db.hasPermission("vehicles", "view") && customerVehicles.isNotEmpty()) {
            actions += "فتح سيارة من سيارات العميل" to {
                showCustomerVehicles(customerId)
            }
        }
        if (db.hasPermission("work_orders", "view")) {
            actions += "سجل زيارات العميل" to {
                showOwnerHistoryForCustomer(customerId)
            }
        }
        if (actions.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("ملف العميل")
                .setMessage(message)
                .setPositiveButton("إغلاق", null)
                .show()
        } else {
            AlertDialog.Builder(this)
                .setTitle("ملف العميل")
                .setMessage(message)
                .setItems(actions.map { it.first }.toTypedArray()) { _, which ->
                    actions[which].second.invoke()
                }
                .setPositiveButton("إغلاق", null)
                .show()
        }
    }

    private fun showCustomerVehicles(customerId: Long) {
        if (!allowed("vehicles", "view")) return
        val vehicles = db.getCustomerVehicles(customerId)
        if (vehicles.isEmpty()) {
            toast("لا توجد سيارات مسجلة لهذا العميل")
            return
        }

        AlertDialog.Builder(this)
            .setTitle("سيارات العميل")
            .setItems(vehicles.map {
                "${it.make} ${it.model} — ${it.plate}"
            }.toTypedArray()) { _, index ->
                showVehicleProfile(vehicles[index].id)
            }
            .setNegativeButton("إغلاق", null)
            .show()
    }

    private fun showVehicleVisits(vehicleId: Long) {
        if (!allowed("work_orders", "view")) return
        val records = db.getVehicleWorkOrders(vehicleId)
        if (records.isEmpty()) {
            toast("لا توجد زيارات مسجلة")
            return
        }

        val lines = records.map {
            "التاريخ: ${it.createdAt}\n" +
                "العميل: ${it.customerName}\n" +
                "العداد: ${it.odometer}\n" +
                "الشكوى: ${it.complaint}\n" +
                "التشخيص: ${it.diagnosis}\n" +
                "الإصلاح: ${it.repairNotes}\n" +
                "الحالة: ${it.status}\n" +
                "الأجور: ${it.laborCost} — القطع: ${it.partsCost}"
        }

        AlertDialog.Builder(this)
            .setTitle("سجل زيارات السيارة")
            .setItems(lines.toTypedArray()) { _, index ->
                showMessage("تفاصيل الزيارة", lines[index])
            }
            .setNegativeButton("إغلاق", null)
            .show()
    }

    private fun showOwnerHistory(vehicleId: Long) {
        if (!allowed("vehicles", "view")) return
        val records = db.getVehicleOwnerHistory(vehicleId)
        if (records.isEmpty()) {
            showMessage("سجل الملكية", "لا توجد عمليات نقل ملكية مسجلة.")
            return
        }

        val message = records.joinToString("\n\n") {
            "من: ${it.oldOwner}\n" +
                "إلى: ${it.newOwner}\n" +
                "السبب: ${it.reason}\n" +
                "التاريخ: ${it.changedAt}"
        }

        showMessage("سجل ملاك السيارة", message)
    }

    private fun showOwnerHistoryForCustomer(customerId: Long) {
        if (!allowed("work_orders", "view")) return
        val records = db.getCustomerWorkOrders(customerId)
        if (records.isEmpty()) {
            showMessage("سجل زيارات العميل", "لا توجد زيارات مسجلة.")
            return
        }

        val lines = records.map {
            "السيارة: ${it.vehicleName}\n" +
                "التاريخ: ${it.createdAt}\n" +
                "الشكوى: ${it.complaint}\n" +
                "التشخيص: ${it.diagnosis}\n" +
                "الحالة: ${it.status}"
        }

        AlertDialog.Builder(this)
            .setTitle("سجل زيارات العميل")
            .setItems(lines.toTypedArray()) { _, index ->
                showMessage("تفاصيل الزيارة", lines[index])
            }
            .setNegativeButton("إغلاق", null)
            .show()
    }

    // ---------------------------------------------------------
    // السجلات العامة
    // ---------------------------------------------------------

    private fun recordsDialog(table: String, title: String) {
        val module = when (table) {
            "customers" -> "customers"
            "vehicles" -> "vehicles"
            "work_orders" -> "work_orders"
            "parts" -> "parts"
            else -> return
        }
        if (!allowed(module, "view")) return

        if (table == "parts") {
            val rows = db.recentRows(table)
            if (rows.isEmpty()) {
                toast("لا توجد سجلات")
                return
            }
            AlertDialog.Builder(this)
                .setTitle(title)
                .setItems(rows.toTypedArray()) { _, index ->
                    showMessage(title, rows[index])
                }
                .setNegativeButton("إغلاق", null)
                .show()
            return
        }

        val records = db.recentClickableRecords(table)
        if (records.isEmpty()) {
            toast("لا توجد سجلات")
            return
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(records.map { "${it.title}\n${it.subtitle}" }.toTypedArray()) { _, index ->
                val record = records[index]
                when (table) {
                    "customers" -> showCustomerProfile(record.id)
                    "vehicles" -> showVehicleProfile(record.id)
                    "work_orders" -> {
                        if (record.vehicleId > 0 && db.hasPermission("vehicles", "view")) {
                            showVehicleProfile(record.vehicleId)
                        } else {
                            showMessage("أمر الإصلاح #${record.id}", record.subtitle)
                        }
                    }
                }
            }
            .setNegativeButton("إغلاق", null)
            .show()
    }


/** Saves customer notes while preserving the existing four-argument WorkshopDb API. */
private fun WorkshopDb.addCustomer(
    name: String, phone: String, alt: String, address: String, notes: String
): Long {
    check(hasPermission("customers", "add")) { "ليس لديك صلاحية إضافة عميل" }
    val cleanName = name.trim()
    val cleanPhone = WorkshopDb.normalizePhone(phone)
    require(cleanName.isNotBlank()) { "يرجى إدخال اسم العميل" }
    require(cleanPhone.isNotBlank()) { "يرجى إدخال رقم هاتف صحيح" }
    val values = ContentValues().apply {
        put("name", cleanName)
        put("phone", cleanPhone)
        put("alternate_phones", alt.trim())
        put("address", address.trim())
        put("notes", notes.trim())
    }
    return writableDatabase.insertOrThrow("customers", null, values)
}

/** Adds vehicle notes and prevents a non-empty plate from being registered twice. */
private fun WorkshopDb.addVehicle(
    customerId: Long, make: String, model: String, year: String,
    engine: String, plate: String, vin: String, odometer: String, notes: String
): Long {
    check(hasPermission("vehicles", "add")) { "ليس لديك صلاحية إضافة سيارة" }
    val cleanPlate = plate.trim()
    val database = writableDatabase
    database.beginTransaction()
    try {
        if (cleanPlate.isNotBlank()) {
            database.rawQuery(
                "SELECT id FROM vehicles WHERE TRIM(plate)=? COLLATE NOCASE LIMIT 1",
                arrayOf(cleanPlate)
            ).use { cursor ->
                require(!cursor.moveToFirst()) { "رقم اللوحة مسجل مسبقاً لسيارة أخرى" }
            }
        }
        val id = addVehicle(customerId, make, model, year, engine, cleanPlate, vin, odometer)
        val values = ContentValues().apply { put("notes", notes.trim()) }
        database.update("vehicles", values, "id=?", arrayOf(id.toString()))
        database.setTransactionSuccessful()
        return id
    } finally {
        database.endTransaction()
    }
}

}
