
package com.khubara.workshop

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*

class MainActivity : Activity() {

    private lateinit var db: WorkshopDb
    private lateinit var root: LinearLayout
    private var loggedUser: AuthUser? = null
    private var activeFormDialog: AlertDialog? = null

    private val blue = Color.rgb(25, 48, 78)
    private val gold = Color.rgb(190, 145, 55)
    private val light = Color.rgb(245, 247, 250)
    private val dark = Color.rgb(35, 42, 52)

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
                            post {
                                if (hasFocus()) {
                                    showDropDown()
                                }
                            }
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

    private fun showDashboard() {
        val user = db.currentUser()
        if (user == null) {
            showLogin()
            return
        }
        loggedUser = user

        page(
            "خبراء هونداي وفورد",
            "مرحباً ${user.fullName} — ${user.role}"
        )

        addText("ملخص الورشة", 17f, blue, true)
        addText(
            "العملاء: ${db.count("customers")}     " +
                "السيارات: ${db.count("vehicles")}"
        )
        addText(
            "أوامر الإصلاح: ${db.count("work_orders")}     " +
                "قطع الغيار: ${db.count("parts")}"
        )

        addText("الاستقبال", 17f, gold, true)
        addAction("استقبال سيارة / البحث عن عميل أو سيارة") {
            if (allowed("vehicles", "view")) receptionScreen()
        }

        addText("إدارة الورشة", 17f, gold, true)

        if (db.hasPermission("customers", "add")) {
            addAction("إضافة عميل") { customerForm() }
        }
        if (db.hasPermission("vehicles", "add")) {
            addAction("إضافة سيارة") { vehicleForm() }
        }
        if (db.hasPermission("work_orders", "add")) {
            addAction("أمر إصلاح / زيارة جديدة") { workOrderForm() }
        }
        if (db.hasPermission("parts", "add")) {
            addAction("إضافة قطعة غيار") { partForm() }
        }

        if (db.hasPermission("customers", "view")) {
            addAction("بحث عن عميل") { customerSearchDialog() }
        }
        if (db.hasPermission("vehicles", "view")) {
            addAction("بحث عن سيارة") { vehicleSearchDialog() }
        }

        addText("السجلات", 17f, gold, true)
        if (db.hasPermission("customers", "view")) {
            addAction("سجل العملاء") { recordsDialog("customers", "العملاء") }
        }
        if (db.hasPermission("vehicles", "view")) {
            addAction("سجل السيارات") { recordsDialog("vehicles", "السيارات") }
        }
        if (db.hasPermission("work_orders", "view")) {
            addAction("سجل أوامر الإصلاح") {
                recordsDialog("work_orders", "أوامر الإصلاح")
            }
        }
        if (db.hasPermission("parts", "view")) {
            addAction("سجل قطع الغيار") {
                recordsDialog("parts", "قطع الغيار")
            }
        }

        if (user.role == "مدير") {
            addText("إدارة النظام", 17f, gold, true)
            addAction("إدارة المستخدمين والصلاحيات") {
                usersScreen()
            }
        }

        addAction("تسجيل الخروج", { logout() }, Color.rgb(150, 55, 55))
        addText("الإصدار 0.6.0", 12f, Color.GRAY)
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
                    address.text.toString()
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

        val views = mutableListOf<View>()
        if (customerId == null) {
            views.add(TextView(this).apply { text = "العميل" })
            views.add(customer)
        }
        views.addAll(listOf(make, model, year, engine, plate, vin, odometer))

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
                    odometer.text.toString()
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
            addAction(label) {
                if (user.role == "مدير") {
                    showMessage("حساب المدير", "لا يمكن تعديل صلاحيات حساب المدير.")
                } else {
                    permissionsScreen(user)
                }
            }
        }

        addAction("رجوع", { showDashboard() })
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

        if (cleanPhone.isBlank() || cleanIdentifier.isBlank()) {
            toast("أدخل رقم الهاتف ورقم الهيكل أو اللوحة")
            return
        }

        val customers = db.searchReception(cleanPhone)
            .filter { it.type == "customer" }
            .distinctBy { it.id }

        val vehicles = db.searchReceptionDetailed(cleanIdentifier)
            .mapNotNull { it.vehicle }
            .distinctBy { it.id }

        if (customers.isEmpty() && vehicles.isEmpty()) {
            newCustomerAndVehicleForm(cleanPhone, cleanIdentifier)
            return
        }

        fun continueWith(customerId: Long?, vehicleId: Long?) {
            when {
                customerId != null && vehicleId != null -> {
                    val customer = db.getCustomerProfile(customerId)
                    val vehicle = db.getVehicleProfile(vehicleId)
                    if (customer == null || vehicle == null) {
                        toast("تعذر تحميل بيانات العميل أو السيارة")
                    } else if (vehicle.customerId == customer.id) {
                        showMessage(
                            "تم التحقق من الملكية",
                            "العميل: ${customer.name}\n" +
                                "السيارة: ${vehicle.make} ${vehicle.model}\n" +
                                "اللوحة: ${vehicle.plate}\n\n" +
                                "البيانات متطابقة."
                        )
                        receptionChoices(customer.id, vehicle.id)
                    } else {
                        ownershipConflict(customer.id, vehicle.id)
                    }
                }
                customerId != null -> {
                    val customer = db.getCustomerProfile(customerId) ?: return
                    val customerVehicles = db.getCustomerVehicles(customerId)
                    if (customerVehicles.isNotEmpty()) {
                        showCustomerVehicles(customerId)
                    } else {
                        AlertDialog.Builder(this)
                            .setTitle("العميل موجود")
                            .setMessage("لم نعثر على سيارة مطابقة. هل تريد تسجيل سيارة جديدة لهذا العميل؟")
                            .setPositiveButton("إضافة سيارة") { _, _ -> vehicleForm(customer.id) }
                            .setNegativeButton("إلغاء", null)
                            .show()
                    }
                }
                vehicleId != null -> {
                    val vehicle = db.getVehicleProfile(vehicleId) ?: return
                    showMessage(
                        "السيارة موجودة",
                        "السيارة مسجلة باسم: ${vehicle.ownerName}\n" +
                            "هاتف المالك الحالي: ${vehicle.ownerPhone}\n\n" +
                            "لم نعثر على عميل مطابق برقم الهاتف المدخل."
                    )
                    newCustomerForExistingVehicle(vehicle.id)
                }
            }
        }

        fun chooseVehicle(customerId: Long?) {
            when {
                vehicles.isEmpty() -> continueWith(customerId, null)
                vehicles.size == 1 -> continueWith(customerId, vehicles.first().id)
                else -> AlertDialog.Builder(this)
                    .setTitle("اختر السيارة المطابقة")
                    .setItems(vehicles.map {
                        "${it.make} ${it.model} — ${it.plate}\nVIN: ${it.vin}"
                    }.toTypedArray()) { _, index ->
                        continueWith(customerId, vehicles[index].id)
                    }
                    .setNegativeButton("إلغاء", null)
                    .show()
            }
        }

        when {
            customers.isEmpty() -> chooseVehicle(null)
            customers.size == 1 -> chooseVehicle(customers.first().id)
            else -> AlertDialog.Builder(this)
                .setTitle("اختر العميل المطابق")
                .setItems(customers.map {
                    "${it.title}\n${it.subtitle}"
                }.toTypedArray()) { _, index ->
                    chooseVehicle(customers[index].id)
                }
                .setNegativeButton("إلغاء", null)
                .show()
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
        val make = field("الشركة / الصانع")
        val model = field("طراز السيارة")
        val year = field("سنة الصنع", "number")
        val engine = field("نوع المحرك")
        val plate = field("رقم اللوحة", suggestionKeys = listOf("plate"))
        val vin = field("رقم الهيكل VIN", suggestionKeys = listOf("vin"))
        val odometer = field("قراءة العداد", "number")

        if (identifierValue.length == 17) vin.setText(identifierValue)
        else plate.setText(identifierValue)

        formDialog(
            "عميل وسيارة جديدة",
            listOf(
                name, phone, alt, address,
                make, model, year, engine, plate, vin, odometer
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
                odometer.text.toString()
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

    private fun customerSearchDialog() {
        if (!allowed("customers", "view")) return
        val query = field("رقم هاتف العميل أو اسمه", suggestionKeys = listOf("customer_name", "customer_phone"))

        AlertDialog.Builder(this)
            .setTitle("بحث عن عميل")
            .setView(query)
            .setPositiveButton("بحث") { _, _ ->
                val results = db.searchReception(query.text.toString())
                    .filter { it.type == "customer" }

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

        val message =
            "السيارة: ${v.make} ${v.model}\n" +
            "السنة: ${v.year}\n" +
            "المحرك: ${v.engine}\n" +
            "اللوحة: ${v.plate}\n" +
            "رقم الهيكل: ${v.vin}\n" +
            "العداد: ${v.odometer}\n\n" +
            "المالك الحالي: ${v.ownerName}\n" +
            "الهاتف: ${v.ownerPhone}\n\n" +
            "ملاحظات: ${v.notes}"

        AlertDialog.Builder(this)
            .setTitle("ملف السيارة")
            .setMessage(message)
            .setItems(
                arrayOf(
                    "سجل زيارات السيارة",
                    "سجل ملاك السيارة",
                    "زيارة جديدة"
                )
            ) { _, which ->
                when (which) {
                    0 -> showVehicleVisits(vehicleId)
                    1 -> showOwnerHistory(vehicleId)
                    2 -> workOrderForm(v.customerId, vehicleId)
                }
            }
            .setPositiveButton("إغلاق", null)
            .show()
    }

    private fun showCustomerProfile(customerId: Long) {
        if (!allowed("customers", "view")) return
        val c = db.getCustomerProfile(customerId) ?: return

        val message =
            "الاسم: ${c.name}\n" +
            "الهاتف: ${c.phone}\n" +
            "هاتف إضافي: ${c.alternatePhones}\n" +
            "العنوان: ${c.address}\n" +
            "ملاحظات: ${c.notes}"

        val vehicles = db.getCustomerVehicles(customerId)
        val vehicleSummary = if (vehicles.isEmpty()) {
            "\n\nالسيارات المسجلة: لا توجد سيارات"
        } else {
            "\n\nالسيارات المسجلة: ${vehicles.size}"
        }

        AlertDialog.Builder(this)
            .setTitle("ملف العميل")
            .setMessage(message + vehicleSummary)
            .setItems(
                arrayOf("عرض سيارات العميل", "سجل زيارات العميل")
            ) { _, which ->
                when (which) {
                    0 -> showCustomerVehicles(customerId)
                    1 -> showOwnerHistoryForCustomer(customerId)
                }
            }
            .setPositiveButton("إغلاق", null)
            .show()
    }

    private fun showCustomerVehicles(customerId: Long) {
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

        val records = db.recentRows(table)
        if (records.isEmpty()) {
            toast("لا توجد سجلات")
            return
        }

        AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(records.toTypedArray()) { _, index ->
                when (table) {
                    "customers" -> {
                        val users = db.listUsers()
                        // يعرض السجل النصي؛ البحث التفصيلي متاح من زر البحث.
                        showMessage(title, records[index])
                    }
                    "vehicles" -> {
                        val vehicles = db.searchVehicles(
                            records[index].substringAfter("\n")
                        )
                        if (vehicles.isNotEmpty()) {
                            showVehicleProfile(vehicles.first().id)
                        } else {
                            showMessage(title, records[index])
                        }
                    }
                    else -> showMessage(title, records[index])
                }
            }
            .setNegativeButton("إغلاق", null)
            .show()
    }
}
