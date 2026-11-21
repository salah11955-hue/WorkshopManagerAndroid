package com.khubara.workshop

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class MainActivity : Activity() {

    private lateinit var db: WorkshopDb
    private lateinit var root: LinearLayout
    private lateinit var contentHolder: LinearLayout
    private var loggedUser: AuthUser? = null
    private var activeFormDialog: AlertDialog? = null
    private var currentTab: String = "home"
    private var pendingPhotoVehicleId: Long = -1L
    private val PICK_PHOTO = 1001

    // ===== Modern Palette =====
    private val primary = Color.rgb(15, 23, 42)      // Slate 900 - كحلي داكن
    private val primaryLight = Color.rgb(30, 41, 59)
    private val teal = Color.rgb(14, 165, 165)       // تركوازي
    private val tealDark = Color.rgb(13, 148, 136)
    private val gold = Color.rgb(245, 158, 11)       // ذهبي
    private val bg = Color.rgb(241, 245, 249)         // خلفية فاتحة
    private val cardBg = Color.WHITE
    private val dark = Color.rgb(30, 41, 59)
    private val muted = Color.rgb(100, 116, 139)
    private val success = Color.rgb(22, 163, 74)
    private val danger = Color.rgb(220, 38, 38)
    private val warning = Color.rgb(245, 158, 11)
    private val border = Color.rgb(226, 232, 240)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = WorkshopDb(this)
        if (db.hasUsers()) showLogin() else showCreateAdmin()
    }

    override fun onDestroy() { super.onDestroy(); try { db.close() } catch (e: Exception) {} }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_PHOTO && resultCode == RESULT_OK && data?.data != null && pendingPhotoVehicleId > 0) {
            val uri = data.data.toString()
            try {
                contentResolver.takePersistableUriPermission(data.data!!, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {}
            db.addVehiclePhoto(pendingPhotoVehicleId, uri, "صورة السيارة")
            toast("تمت إضافة الصورة")
            showVehicleProfile(pendingPhotoVehicleId)
        }
    }

    // ==================== Core UI Helpers ====================
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun matchWidth(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    private fun wrap(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    private fun rounded(color: Int, radius: Int = 16, stroke: Int = 0, strokeColor: Int = Color.TRANSPARENT): GradientDrawable {
        return GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat(); if (stroke > 0) setStroke(dp(stroke), strokeColor) }
    }

    private fun shell(title: String, subtitle: String = "") {
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        // Header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(primary)
            setPadding(dp(20), dp(18), dp(20), dp(14))
        }
        val topRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val titleView = TextView(this).apply {
            text = "ورشة خبارة"; textSize = 13f; setTextColor(Color.rgb(148, 163, 184)); typeface = Typeface.DEFAULT_BOLD
        }
        val userView = TextView(this).apply {
            text = loggedUser?.fullName ?: ""; textSize = 12f; setTextColor(Color.WHITE)
            setPadding(dp(8), dp(4), dp(8), dp(4)); background = rounded(Color.rgb(51, 65, 85), 20)
        }
        topRow.addView(titleView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        topRow.addView(userView)
        header.addView(topRow)
        val h = TextView(this).apply { text = title; textSize = 22f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD; setPadding(0, dp(8), 0, 0) }
        header.addView(h)
        if (subtitle.isNotBlank()) {
            val s = TextView(this).apply { text = subtitle; textSize = 13f; setTextColor(Color.rgb(203, 213, 225)); setPadding(0, dp(4), 0, 0) }
            header.addView(s)
        }
        outer.addView(header, matchWidth())

        val scroll = ScrollView(this).apply { setBackgroundColor(bg) }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(100))
            setBackgroundColor(bg)
        }
        contentHolder = root
        scroll.addView(root)
        outer.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        // Bottom Nav
        outer.addView(buildBottomNav(), matchWidth())
        setContentView(outer)
    }

    private fun buildBottomNav(): LinearLayout {
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.WHITE)
            setPadding(dp(8), dp(10), dp(8), dp(10))
            elevation = dp(8).toFloat()
        }
        val items = listOf("الرئيسية" to "home", "العملاء" to "customers", "السيارات" to "vehicles", "أوامر" to "orders", "المخزون" to "parts", "المزيد" to "more")
        for ((label, key) in items) {
            val isActive = currentTab == key
            val btn = TextView(this).apply {
                text = label; textSize = 12f; gravity = Gravity.CENTER
                setTextColor(if (isActive) tealDark else muted)
                typeface = if (isActive) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setPadding(dp(6), dp(8), dp(6), dp(8))
                background = if (isActive) rounded(Color.rgb(204, 251, 241), 20) else null
                setOnClickListener {
                    currentTab = key
                    when (key) {
                        "home" -> showDashboard()
                        "customers" -> showCustomersPage()
                        "vehicles" -> showVehiclesPage()
                        "orders" -> showWorkOrdersPage()
                        "parts" -> showPartsPage()
                        "more" -> showMorePage()
                    }
                }
            }
            nav.addView(btn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        return nav
    }

    private fun card(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(cardBg)
            background = rounded(cardBg, 18, 1, border)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            elevation = dp(2).toFloat()
        }
    }

    private fun addCard(block: LinearLayout.() -> Unit) {
        val c = card().apply(block)
        root.addView(c, matchWidth().apply { setMargins(0, 0, 0, dp(12)) })
    }

    private fun sectionTitle(text: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val t = TextView(this).apply { this.text = text; textSize = 17f; setTextColor(primary); typeface = Typeface.DEFAULT_BOLD }
        row.addView(t, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (actionLabel != null && onAction != null) {
            val b = TextView(this).apply {
                this.text = actionLabel; textSize = 13f; setTextColor(tealDark); typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(10), dp(6), dp(10), dp(6)); background = rounded(Color.rgb(204, 251, 241), 20)
                setOnClickListener { onAction() }
            }
            row.addView(b)
        }
        root.addView(row, matchWidth().apply { setMargins(0, dp(8), 0, dp(10)) })
    }

    private fun addText(text: String, size: Float = 15f, color: Int = dark, bold: Boolean = false) {
        val view = TextView(this).apply {
            this.text = text; textSize = size; setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(2), dp(4), dp(2), dp(4))
        }
        root.addView(view, matchWidth())
    }

    private fun addMuted(text: String) { addText(text, 13f, muted) }

    private fun addAction(label: String, action: () -> Unit, color: Int = tealDark) {
        val button = Button(this).apply {
            text = label; textSize = 15f; isAllCaps = false; setTextColor(Color.WHITE)
            background = rounded(color, 14); setPadding(dp(10), dp(12), dp(10), dp(12))
            elevation = dp(2).toFloat(); setOnClickListener { action() }
        }
        root.addView(button, matchWidth().apply { setMargins(0, dp(4), 0, dp(4)) })
    }

    private fun addOutlineAction(label: String, action: () -> Unit) {
        val button = Button(this).apply {
            text = label; textSize = 14f; isAllCaps = false; setTextColor(primary)
            background = rounded(Color.WHITE, 14, 1, border); setPadding(dp(10), dp(11), dp(10), dp(11))
            setOnClickListener { action() }
        }
        root.addView(button, matchWidth().apply { setMargins(0, dp(4), 0, dp(4)) })
    }

    private fun addDangerAction(label: String, action: () -> Unit) { addAction(label, action, danger) }

    private fun fieldLabel(text: String): TextView {
        return TextView(this).apply { this.text = text; textSize = 13f; setTextColor(dark); typeface = Typeface.DEFAULT_BOLD; setPadding(dp(4), dp(10), dp(4), dp(4)) }
    }

    private fun field(hint: String, type: String = "text", suggestionKeys: List<String> = emptyList()): AutoCompleteTextView {
        return AutoCompleteTextView(this).apply {
            this.hint = hint; textSize = 15f; setTextColor(dark); setHintTextColor(Color.rgb(148, 163, 184))
            setSingleLine(type != "multiline")
            inputType = when (type) {
                "phone" -> InputType.TYPE_CLASS_PHONE
                "number" -> InputType.TYPE_CLASS_NUMBER
                "decimal" -> InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                "password" -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                "multiline" -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                else -> InputType.TYPE_CLASS_TEXT
            }
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(Color.WHITE, 12, 1, border)
            if (suggestionKeys.isNotEmpty() && type != "password" && type != "number" && type != "decimal" && type != "multiline") {
                threshold = 1
                val adapter = ArrayAdapter<String>(this@MainActivity, android.R.layout.simple_dropdown_item_1line, mutableListOf())
                setAdapter(adapter); setDropDownVerticalOffset(dp(4))
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(t: CharSequence?, s: Int, c: Int, a: Int) = Unit
                    override fun onTextChanged(t: CharSequence?, s: Int, b: Int, c: Int) = Unit
                    override fun afterTextChanged(e: Editable?) {
                        val prefix = e?.toString().orEmpty().trim()
                        if (prefix.isBlank()) { adapter.clear(); dismissDropDown(); return }
                        val matches = suggestionKeys.filter { it.contains(prefix, ignoreCase = true) }.take(10)
                        adapter.clear(); adapter.addAll(matches); if (matches.isNotEmpty()) showDropDown() else dismissDropDown()
                    }
                })
            }
        }
    }

    private fun addFieldTo(parent: LinearLayout, label: String, hint: String, type: String = "text", suggestionKeys: List<String> = emptyList()): AutoCompleteTextView {
        parent.addView(fieldLabel(label))
        val f = field(hint, type, suggestionKeys)
        parent.addView(f, matchWidth())
        return f
    }

    private fun spinner(options: List<String>, selected: String = ""): Spinner {
        val sp = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, options)
            background = rounded(Color.WHITE, 12, 1, border)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        if (selected.isNotBlank()) { val idx = options.indexOf(selected); if (idx >= 0) sp.setSelection(idx) }
        return sp
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    private fun showMessage(title: String, msg: String) {
        AlertDialog.Builder(this).setTitle(title).setMessage(msg).setPositiveButton("حسناً", null).show()
    }
    private fun allowed(module: String, action: String): Boolean {
        if (!db.hasPermission(module, action)) { toast("ليس لديك صلاحية"); return false }
        return true
    }
    private fun confirm(title: String, msg: String, onYes: () -> Unit) {
        AlertDialog.Builder(this).setTitle(title).setMessage(msg).setPositiveButton("نعم") { _, _ -> onYes() }.setNegativeButton("إلغاء", null).show()
    }

    private fun statCard(title: String, value: String, sub: String, color: Int): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE); background = rounded(Color.WHITE, 16, 1, border)
            setPadding(dp(14), dp(14), dp(14), dp(14)); elevation = dp(1).toFloat()
            val v = TextView(this@MainActivity).apply { text = value; textSize = 20f; setTextColor(color); typeface = Typeface.DEFAULT_BOLD }
            val t = TextView(this@MainActivity).apply { text = title; textSize = 13f; setTextColor(dark); typeface = Typeface.DEFAULT_BOLD }
            val s = TextView(this@MainActivity).apply { text = sub; textSize = 11f; setTextColor(muted) }
            addView(v); addView(t); addView(s)
        }
    }

    private fun addStatGrid(cards: List<LinearLayout>) {
        for (i in cards.indices step 2) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(cards[i], LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) })
            if (i + 1 < cards.size) row.addView(cards[i+1], LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(8) })
            else row.addView(View(this), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            root.addView(row, matchWidth().apply { setMargins(0, 0, 0, dp(10)) })
        }
    }

    private fun addRowItem(title: String, subtitle: String, badge: String? = null, badgeColor: Int = tealDark, onClick: () -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.WHITE); background = rounded(Color.WHITE, 14, 1, border)
            setPadding(dp(14), dp(12), dp(14), dp(12)); isClickable = true; setOnClickListener { onClick() }
        }
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val t = TextView(this).apply { text = title; textSize = 15f; setTextColor(dark); typeface = Typeface.DEFAULT_BOLD }
        val s = TextView(this).apply { text = subtitle; textSize = 12f; setTextColor(muted) }
        texts.addView(t); texts.addView(s)
        row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (badge != null) {
            val b = TextView(this).apply { text = badge; textSize = 11f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD; setPadding(dp(10), dp(6), dp(10), dp(6)); background = rounded(badgeColor, 20); gravity = Gravity.CENTER }
            row.addView(b)
        }
        root.addView(row, matchWidth().apply { setMargins(0, 0, 0, dp(8)) })
    }


    // ==================== Auth ====================
    private fun showLogin() {
        val outer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(primary); layoutDirection = View.LAYOUT_DIRECTION_RTL; setPadding(dp(24), dp(48), dp(24), dp(24)) }
        val logo = TextView(this).apply { text = "🔧 ورشة خبارة"; textSize = 28f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER }
        val sub = TextView(this).apply { text = "إدارة الورشة باحتراف • عملاء • سيارات • مخزون • فواتير"; textSize = 13f; setTextColor(Color.rgb(203,213,225)); gravity = Gravity.CENTER; setPadding(0, dp(8), 0, dp(28)) }
        outer.addView(logo, matchWidth()); outer.addView(sub, matchWidth())
        val cardBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.WHITE); background = rounded(Color.WHITE, 22); setPadding(dp(20), dp(22), dp(20), dp(22)) }
        val title = TextView(this).apply { text = "تسجيل الدخول"; textSize = 20f; setTextColor(primary); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER }
        cardBox.addView(title, matchWidth())
        val userF = field("اسم المستخدم"); val passF = field("كلمة المرور", "password")
        cardBox.addView(fieldLabel("اسم المستخدم")); cardBox.addView(userF, matchWidth())
        cardBox.addView(fieldLabel("كلمة المرور")); cardBox.addView(passF, matchWidth())
        val btn = Button(this).apply { text = "دخول"; textSize = 16f; setTextColor(Color.WHITE); background = rounded(tealDark, 14); setPadding(dp(10), dp(14), dp(10), dp(14)); setOnClickListener {
            val u = userF.text.toString().trim(); val p = passF.text.toString()
            if (u.isBlank() || p.isBlank()) { toast("أدخل اسم المستخدم وكلمة المرور"); return@setOnClickListener }
            val user = db.authenticate(u, p)
            if (user == null) toast("بيانات الدخول غير صحيحة") else { loggedUser = user; db.setActiveUser(user.id); showDashboard() }
        } }
        cardBox.addView(btn, matchWidth().apply { setMargins(0, dp(16), 0, 0) })
        outer.addView(cardBox, matchWidth())
        val scroll = ScrollView(this); scroll.addView(outer); setContentView(scroll)
    }

    private fun showCreateAdmin() {
        val outer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(primary); layoutDirection = View.LAYOUT_DIRECTION_RTL; setPadding(dp(24), dp(40), dp(24), dp(24)) }
        val t = TextView(this).apply { text = "مرحباً بك في ورشة خبارة"; textSize = 24f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER }
        val s = TextView(this).apply { text = "أنشئ حساب المدير للبدء"; textSize = 14f; setTextColor(Color.rgb(203,213,225)); gravity = Gravity.CENTER; setPadding(0, dp(8), 0, dp(24)) }
        outer.addView(t, matchWidth()); outer.addView(s, matchWidth())
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.WHITE); background = rounded(Color.WHITE, 22); setPadding(dp(20), dp(20), dp(20), dp(20)) }
        val nameF = field("الاسم الكامل"); val userF = field("اسم المستخدم"); val passF = field("كلمة مرور قوية", "password")
        box.addView(fieldLabel("الاسم الكامل")); box.addView(nameF, matchWidth())
        box.addView(fieldLabel("اسم المستخدم")); box.addView(userF, matchWidth())
        box.addView(fieldLabel("كلمة المرور")); box.addView(passF, matchWidth())
        val btn = Button(this).apply { text = "إنشاء حساب المدير"; setTextColor(Color.WHITE); background = rounded(tealDark, 14); setPadding(dp(10), dp(14), dp(10), dp(14)); setOnClickListener {
            if (nameF.text.toString().isBlank() || userF.text.toString().isBlank() || passF.text.toString().length < 4) { toast("أكمل البيانات - كلمة المرور 4 أحرف على الأقل"); return@setOnClickListener }
            val id = db.createUser(userF.text.toString(), passF.text.toString(), nameF.text.toString(), "مدير")
            val user = db.authenticate(userF.text.toString(), passF.text.toString())
            loggedUser = user; db.setActiveUser(id); toast("تم إنشاء حساب المدير"); showDashboard()
        } }
        box.addView(btn, matchWidth().apply { setMargins(0, dp(16), 0, 0) })
        outer.addView(box, matchWidth())
        val scroll = ScrollView(this); scroll.addView(outer); setContentView(scroll)
    }

    private fun logout() { loggedUser = null; db.setActiveUser(null); showLogin() }

    // ==================== Dashboard ====================
    private fun showDashboard() {
        currentTab = "home"
        shell("أهلاً، " + (loggedUser?.fullName ?: ""), "إليك نظرة سريعة على ورشتك اليوم")
        val stats = db.getDashboardStats()
        sectionTitle("نظرة سريعة")
        addStatGrid(listOf(
            statCard("إيراد اليوم", String.format("%.0f", stats.revenueToday) + " ر.ي", "مدفوعات اليوم", success),
            statCard("أوامر مفتوحة", stats.openOrders.toString(), "تحتاج متابعة", warning),
            statCard("إجمالي الديون", String.format("%.0f", stats.totalDebt) + " ر.ي", "مبالغ متبقية", danger),
            statCard("تنبيه مخزون", stats.lowStockCount.toString(), "قطع وصلت للحد الأدنى", gold)
        ))
        // Alerts
        val low = db.getLowStockParts()
        if (low.isNotEmpty()) {
            addCard {
                val t = TextView(this@MainActivity).apply { text = "⚠️ تنبيه مخزون - " + low.size + " قطع تحتاج إعادة طلب"; textSize = 15f; setTextColor(danger); typeface = Typeface.DEFAULT_BOLD }
                addView(t)
                low.take(3).forEach { p ->
                    val line = TextView(this@MainActivity).apply { text = "• " + p.name + " - المتبقي: " + p.quantity + " " + p.unit + " (الحد: " + p.minQuantity + ")"; textSize = 13f; setTextColor(dark); setPadding(0, dp(4), 0, 0) }
                    addView(line)
                }
                val b = Button(this@MainActivity).apply { text = "عرض كل التنبيهات"; setTextColor(Color.WHITE); background = rounded(danger, 12); setOnClickListener { showPartsPage() } }
                addView(b, matchWidth().apply { setMargins(0, dp(10), 0, 0) })
            }
        }
        sectionTitle("إجراءات سريعة")
        // Quick actions grid 2 columns
        val quick = listOf(
            Triple("➕ عميل جديد", tealDark) { showAddCustomer() },
            Triple("🚗 سيارة جديدة", primary) { showAddVehicle() },
            Triple("🛠️ أمر شغل جديد", gold) { showAddWorkOrder() },
            Triple("📦 إضافة قطعة", success) { showAddPart() }
        )
        for (i in quick.indices step 2) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (j in 0..1) {
                if (i + j >= quick.size) break
                val (label, col, act) = quick[i + j]
                val btn = Button(this).apply { text = label; setTextColor(Color.WHITE); background = rounded(col, 14); setPadding(dp(8), dp(14), dp(8), dp(14)); setOnClickListener { act() } }
                row.addView(btn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(4); marginEnd = dp(4) })
            }
            root.addView(row, matchWidth().apply { setMargins(0, 0, 0, dp(8)) })
        }
        sectionTitle("بحث سريع", "بحث متقدم") { showAdvancedSearch() }
        addCard {
            val f = field("ابحث بالاسم، الهاتف، اللوحة، VIN...")
            addView(fieldLabel("بحث فوري")); addView(f, matchWidth())
            val btn = Button(this@MainActivity).apply { text = "بحث"; setTextColor(Color.WHITE); background = rounded(primary, 12); setOnClickListener { if (f.text.toString().isBlank()) toast("اكتب كلمة بحث") else showSearchResults(f.text.toString()) } }
            addView(btn, matchWidth().apply { setMargins(0, dp(10), 0, 0) })
        }
        sectionTitle("أحدث أوامر الشغل")
        val orders = db.getAllWorkOrders().take(5)
        if (orders.isEmpty()) addMuted("لا توجد أوامر بعد - ابدأ بإضافة أول أمر شغل")
        else orders.forEach { o ->
            val badge = when (o.status) { "مكتمل" -> "مكتمل"; "مفتوح" -> "مفتوح"; else -> o.status }
            val bc = if (o.status == "مكتمل") success else if (o.status == "مفتوح") warning else tealDark
            addRowItem("أمر #" + o.id + " - " + o.customerName, o.complaint + " • " + o.createdAt + " • " + (o.laborCost + o.partsCost - o.discount) + " ر.ي", badge, bc) { showWorkOrderDetails(o.id) }
        }
        sectionTitle("أحدث العملاء")
        db.getAllCustomers().take(4).forEach { c ->
            addRowItem(c.name, c.phone + " • " + c.city + " • " + c.type) { showCustomerProfile(c.id) }
        }
        addOutlineAction("📊 تقرير اليوم") { showReportsPage() }
        addOutlineAction("💾 نسخ احتياطي") { exportBackup() }
        addOutlineAction("🚪 تسجيل الخروج") { logout() }
    }

    // ==================== Customers ====================
    private fun showCustomersPage() {
        currentTab = "customers"
        shell("العملاء", "إدارة عملاء الورشة وديونهم")
        addAction("➕ إضافة عميل جديد") { showAddCustomer() }
        addAction("🔍 بحث متقدم") { showAdvancedSearch() }
        sectionTitle("كل العملاء")
        val list = db.getAllCustomers()
        if (list.isEmpty()) addMuted("لا يوجد عملاء بعد")
        else list.forEach { c ->
            val debtTxt = if (c.debt > 0) " • دين: " + c.debt + " ر.ي" else ""
            addRowItem(c.name, c.phone + " • " + c.city + " • " + c.type + debtTxt) { showCustomerProfile(c.id) }
        }
    }

    private fun showAddCustomer() {
        if (!allowed("customers", "add")) return
        shell("عميل جديد", "أدخل بيانات العميل كاملة")
        val cardBox = card(); root.addView(cardBox, matchWidth())
        val nameF = addFieldTo(cardBox, "الاسم الكامل *", "مثال: محمد أحمد")
        val phoneF = addFieldTo(cardBox, "رقم الهاتف *", "77xxxxxxx", "phone")
        val altF = addFieldTo(cardBox, "هاتف بديل", "اختياري", "phone")
        val emailF = addFieldTo(cardBox, "البريد الإلكتروني", "example@email.com")
        val cityF = addFieldTo(cardBox, "المدينة", "مثال: صنعاء / إب")
        cardBox.addView(fieldLabel("نوع العميل"))
        val typeSp = spinner(listOf("فرد", "شركة", "تاجر", "جهة حكومية")); cardBox.addView(typeSp, matchWidth())
        val addrF = addFieldTo(cardBox, "العنوان", "الحي - الشارع")
        val notesF = addFieldTo(cardBox, "ملاحظات", "ملاحظات عن العميل", "multiline")
        // Live duplicate check
        phoneF.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(e: Editable?) {
                val existing = db.findCustomerByPhone(e.toString())
                if (existing != null) toast("تنبيه: هذا الرقم مسجل باسم " + existing)
            }
        })
        addAction("حفظ العميل") {
            try {
                val id = db.addCustomer(nameF.text.toString(), phoneF.text.toString(), altF.text.toString(), addrF.text.toString(), notesF.text.toString(), emailF.text.toString(), cityF.text.toString(), typeSp.selectedItem.toString())
                toast("تم حفظ العميل"); showCustomerProfile(id)
            } catch (e: Exception) { toast(e.message ?: "خطأ في الحفظ") }
        }
        addOutlineAction("إلغاء") { showCustomersPage() }
    }

    private fun showCustomerProfile(customerId: Long) {
        val c = db.getCustomerProfile(customerId) ?: run { toast("العميل غير موجود"); return }
        shell(c.name, c.type + " • " + c.city + " • " + c.phone)
        addCard {
            val t = TextView(this@MainActivity).apply { text = "بيانات العميل"; textSize = 16f; setTextColor(primary); typeface = Typeface.DEFAULT_BOLD }
            addView(t)
            val lines = listOf("📞 الهاتف: " + c.phone, "📱 بديل: " + c.alternatePhones, "✉️ إيميل: " + c.email, "📍 العنوان: " + c.address, "🏙️ المدينة: " + c.city, "📝 ملاحظات: " + c.notes)
            lines.forEach { line -> val tv = TextView(this@MainActivity).apply { text = line; textSize = 14f; setTextColor(dark); setPadding(0, dp(4), 0, 0) }; addView(tv) }
            val debtView = TextView(this@MainActivity).apply { text = "💰 إجمالي الدين: " + String.format("%.0f", c.debt) + " ر.ي"; textSize = 15f; setTextColor(if (c.debt > 0) danger else success); typeface = Typeface.DEFAULT_BOLD; setPadding(0, dp(10), 0, 0) }
            addView(debtView)
        }
        addAction("🚗 إضافة سيارة لهذا العميل") { showAddVehicle(customerId) }
        addAction("🛠️ أمر شغل جديد") { showAddWorkOrder(customerId) }
        sectionTitle("سيارات العميل")
        val vehicles = db.getAllVehicles().filter { it.customerId == customerId }
        if (vehicles.isEmpty()) addMuted("لا توجد سيارات مسجلة")
        else vehicles.forEach { v -> addRowItem(v.make + " " + v.model + " (" + v.year + ")", "لوحة: " + v.plate + " • VIN: " + v.vin + " • " + v.color) { showVehicleProfile(v.id) } }
        sectionTitle("سجل زيارات العميل")
        val orders = db.getCustomerWorkOrders(customerId)
        if (orders.isEmpty()) addMuted("لا توجد زيارات")
        else orders.take(10).forEach { o -> addRowItem("أمر #" + o.id + " - " + o.vehicleName, o.complaint + " • " + o.status + " • " + (o.laborCost + o.partsCost - o.discount) + " ر.ي") { showWorkOrderDetails(o.id) } }
        if (allowed("customers", "edit")) addOutlineAction("✏️ تعديل بيانات العميل") { showEditCustomer(c) }
        addOutlineAction("⬅️ عودة للعملاء") { showCustomersPage() }
    }

    private fun showEditCustomer(c: CustomerProfile) {
        shell("تعديل عميل", c.name)
        val box = card(); root.addView(box, matchWidth())
        val nameF = addFieldTo(box, "الاسم", "الاسم"); nameF.setText(c.name)
        val phoneF = addFieldTo(box, "الهاتف", "الهاتف", "phone"); phoneF.setText(c.phone)
        val altF = addFieldTo(box, "هاتف بديل", "بديل", "phone"); altF.setText(c.alternatePhones)
        val emailF = addFieldTo(box, "إيميل", "إيميل"); emailF.setText(c.email)
        val cityF = addFieldTo(box, "مدينة", "مدينة"); cityF.setText(c.city)
        val addrF = addFieldTo(box, "عنوان", "عنوان"); addrF.setText(c.address)
        val notesF = addFieldTo(box, "ملاحظات", "ملاحظات", "multiline"); notesF.setText(c.notes)
        addAction("حفظ التعديل") {
            db.updateCustomer(c.id, nameF.text.toString(), phoneF.text.toString(), altF.text.toString(), addrF.text.toString(), notesF.text.toString(), emailF.text.toString(), cityF.text.toString(), c.type)
            toast("تم التحديث"); showCustomerProfile(c.id)
        }
        addOutlineAction("إلغاء") { showCustomerProfile(c.id) }
    }

    // ==================== Vehicles ====================
    private fun showVehiclesPage() {
        currentTab = "vehicles"
        shell("السيارات", "جميع السيارات المسجلة في الورشة")
        addAction("➕ إضافة سيارة") { showAddVehicle() }
        sectionTitle("كل السيارات")
        val list = db.getAllVehicles()
        if (list.isEmpty()) addMuted("لا توجد سيارات")
        else list.forEach { v ->
            addRowItem(v.make + " " + v.model + " (" + v.year + ")", v.ownerName + " • لوحة: " + v.plate + " • " + v.color + " • " + v.fuelType) { showVehicleProfile(v.id) }
        }
    }

    private fun showAddVehicle(preselectedCustomer: Long = -1L) {
        if (!allowed("vehicles", "add")) return
        shell("سيارة جديدة", "أدخل بيانات السيارة بدقة")
        val box = card(); root.addView(box, matchWidth())
        val customers = db.getAllCustomers()
        box.addView(fieldLabel("العميل *"))
        val custNames = customers.map { it.name + " - " + it.phone }
        val custSp = spinner(if (custNames.isEmpty()) listOf("لا يوجد عملاء - أضف عميل أولاً") else custNames)
        if (preselectedCustomer > 0) { val idx = customers.indexOfFirst { it.id == preselectedCustomer }; if (idx >= 0) custSp.setSelection(idx) }
        box.addView(custSp, matchWidth())
        val makeF = addFieldTo(box, "الماركة *", "مثال: تويوتا")
        val modelF = addFieldTo(box, "الموديل *", "مثال: كورولا")
        val yearF = addFieldTo(box, "سنة الصنع", "2020", "number")
        val engineF = addFieldTo(box, "المحرك", "مثال: 1.8")
        val plateF = addFieldTo(box, "رقم اللوحة", "مثال: 12345")
        val vinF = addFieldTo(box, "رقم الشاصي VIN", "17 حرف")
        val odoF = addFieldTo(box, "عداد الكيلومترات", "0", "number")
        box.addView(fieldLabel("اللون"))
        val colorSp = spinner(listOf("أبيض", "أسود", "فضي", "رمادي", "أحمر", "أزرق", "أخضر", "ذهبي", "بني", "أخرى")); box.addView(colorSp, matchWidth())
        box.addView(fieldLabel("نوع الوقود"))
        val fuelSp = spinner(listOf("بنزين", "ديزل", "هجين", "كهرباء", "غاز")); box.addView(fuelSp, matchWidth())
        box.addView(fieldLabel("ناقل الحركة"))
        val transSp = spinner(listOf("أوتوماتيك", "عادي", "CVT")); box.addView(transSp, matchWidth())
        val notesF = addFieldTo(box, "ملاحظات", "حالة السيارة، ملاحظات", "multiline")
        plateF.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(e: Editable?) { val ex = db.findVehicleByPlate(e.toString()); if (ex != null) toast("تنبيه: اللوحة مسجلة: " + ex) }
        })
        addAction("حفظ السيارة") {
            if (customers.isEmpty()) { toast("أضف عميل أولاً"); return@addAction }
            try {
                val cid = customers[custSp.selectedItemPosition].id
                val id = db.addVehicle(cid, makeF.text.toString(), modelF.text.toString(), yearF.text.toString(), engineF.text.toString(), plateF.text.toString(), vinF.text.toString(), odoF.text.toString(), notesF.text.toString(), colorSp.selectedItem.toString(), fuelSp.selectedItem.toString(), transSp.selectedItem.toString())
                toast("تم حفظ السيارة"); showVehicleProfile(id)
            } catch (e: Exception) { toast(e.message ?: "خطأ") }
        }
        addOutlineAction("إلغاء") { showVehiclesPage() }
    }

    private fun showVehicleProfile(vehicleId: Long) {
        val v = db.getVehicleProfile(vehicleId) ?: run { toast("السيارة غير موجودة"); return }
        shell(v.make + " " + v.model, v.ownerName + " • لوحة: " + v.plate)
        addCard {
            val t = TextView(this@MainActivity).apply { text = "بيانات السيارة"; textSize = 16f; setTextColor(primary); typeface = Typeface.DEFAULT_BOLD }
            addView(t)
            val lines = listOf(
                "👤 المالك: " + v.ownerName + " (" + v.ownerPhone + ")",
                "📅 سنة الصنع: " + v.year, "⚙️ المحرك: " + v.engine,
                "🎨 اللون: " + v.color, "⛽ الوقود: " + v.fuelType, "🔧 القير: " + v.transmission,
                "🔢 VIN: " + v.vin, "📍 اللوحة: " + v.plate, "🛣️ العداد: " + v.odometer + " كم",
                "📝 ملاحظات: " + v.notes
            )
            lines.forEach { line -> val tv = TextView(this@MainActivity).apply { text = line; textSize = 14f; setTextColor(dark); setPadding(0, dp(3), 0, 0) }; addView(tv) }
        }
        sectionTitle("صور السيارة", "➕ إضافة صورة") {
            pendingPhotoVehicleId = vehicleId
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "image/*"; addCategory(Intent.CATEGORY_OPENABLE) }
            startActivityForResult(intent, PICK_PHOTO)
        }
        val photos = db.getVehiclePhotos(vehicleId)
        if (photos.isEmpty()) addMuted("لا توجد صور - أضف صورة للتوثيق")
        else photos.forEach { ph ->
            addRowItem("صورة - " + ph.createdAt, ph.uri.take(60)) {
                try { val iv = Intent(Intent.ACTION_VIEW).apply { setDataAndType(Uri.parse(ph.uri), "image/*"); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }; startActivity(iv) } catch (e: Exception) { toast("تعذر فتح الصورة") }
            }
        }
        addAction("🛠️ أمر شغل جديد لهذه السيارة") { showAddWorkOrder(v.customerId, vehicleId) }
        addOutlineAction("🔄 نقل ملكية") { showTransferOwnership(vehicleId) }
        sectionTitle("سجل زيارات السيارة")
        val orders = db.getVehicleWorkOrders(vehicleId)
        if (orders.isEmpty()) addMuted("لا توجد زيارات")
        else orders.forEach { o -> addRowItem("أمر #" + o.id + " - " + o.status, o.complaint + " • " + o.createdAt + " • " + (o.laborCost + o.partsCost - o.discount) + " ر.ي") { showWorkOrderDetails(o.id) } }
        sectionTitle("سجل الملكية")
        val hist = db.getVehicleOwnerHistory(vehicleId)
        if (hist.isEmpty()) addMuted("لا توجد عمليات نقل ملكية")
        else hist.forEach { h -> addRowItem("من " + h.oldOwner + " إلى " + h.newOwner, h.reason + " • " + h.changedAt) {} }
        addOutlineAction("⬅️ عودة للسيارات") { showVehiclesPage() }
    }

    private fun showTransferOwnership(vehicleId: Long) {
        val customers = db.getAllCustomers()
        AlertDialog.Builder(this).setTitle("نقل ملكية").setMessage("اختر المالك الجديد").setItems(customers.map { it.name + " - " + it.phone }.toTypedArray()) { _, idx ->
            val reasonF = field("سبب النقل", "multiline")
            AlertDialog.Builder(this).setTitle("سبب النقل").setView(reasonF).setPositiveButton("تأكيد") { _, _ ->
                db.transferOwnership(vehicleId, customers[idx].id, reasonF.text.toString()); toast("تم نقل الملكية"); showVehicleProfile(vehicleId)
            }.setNegativeButton("إلغاء", null).show()
        }.setNegativeButton("إلغاء", null).show()
    }

    // ==================== Work Orders ====================
    private fun showWorkOrdersPage() {
        currentTab = "orders"
        shell("أوامر الشغل", "متابعة الإصلاحات والحالات والدفعات")
        addAction("➕ أمر شغل جديد") { showAddWorkOrder() }
        // Filter chips
        val filters = listOf("الكل", "مفتوح", "قيد الإصلاح", "مكتمل")
        val chipRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        filters.forEach { f ->
            val chip = TextView(this).apply { text = f; textSize = 13f; setTextColor(if (f == "الكل") Color.WHITE else primary); setPadding(dp(14), dp(8), dp(14), dp(8)); background = rounded(if (f == "الكل") tealDark else Color.WHITE, 20, 1, border); setOnClickListener { showFilteredOrders(f) } }
            chipRow.addView(chip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(8) })
        }
        root.addView(chipRow, matchWidth().apply { setMargins(0, dp(4), 0, dp(12)) })
        sectionTitle("أحدث الأوامر")
        val list = db.getAllWorkOrders()
        if (list.isEmpty()) addMuted("لا توجد أوامر")
        else list.take(30).forEach { o ->
            val bc = if (o.status == "مكتمل") success else if (o.status == "مفتوح") warning else tealDark
            addRowItem("أمر #" + o.id + " - " + o.customerName, o.complaint + " • فني: " + o.technician + " • أولوية: " + o.priority + " • " + (o.laborCost + o.partsCost - o.discount) + " ر.ي", o.status, bc) { showWorkOrderDetails(o.id) }
        }
    }

    private fun showFilteredOrders(status: String) {
        shell("أوامر - " + status, "تصفية حسب الحالة")
        val list = if (status == "الكل") db.getAllWorkOrders() else db.getAllWorkOrders().filter { it.status == status }
        if (list.isEmpty()) addMuted("لا توجد أوامر بهذه الحالة")
        else list.forEach { o -> addRowItem("أمر #" + o.id + " - " + o.customerName, o.complaint + " • " + o.createdAt) { showWorkOrderDetails(o.id) } }
        addOutlineAction("عرض الكل") { showWorkOrdersPage() }
    }

    private fun showAddWorkOrder(preCustomer: Long = -1L, preVehicle: Long = -1L) {
        if (!allowed("work_orders", "add")) return
        shell("أمر شغل جديد", "سجل الشكوى والتشخيص والإصلاح")
        val box = card(); root.addView(box, matchWidth())
        val customers = db.getAllCustomers(); val vehicles = db.getAllVehicles()
        box.addView(fieldLabel("العميل"))
        val custSp = spinner(if (customers.isEmpty()) listOf("أضف عميل أولاً") else customers.map { it.name + " - " + it.phone })
        if (preCustomer > 0) { val idx = customers.indexOfFirst { it.id == preCustomer }; if (idx >= 0) custSp.setSelection(idx) }
        box.addView(custSp, matchWidth())
        box.addView(fieldLabel("السيارة"))
        val vehSp = spinner(if (vehicles.isEmpty()) listOf("أضف سيارة أولاً") else vehicles.map { it.make + " " + it.model + " - " + it.plate })
        if (preVehicle > 0) { val idx = vehicles.indexOfFirst { it.id == preVehicle }; if (idx >= 0) vehSp.setSelection(idx) }
        box.addView(vehSp, matchWidth())
        val odoF = addFieldTo(box, "عداد الكيلومترات", "العداد الحالي", "number")
        val complaintF = addFieldTo(box, "شكوى العميل *", "ماذا يشتكي العميل؟", "multiline")
        val diagF = addFieldTo(box, "التشخيص", "تشخيص الفني", "multiline")
        val repairF = addFieldTo(box, "ملاحظات الإصلاح", "ما تم إصلاحه", "multiline")
        box.addView(fieldLabel("الأولوية"))
        val priSp = spinner(listOf("عادية", "مستعجلة", "عاجلة جداً")); box.addView(priSp, matchWidth())
        val techF = addFieldTo(box, "اسم الفني", "مثال: أحمد")
        val laborF = addFieldTo(box, "أجور العمل", "0", "decimal")
        val partsF = addFieldTo(box, "تكلفة القطع", "0", "decimal")
        val discF = addFieldTo(box, "الخصم", "0", "decimal")
        val expectedF = addFieldTo(box, "تاريخ التسليم المتوقع", "2026-11-25")
        box.addView(fieldLabel("الحالة"))
        val statusSp = spinner(listOf("مفتوح", "قيد الإصلاح", "بانتظار قطع", "مكتمل", "مغلق")); box.addView(statusSp, matchWidth())
        val payF = addFieldTo(box, "دفعة مقدمة (اختياري)", "0", "decimal")
        addAction("حفظ أمر الشغل") {
            if (customers.isEmpty() || vehicles.isEmpty()) { toast("أضف عميل وسيارة أولاً"); return@addAction }
            try {
                val cid = customers[custSp.selectedItemPosition].id; val vid = vehicles[vehSp.selectedItemPosition].id
                val id = db.addWorkOrder(cid, vid, odoF.text.toString().toLongOrNull() ?: 0L, complaintF.text.toString(), diagF.text.toString(), repairF.text.toString(), statusSp.selectedItem.toString(), laborF.text.toString().toDoubleOrNull() ?: 0.0, partsF.text.toString().toDoubleOrNull() ?: 0.0, priSp.selectedItem.toString(), techF.text.toString(), discF.text.toString().toDoubleOrNull() ?: 0.0, expectedF.text.toString())
                val advance = payF.text.toString().toDoubleOrNull() ?: 0.0
                if (advance > 0) db.addPayment(id, advance, "نقدي", "دفعة مقدمة")
                toast("تم حفظ أمر الشغل"); showWorkOrderDetails(id)
            } catch (e: Exception) { toast(e.message ?: "خطأ") }
        }
        addOutlineAction("إلغاء") { showWorkOrdersPage() }
    }

    private fun showWorkOrderDetails(orderId: Long) {
        val o = db.getWorkOrderById(orderId) ?: run { toast("الأمر غير موجود"); return }
        val (paid, remaining) = db.getWorkOrderBalance(orderId)
        val total = o.laborCost + o.partsCost - o.discount
        shell("أمر الشغل #" + o.id, "العميل: " + o.customerName + " • الحالة: " + o.status)
        addCard {
            val t = TextView(this@MainActivity).apply { text = "تفاصيل الأمر"; textSize = 16f; setTextColor(primary); typeface = Typeface.DEFAULT_BOLD }
            addView(t)
            val lines = listOf(
                "📝 الشكوى: " + o.complaint, "🔍 التشخيص: " + o.diagnosis, "🔧 الإصلاح: " + o.repairNotes,
                "👨‍🔧 الفني: " + o.technician, "⚡ الأولوية: " + o.priority, "📅 التسليم المتوقع: " + o.expectedDate,
                "🛣️ العداد: " + o.odometer + " كم", "📅 التاريخ: " + o.createdAt
            )
            lines.forEach { line -> val tv = TextView(this@MainActivity).apply { text = line; textSize = 14f; setTextColor(dark); setPadding(0, dp(3), 0, 0) }; addView(tv) }
        }
        addCard {
            val t = TextView(this@MainActivity).apply { text = "💰 الحساب"; textSize = 16f; setTextColor(primary); typeface = Typeface.DEFAULT_BOLD }
            addView(t)
            val rows = listOf(
                "أجور العمل: " + o.laborCost + " ر.ي", "تكلفة القطع: " + o.partsCost + " ر.ي",
                "الخصم: -" + o.discount + " ر.ي", "الإجمالي: " + total + " ر.ي",
                "المدفوع: " + paid + " ر.ي", "المتبقي: " + remaining + " ر.ي"
            )
            rows.forEach { line ->
                val tv = TextView(this@MainActivity).apply { text = line; textSize = 14f; setTextColor(if (line.startsWith("المتبقي")) danger else dark); typeface = if (line.startsWith("الإجمالي") || line.startsWith("المتبقي")) Typeface.DEFAULT_BOLD else Typeface.DEFAULT; setPadding(0, dp(3), 0, 0) }
                addView(tv)
            }
            // Progress
            val pct = if (total > 0) ((paid / total) * 100).toInt().coerceIn(0, 100) else 0
            val barBg = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL; background = rounded(Color.rgb(226,232,240), 20); setPadding(0, 0, 0, 0) }
            val barFill = View(this@MainActivity).apply { setBackgroundColor(if (remaining == 0.0) success else teal); layoutParams = LinearLayout.LayoutParams(0, dp(10), pct.toFloat()) }
            barBg.addView(barFill)
            addView(TextView(this@MainActivity).apply { text = "نسبة الدفع: " + pct + "%"; textSize = 12f; setTextColor(muted); setPadding(0, dp(10), 0, dp(4)) })
            addView(barBg, matchWidth())
        }
        sectionTitle("الدفعات")
        val pays = db.getPayments(orderId)
        if (pays.isEmpty()) addMuted("لا توجد دفعات بعد")
        else pays.forEach { p -> addRowItem(p.amount.toString() + " ر.ي - " + p.method, p.note + " • " + p.createdAt) {} }
        if (remaining > 0) {
            addAction("💵 إضافة دفعة") {
                val amtF = field("المبلغ", "decimal"); val methodSp = spinner(listOf("نقدي", "تحويل", "بطاقة", "آجل")); val noteF = field("ملاحظة")
                val dlgBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(16)) }
                dlgBox.addView(fieldLabel("المبلغ")); dlgBox.addView(amtF, matchWidth())
                dlgBox.addView(fieldLabel("طريقة الدفع")); dlgBox.addView(methodSp, matchWidth())
                dlgBox.addView(fieldLabel("ملاحظة")); dlgBox.addView(noteF, matchWidth())
                AlertDialog.Builder(this).setTitle("دفعة جديدة - المتبقي " + remaining).setView(dlgBox).setPositiveButton("حفظ") { _, _ ->
                    val amt = amtF.text.toString().toDoubleOrNull() ?: 0.0
                    if (amt <= 0) toast("أدخل مبلغ صحيح") else { db.addPayment(orderId, amt, methodSp.selectedItem.toString(), noteF.text.toString()); toast("تم تسجيل الدفعة"); showWorkOrderDetails(orderId) }
                }.setNegativeButton("إلغاء", null).show()
            }
        }
        addAction("🖨️ طباعة / مشاركة الفاتورة") { shareInvoice(orderId) }
        addOutlineAction("🔄 تغيير الحالة") {
            AlertDialog.Builder(this).setTitle("تغيير حالة الأمر").setItems(arrayOf("مفتوح", "قيد الإصلاح", "بانتظار قطع", "مكتمل", "مغلق")) { _, idx ->
                val statuses = arrayOf("مفتوح", "قيد الإصلاح", "بانتظار قطع", "مكتمل", "مغلق")
                db.updateWorkOrderStatus(orderId, statuses[idx]); toast("تم تحديث الحالة"); showWorkOrderDetails(orderId)
            }.setNegativeButton("إلغاء", null).show()
        }
        addOutlineAction("⬅️ عودة للأوامر") { showWorkOrdersPage() }
    }

    private fun shareInvoice(orderId: Long) {
        val o = db.getWorkOrderById(orderId) ?: return
        val (paid, remaining) = db.getWorkOrderBalance(orderId)
        val total = o.laborCost + o.partsCost - o.discount
        val invoice = """
            ========================
            🧾 فاتورة ورشة خبارة
            ========================
            رقم الأمر: #$orderId
            العميل: ${o.customerName}
            التاريخ: ${o.createdAt}
            ------------------------
            الشكوى: ${o.complaint}
            التشخيص: ${o.diagnosis}
            الإصلاح: ${o.repairNotes}
            الفني: ${o.technician}
            ------------------------
            أجور العمل: ${o.laborCost} ر.ي
            قطع الغيار: ${o.partsCost} ر.ي
            الخصم: -${o.discount} ر.ي
            الإجمالي: $total ر.ي
            المدفوع: $paid ر.ي
            المتبقي: $remaining ر.ي
            ------------------------
            شكراً لثقتكم بنا ❤️
            ورشة خبارة - صنعاء
        """.trimIndent()
        val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, invoice); putExtra(Intent.EXTRA_SUBJECT, "فاتورة أمر #$orderId") }
        startActivity(Intent.createChooser(intent, "مشاركة الفاتورة"))
    }

    // ==================== Parts / Inventory ====================
    private fun showPartsPage() {
        currentTab = "parts"
        shell("المخزون وقطع الغيار", "تابع الكميات ونقص المخزون")
        addAction("➕ إضافة قطعة جديدة") { showAddPart() }
        val low = db.getLowStockParts()
        if (low.isNotEmpty()) {
            sectionTitle("⚠️ قطع تحتاج إعادة طلب (" + low.size + ")")
            low.forEach { p -> addRowItem("⚠️ " + p.name, "المتبقي: " + p.quantity + " " + p.unit + " • الحد: " + p.minQuantity + " • رف: " + p.shelf, "ناقص", danger) { showPartDetails(p) } }
        }
        sectionTitle("كل القطع")
        val list = db.getAllParts()
        if (list.isEmpty()) addMuted("المخزون فارغ")
        else list.forEach { p ->
            val badge = if (p.quantity <= p.minQuantity) "ناقص" else p.quantity.toString() + " " + p.unit
            val bc = if (p.quantity <= p.minQuantity) danger else success
            addRowItem(p.name, "رقم: " + p.partNumber + " • باركود: " + p.barcode + " • سعر: " + p.salePrice + " ر.ي • مورد: " + p.supplier + " • رف: " + p.shelf, badge, bc) { showPartDetails(p) }
        }
    }

    private fun showAddPart() {
        if (!allowed("parts", "add")) return
        shell("قطعة جديدة", "أضف قطعة للمخزون")
        val box = card(); root.addView(box, matchWidth())
        val numF = addFieldTo(box, "رقم القطعة", "مثال: P-123")
        val nameF = addFieldTo(box, "اسم القطعة *", "مثال: فلتر زيت")
        val qtyF = addFieldTo(box, "الكمية", "0", "decimal")
        box.addView(fieldLabel("الوحدة"))
        val unitSp = spinner(listOf("قطعة", "علبة", "لتر", "متر", "طقم", "كيس")); box.addView(unitSp, matchWidth())
        val purchF = addFieldTo(box, "سعر الشراء", "0", "decimal")
        val saleF = addFieldTo(box, "سعر البيع", "0", "decimal")
        val minF = addFieldTo(box, "الحد الأدنى للتنبيه", "مثال: 5", "decimal")
        val suppF = addFieldTo(box, "المورد", "اسم المورد")
        val shelfF = addFieldTo(box, "رقم الرف", "مثال: A-1")
        val barF = addFieldTo(box, "الباركود", "امسح أو أدخل الباركود")
        numF.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(e: Editable?) { val ex = db.findPartByNumber(e.toString()); if (ex != null) toast("تنبيه: الرقم مسجل: " + ex) }
        })
        addAction("حفظ القطعة") {
            try {
                db.addPart(numF.text.toString(), nameF.text.toString(), qtyF.text.toString().toDoubleOrNull() ?: 0.0, unitSp.selectedItem.toString(), purchF.text.toString().toDoubleOrNull() ?: 0.0, saleF.text.toString().toDoubleOrNull() ?: 0.0, minF.text.toString().toDoubleOrNull() ?: 0.0, suppF.text.toString(), shelfF.text.toString(), barF.text.toString())
                toast("تم حفظ القطعة"); showPartsPage()
            } catch (e: Exception) { toast(e.message ?: "خطأ") }
        }
        addOutlineAction("إلغاء") { showPartsPage() }
    }

    private fun showPartDetails(p: PartItem) {
        shell(p.name, "رقم: " + p.partNumber + " • باركود: " + p.barcode)
        addCard {
            val t = TextView(this@MainActivity).apply { text = "بيانات القطعة"; textSize = 16f; setTextColor(primary); typeface = Typeface.DEFAULT_BOLD }
            addView(t)
            val lines = listOf(
                "📦 الكمية: " + p.quantity + " " + p.unit,
                "💰 سعر الشراء: " + p.purchasePrice + " ر.ي • سعر البيع: " + p.salePrice + " ر.ي",
                "⚠️ الحد الأدنى: " + p.minQuantity + " " + p.unit,
                "🏭 المورد: " + p.supplier, "🗄️ الرف: " + p.shelf, "🔖 الباركود: " + p.barcode
            )
            lines.forEach { line -> val tv = TextView(this@MainActivity).apply { text = line; textSize = 14f; setTextColor(dark); setPadding(0, dp(3), 0, 0) }; addView(tv) }
            if (p.quantity <= p.minQuantity) {
                val warn = TextView(this@MainActivity).apply { text = "⚠️ تنبيه: وصلت للحد الأدنى - أعد الطلب"; textSize = 14f; setTextColor(danger); typeface = Typeface.DEFAULT_BOLD; setPadding(0, dp(10), 0, 0) }
                addView(warn)
            }
        }
        addAction("➕ إضافة كمية") {
            val qF = field("الكمية المضافة", "decimal")
            AlertDialog.Builder(this).setTitle("إضافة كمية").setView(qF).setPositiveButton("إضافة") { _, _ ->
                val q = qF.text.toString().toDoubleOrNull() ?: 0.0
                db.adjustPartStock(p.id, q); toast("تمت الإضافة"); showPartsPage()
            }.setNegativeButton("إلغاء", null).show()
        }
        addOutlineAction("➖ خصم كمية (بيع / استخدام)") {
            val qF = field("الكمية", "decimal")
            AlertDialog.Builder(this).setTitle("خصم كمية").setView(qF).setPositiveButton("خصم") { _, _ ->
                val q = qF.text.toString().toDoubleOrNull() ?: 0.0
                db.adjustPartStock(p.id, -q); toast("تم الخصم"); showPartsPage()
            }.setNegativeButton("إلغاء", null).show()
        }
        addOutlineAction("⬅️ عودة للمخزون") { showPartsPage() }
    }

    // ==================== Reports ====================
    private fun showReportsPage() {
        shell("التقارير", "تقرير اليوم والإيرادات والديون")
        val today = db.getDailyReport("")
        addCard {
            val t = TextView(this@MainActivity).apply { text = "📊 تقرير اليوم"; textSize = 17f; setTextColor(primary); typeface = Typeface.DEFAULT_BOLD }
            addView(t)
            val lines = listOf(
                "أوامر اليوم: " + today.ordersCount, "أوامر مكتملة: " + today.completedCount,
                "إجمالي الإيراد: " + String.format("%.0f", today.revenue) + " ر.ي",
                "أجور العمل: " + String.format("%.0f", today.laborTotal) + " ر.ي",
                "قطع الغيار: " + String.format("%.0f", today.partsTotal) + " ر.ي",
                "مدفوعات اليوم: " + String.format("%.0f", today.paymentsReceived) + " ر.ي",
                "إجمالي الديون المتبقية: " + String.format("%.0f", today.debtRemaining) + " ر.ي"
            )
            lines.forEach { line -> val tv = TextView(this@MainActivity).apply { text = line; textSize = 14f; setTextColor(dark); setPadding(0, dp(4), 0, 0) }; addView(tv) }
        }
        addCard {
            val t = TextView(this@MainActivity).apply { text = "🔍 تقرير تاريخ معين"; textSize = 15f; setTextColor(primary); typeface = Typeface.DEFAULT_BOLD }
            addView(t)
            val dateF = field("YYYY-MM-DD", "text"); dateF.setText("2026-11-22")
            addView(fieldLabel("التاريخ (YYYY-MM-DD)")); addView(dateF, matchWidth())
            val btn = Button(this@MainActivity).apply { text = "عرض التقرير"; setTextColor(Color.WHITE); background = rounded(tealDark, 12); setOnClickListener {
                val rep = db.getDailyReport(dateF.text.toString().trim())
                showMessage("تقرير " + dateF.text.toString(), "أوامر: " + rep.ordersCount + "\nمكتملة: " + rep.completedCount + "\nإيراد: " + rep.revenue + " ر.ي\nمدفوعات: " + rep.paymentsReceived + " ر.ي")
            } }
            addView(btn, matchWidth().apply { setMargins(0, dp(10), 0, 0) })
        }
        sectionTitle("أكبر الديون")
        val debtors = db.getAllCustomers().map { it to db.getCustomerDebt(it.id) }.filter { it.second > 0 }.sortedByDescending { it.second }.take(5)
        if (debtors.isEmpty()) addMuted("لا توجد ديون مستحقة - ممتاز!")
        else debtors.forEach { (c, d) -> addRowItem(c.name, c.phone, String.format("%.0f", d) + " ر.ي", danger) { showCustomerProfile(c.id) } }
        addOutlineAction("💾 تصدير نسخة احتياطية") { exportBackup() }
        addOutlineAction("⬅️ عودة") { showDashboard() }
    }

    // ==================== Search ====================
    private fun showAdvancedSearch() {
        shell("بحث متقدم", "ابحث في العملاء والسيارات والأوامر والقطع")
        addCard {
            val qF = field("اكتب كلمة البحث...")
            addView(fieldLabel("كلمة البحث")); addView(qF, matchWidth())
            addView(fieldLabel("التصنيف"))
            val filterSp = spinner(listOf("الكل", "عملاء", "سيارات", "أوامر", "قطع")); addView(filterSp, matchWidth())
            val btn = Button(this@MainActivity).apply { text = "بحث"; setTextColor(Color.WHITE); background = rounded(tealDark, 12); setOnClickListener { showSearchResults(qF.text.toString(), filterSp.selectedItem.toString()) } }
            addView(btn, matchWidth().apply { setMargins(0, dp(12), 0, 0) })
        }
        addOutlineAction("⬅️ عودة") { showDashboard() }
    }

    private fun showSearchResults(query: String, filter: String = "الكل") {
        shell("نتائج البحث: " + query, "التصنيف: " + filter)
        val results = db.searchAdvanced(query, filter)
        if (results.isEmpty()) addMuted("لا توجد نتائج مطابقة")
        else results.forEach { r ->
            addRowItem(r.title, r.subtitle + " • " + r.type) {
                when (r.type) {
                    "customer" -> showCustomerProfile(r.id)
                    "vehicle" -> showVehicleProfile(r.id)
                    "work_order" -> showWorkOrderDetails(r.id)
                    "part" -> showPartsPage()
                }
            }
        }
        addOutlineAction("بحث جديد") { showAdvancedSearch() }
    }

    // ==================== More / Settings / Users ====================
    private fun showMorePage() {
        currentTab = "more"
        shell("المزيد", "إعدادات، مستخدمون، نسخ احتياطي")
        addCard {
            val t = TextView(this@MainActivity).apply { text = "👤 حسابي"; textSize = 16f; setTextColor(primary); typeface = Typeface.DEFAULT_BOLD }
            addView(t)
            val info = TextView(this@MainActivity).apply { text = (loggedUser?.fullName ?: "") + " • " + (loggedUser?.username ?: "") + " • دور: " + (loggedUser?.role ?: ""); textSize = 14f; setTextColor(dark); setPadding(0, dp(6), 0, 0) }
            addView(info)
        }
        addAction("📊 التقارير") { showReportsPage() }
        addAction("🔍 بحث متقدم") { showAdvancedSearch() }
        addAction("💾 نسخ احتياطي / تصدير") { exportBackup() }
        addAction("👥 إدارة المستخدمين والصلاحيات") { showUsersPage() }
        addAction("📋 سجل العملاء") { showCustomersPage() }
        addAction("📋 سجل السيارات") { showVehiclesPage() }
        addAction("📋 سجل الأوامر") { showWorkOrdersPage() }
        addDangerAction("🚪 تسجيل الخروج") { logout() }
    }

    private fun showUsersPage() {
        if (!allowed("users", "view")) return
        shell("المستخدمون والصلاحيات", "إدارة حسابات الموظفين")
        addAction("➕ إضافة مستخدم جديد") { showAddUser() }
        sectionTitle("كل المستخدمين")
        db.getAllUsers().forEach { u ->
            addRowItem(u.fullName + " (" + u.username + ")", "الدور: " + u.role) {
                val perms = db.getUserPermissions(u.id)
                val msg = perms.joinToString("\n") { it.module + ": عرض=" + it.canView + " إضافة=" + it.canAdd + " تعديل=" + it.canEdit + " حذف=" + it.canDelete }
                showMessage("صلاحيات " + u.fullName, if (msg.isBlank()) "مدير - كل الصلاحيات" else msg)
            }
        }
        addOutlineAction("⬅️ عودة") { showMorePage() }
    }

    private fun showAddUser() {
        if (!allowed("users", "add")) return
        shell("مستخدم جديد", "أنشئ حساب موظف بصلاحيات محددة")
        val box = card(); root.addView(box, matchWidth())
        val nameF = addFieldTo(box, "الاسم الكامل", "الاسم")
        val userF = addFieldTo(box, "اسم المستخدم", "username")
        val passF = addFieldTo(box, "كلمة المرور", "كلمة مرور", "password")
        box.addView(fieldLabel("الدور"))
        val roleSp = spinner(listOf("موظف", "مدير", "فني", "استقبال")); box.addView(roleSp, matchWidth())
        addAction("إنشاء المستخدم") {
            try {
                db.createUser(userF.text.toString(), passF.text.toString(), nameF.text.toString(), roleSp.selectedItem.toString())
                toast("تم إنشاء المستخدم"); showUsersPage()
            } catch (e: Exception) { toast(e.message ?: "خطأ") }
        }
        addOutlineAction("إلغاء") { showUsersPage() }
    }

    // ==================== Backup ====================
    private fun exportBackup() {
        try {
            val text = db.exportBackupText()
            val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text); putExtra(Intent.EXTRA_SUBJECT, "نسخة احتياطية - ورشة خبارة") }
            startActivity(Intent.createChooser(intent, "مشاركة النسخة الاحتياطية"))
            // Also save local copy
            try {
                val file = File(getExternalFilesDir(null), "workshop_backup.txt")
                FileOutputStream(file).use { it.write(text.toByteArray(Charsets.UTF_8)) }
                toast("تم حفظ نسخة في: " + file.absolutePath)
            } catch (e: Exception) {}
        } catch (e: Exception) { toast("تعذر التصدير: " + e.message) }
    }

    // Legacy compatibility - recordsDialog
    private fun recordsDialog(table: String, title: String) {
        if (table == "parts") { showPartsPage(); return }
        when (table) {
            "customers" -> showCustomersPage()
            "vehicles" -> showVehiclesPage()
            "work_orders" -> showWorkOrdersPage()
        }
    }
}
