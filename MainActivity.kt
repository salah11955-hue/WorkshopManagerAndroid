package com.khubara.workshop

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.provider.MediaStore
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import java.io.File
import java.io.FileOutputStream

class MainActivity : Activity() {

    private enum class Tab {
        HOME, CUSTOMERS, VEHICLES, WORK_ORDERS, INVENTORY, MORE
    }

    private enum class Screen {
        CREATE_ADMIN,
        LOGIN,
        TAB_HOME,
        TAB_CUSTOMERS,
        TAB_VEHICLES,
        TAB_WORK_ORDERS,
        TAB_INVENTORY,
        TAB_MORE,
        RECEPTION,
        SEARCH,
        REPORTS,
        USERS,
        PERMISSIONS
    }

    private lateinit var db: WorkshopDb
    private lateinit var contentScroll: ScrollView
    private lateinit var root: LinearLayout
    private lateinit var bottomBar: LinearLayout

    private var loggedUser: AuthUser? = null
    private var activeFormDialog: AlertDialog? = null
    private var activeFormScroll: ScrollView? = null
    private var activeFormSubmit: (() -> Unit)? = null

    private val screenStack = ArrayDeque<Screen>()
    private var currentTab: Tab = Tab.HOME
    private var workOrderFilter: String = "الكل"
    private var selectedPermissionUser: AuthUser? = null
    private var lastBackupFile: File? = null

    private var pendingPhotoLabel: String = "صورة سيارة"
    private var pendingPhotoCallback: ((String) -> Unit)? = null

    // الألوان العصرية الجديدة: كحلي داكن + تركوازي + ذهبي + خلفية فاتحة
    private val navyDark = Color.rgb(15, 23, 42)
    private val navyCard = Color.rgb(30, 41, 59)
    private val teal = Color.rgb(13, 148, 136)
    private val tealDark = Color.rgb(15, 118, 110)
    private val gold = Color.rgb(194, 145, 55)
    private val bgLight = Color.rgb(244, 246, 250)
    private val cardWhite = Color.WHITE
    private val borderLight = Color.rgb(216, 222, 232)
    private val textDark = Color.rgb(15, 23, 42)
    private val textMuted = Color.rgb(100, 116, 139)
    private val dangerRed = Color.rgb(185, 28, 28)
    private val dangerDark = Color.rgb(153, 27, 27)
    private val warnAmber = Color.rgb(180, 83, 9)

    companion object {
        private const val REQ_CAPTURE_PHOTO = 9001
        private const val REQ_PICK_PHOTO = 9002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )

        db = WorkshopDb(this)
        setupShellLayout()

        if (db.hasUsers()) {
            navigateTo(Screen.LOGIN, clearStack = true)
        } else {
            navigateTo(Screen.CREATE_ADMIN, clearStack = true)
        }
    }

    private fun setupShellLayout() {
        val shell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgLight)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        contentScroll = ScrollView(this).apply {
            setBackgroundColor(bgLight)
            isFillViewport = true
            isSmoothScrollingEnabled = true
        }

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(24))
            setBackgroundColor(bgLight)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        contentScroll.addView(root)

        bottomBar = buildBottomNavBar()

        shell.addView(
            contentScroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )
        shell.addView(
            bottomBar,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        setContentView(shell)
    }

    private fun buildBottomNavBar(): LinearLayout {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(navyDark)
            setPadding(dp(4), dp(6), dp(4), dp(8))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        val items = listOf(
            Triple(Tab.HOME, "⌂", "الرئيسية"),
            Triple(Tab.CUSTOMERS, "👤", "العملاء"),
            Triple(Tab.VEHICLES, "🚗", "السيارات"),
            Triple(Tab.WORK_ORDERS, "🛠", "أوامر"),
            Triple(Tab.INVENTORY, "📦", "المخزون"),
            Triple(Tab.MORE, "☰", "المزيد")
        )

        items.forEach { (tab, icon, title) ->
            val isSelected = currentTab == tab
            val tabBox = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(2), dp(5), dp(2), dp(5))
                background = GradientDrawable().apply {
                    setColor(if (isSelected) teal else Color.TRANSPARENT)
                    cornerRadius = dp(10).toFloat()
                }
                setOnClickListener {
                    if (loggedUser == null) {
                        toast("يرجى تسجيل الدخول أولاً")
                        return@setOnClickListener
                    }
                    currentTab = tab
                    val targetScreen = when (tab) {
                        Tab.HOME -> Screen.TAB_HOME
                        Tab.CUSTOMERS -> Screen.TAB_CUSTOMERS
                        Tab.VEHICLES -> Screen.TAB_VEHICLES
                        Tab.WORK_ORDERS -> Screen.TAB_WORK_ORDERS
                        Tab.INVENTORY -> Screen.TAB_INVENTORY
                        Tab.MORE -> Screen.TAB_MORE
                    }
                    navigateTo(targetScreen, clearStack = true)
                }
            }

            val iconTv = TextView(this).apply {
                text = icon
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(if (isSelected) Color.WHITE else Color.rgb(148, 163, 184))
            }
            val labelTv = TextView(this).apply {
                text = title
                textSize = 11f
                gravity = Gravity.CENTER
                typeface = if (isSelected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setTextColor(if (isSelected) Color.WHITE else Color.rgb(203, 213, 225))
            }
            tabBox.addView(iconTv, matchWidth())
            tabBox.addView(labelTv, matchWidth())

            bar.addView(
                tabBox,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(dp(2), 0, dp(2), 0)
                }
            )
        }
        return bar
    }

    private fun refreshBottomBarSelection() {
        val index = bottomBar.indexOfChild(bottomBar.findViewWithTag<View>("__dummy__"))
        val parent = bottomBar.parent as? LinearLayout ?: return
        val idx = parent.indexOfChild(bottomBar)
        if (idx >= 0) {
            bottomBar = buildBottomNavBar()
            parent.removeViewAt(idx)
            parent.addView(
                bottomBar,
                idx,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            )
        }
        @Suppress("UNUSED_VARIABLE")
        val ignored = index
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (activeFormDialog?.isShowing == true) {
            activeFormDialog?.dismiss()
            return
        }
        if (screenStack.size > 1) {
            screenStack.removeLast()
            renderScreen(screenStack.last())
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        activeFormDialog?.dismiss()
        db.close()
    }

    private fun navigateTo(screen: Screen, clearStack: Boolean = false) {
        if (clearStack) screenStack.clear()
        if (screenStack.lastOrNull() != screen) screenStack.addLast(screen)
        renderScreen(screen)
    }

    private fun renderScreen(screen: Screen) {
        when (screen) {
            Screen.CREATE_ADMIN -> showCreateAdmin()
            Screen.LOGIN -> showLogin()
            Screen.TAB_HOME -> {
                currentTab = Tab.HOME
                showHomeDashboard()
            }
            Screen.TAB_CUSTOMERS -> {
                currentTab = Tab.CUSTOMERS
                showCustomersScreen()
            }
            Screen.TAB_VEHICLES -> {
                currentTab = Tab.VEHICLES
                showVehiclesScreen()
            }
            Screen.TAB_WORK_ORDERS -> {
                currentTab = Tab.WORK_ORDERS
                showWorkOrdersScreen()
            }
            Screen.TAB_INVENTORY -> {
                currentTab = Tab.INVENTORY
                showInventoryScreen()
            }
            Screen.TAB_MORE -> {
                currentTab = Tab.MORE
                showMoreScreen()
            }
            Screen.RECEPTION -> receptionScreen()
            Screen.SEARCH -> advancedSearchScreen()
            Screen.REPORTS -> reportsScreen(WorkshopDb.todayString())
            Screen.USERS -> usersScreen()
            Screen.PERMISSIONS -> {
                val target = selectedPermissionUser
                if (target != null) permissionsScreen(target) else showMoreScreen()
            }
        }
    }

    // ---------------------------------------------------------
    // الرأس الموحد، الكروت، الأزرار، والشارات
    // ---------------------------------------------------------

    private fun page(
        title: String,
        subtitle: String = "",
        showBack: Boolean = false,
        showBottomNav: Boolean = true,
        darkAuthBackground: Boolean = false
    ) {
        bottomBar.visibility = if (showBottomNav && loggedUser != null) View.VISIBLE else View.GONE
        refreshBottomBarSelection()

        val pageBg = if (darkAuthBackground) navyDark else bgLight
        contentScroll.setBackgroundColor(pageBg)
        root.removeAllViews()
        root.setBackgroundColor(pageBg)
        root.setPadding(dp(14), dp(14), dp(14), dp(28))

        val userBadgeText = loggedUser?.let { "${it.fullName} • ${it.role}" } ?: "خبراء هونداي وفورد"

        val headerCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable().apply {
                setColor(if (darkAuthBackground) navyCard else navyDark)
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), gold)
            }
        }

        val appTitleTv = TextView(this).apply {
            text = "خبراء هونداي وفورد — $title"
            textSize = 18.5f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        headerCard.addView(appTitleTv, matchWidth())

        val userInfoTv = TextView(this).apply {
            text = if (subtitle.isNotBlank()) "$userBadgeText  |  $subtitle" else userBadgeText
            textSize = 12.5f
            setTextColor(Color.rgb(245, 208, 120))
            gravity = Gravity.CENTER
            setPadding(0, dp(5), 0, 0)
        }
        headerCard.addView(userInfoTv, matchWidth())

        root.addView(headerCard, matchWidth().apply { setMargins(0, 0, 0, dp(12)) })

        if (showBack && screenStack.size > 1) {
            addSecondaryButton("← رجوع للصفحة السابقة") {
                screenStack.removeLast()
                renderScreen(screenStack.last())
            }
        }

        contentScroll.post { contentScroll.scrollTo(0, 0) }
    }

    private fun createCard(title: String, accentColor: Int = teal, cardBackground: Int = cardWhite): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(14))
            background = GradientDrawable().apply {
                setColor(cardBackground)
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), if (cardBackground == dangerRed) dangerDark else borderLight)
            }
        }

        val titleTv = TextView(this).apply {
            text = title
            textSize = 15.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (cardBackground == dangerRed) Color.WHITE else accentColor)
            setPadding(dp(2), 0, dp(2), dp(8))
        }
        card.addView(titleTv, matchWidth())

        root.addView(card, matchWidth().apply { setMargins(0, 0, 0, dp(12)) })
        return card
    }

    private fun addText(
        text: String,
        size: Float = 14.5f,
        color: Int = textDark,
        bold: Boolean = false,
        parent: LinearLayout = root
    ): TextView {
        val tv = TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
            setLineSpacing(dp(2).toFloat(), 1.15f)
            setPadding(dp(3), dp(4), dp(3), dp(4))
        }
        parent.addView(tv, matchWidth())
        return tv
    }

    private fun addStatusBadge(statusText: String, parent: LinearLayout) {
        val badgeColor = when (statusText) {
            "مكتمل" -> teal
            "مفتوح" -> gold
            "قيد الإصلاح", "قيد العمل" -> navyDark
            "ناقص" -> dangerRed
            else -> textMuted
        }
        val badge = TextView(this).apply {
            text = "● $statusText"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(dp(10), dp(4), dp(10), dp(4))
            background = GradientDrawable().apply {
                setColor(badgeColor)
                cornerRadius = dp(99).toFloat()
            }
        }
        parent.addView(
            badge,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, dp(4), 0, dp(6))
            }
        )
    }

    private fun buttonBg(fillColor: Int, strokeColor: Int, pressedFill: Int): StateListDrawable {
        val normal = GradientDrawable().apply {
            setColor(fillColor)
            cornerRadius = dp(12).toFloat()
            setStroke(dp(1), strokeColor)
        }
        val pressed = GradientDrawable().apply {
            setColor(pressedFill)
            cornerRadius = dp(12).toFloat()
            setStroke(dp(1.5.toInt()), gold)
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressed)
            addState(intArrayOf(), normal)
        }
    }

    // زر رئيسي ملون
    private fun addPrimaryButton(label: String, parent: LinearLayout = root, color: Int = teal, action: () -> Unit) {
        val pressedColor = if (color == teal) tealDark else navyCard
        val btn = Button(this).apply {
            text = label
            textSize = 14.5f
            isAllCaps = false
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = buttonBg(color, color, pressedColor)
            setPadding(dp(14), dp(11), dp(14), dp(11))
            minHeight = dp(46)
            setOnClickListener { action() }
        }
        parent.addView(btn, matchWidth().apply { setMargins(0, dp(4), 0, dp(5)) })
    }

    // زر أبيض بحدود
    private fun addSecondaryButton(label: String, parent: LinearLayout = root, action: () -> Unit) {
        val btn = Button(this).apply {
            text = label
            textSize = 14f
            isAllCaps = false
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(navyDark)
            background = buttonBg(cardWhite, borderLight, Color.rgb(226, 232, 240))
            setPadding(dp(14), dp(10), dp(14), dp(10))
            minHeight = dp(44)
            setOnClickListener { action() }
        }
        parent.addView(btn, matchWidth().apply { setMargins(0, dp(4), 0, dp(5)) })
    }

    // زر خطر أحمر
    private fun addDangerButton(label: String, parent: LinearLayout = root, action: () -> Unit) {
        val btn = Button(this).apply {
            text = label
            textSize = 14.5f
            isAllCaps = false
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = buttonBg(dangerRed, dangerRed, dangerDark)
            setPadding(dp(14), dp(11), dp(14), dp(11))
            minHeight = dp(46)
            setOnClickListener { action() }
        }
        parent.addView(btn, matchWidth().apply { setMargins(0, dp(4), 0, dp(5)) })
    }

    // تعويم الحقول تلقائياً فوق لوحة المفاتيح + التنقل بزر الإدخال
    private fun attachSmartKeyboardNavigation(
        fields: List<AutoCompleteTextView>,
        scrollContainer: ScrollView?,
        onLastDone: (() -> Unit)? = null
    ) {
        fields.forEachIndexed { idx, edit ->
            val isLast = idx == fields.lastIndex
            if (edit.inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE == 0) {
                edit.imeOptions = if (isLast) EditorInfo.IME_ACTION_DONE else EditorInfo.IME_ACTION_NEXT
            }

            edit.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    (v.background as? GradientDrawable)?.setStroke(dp(2), teal)
                    scrollContainer?.postDelayed({
                        val rect = android.graphics.Rect()
                        v.getDrawingRect(rect)
                        scrollContainer.offsetDescendantRectToMyCoords(v, rect)
                        val targetY = (rect.top - dp(72)).coerceAtLeast(0)
                        scrollContainer.smoothScrollTo(0, targetY)
                    }, 180)
                } else {
                    (v.background as? GradientDrawable)?.setStroke(dp(1), borderLight)
                }
            }

            edit.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_NEXT || actionId == EditorInfo.IME_ACTION_UNSPECIFIED) {
                    val nextField = fields.drop(idx + 1).firstOrNull { it.isEnabled && it.visibility == View.VISIBLE }
                    if (nextField != null) {
                        nextField.requestFocus()
                        true
                    } else {
                        hideKeyboard(edit)
                        false
                    }
                } else if (actionId == EditorInfo.IME_ACTION_DONE) {
                    hideKeyboard(edit)
                    if (onLastDone != null) {
                        onLastDone.invoke()
                        true
                    } else false
                } else false
            }
        }
    }

    private fun hideKeyboard(view: View) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun field(
        hint: String,
        type: String = "text",
        suggestionKeys: List<String> = emptyList()
    ): AutoCompleteTextView {
        return AutoCompleteTextView(this).apply {
            this.hint = hint
            textSize = 15f
            setTextColor(textDark)
            setHintTextColor(textMuted)
            setSingleLine(type != "multiline")
            if (type == "multiline") {
                minLines = 2
                gravity = Gravity.TOP or Gravity.START
            }
            inputType = when (type) {
                "phone" -> InputType.TYPE_CLASS_PHONE
                "email" -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                "number" -> InputType.TYPE_CLASS_NUMBER
                "decimal" -> InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                "password" -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                "multiline" -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                else -> InputType.TYPE_CLASS_TEXT
            }
            setPadding(dp(12), dp(11), dp(12), dp(11))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(11).toFloat()
                setStroke(dp(1), borderLight)
            }

            if (suggestionKeys.isNotEmpty() &&
                type != "password" && type != "number" && type != "decimal" && type != "multiline"
            ) {
                threshold = 1
                val adapter = ArrayAdapter<String>(this@MainActivity, android.R.layout.simple_dropdown_item_1line, mutableListOf())
                setAdapter(adapter)
                setDropDownVerticalOffset(dp(4))
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                    override fun afterTextChanged(editable: Editable?) {
                        val prefix = editable?.toString().orEmpty().trim()
                        if (prefix.isBlank()) {
                            adapter.clear()
                            dismissDropDown()
                            return
                        }
                        val matches = suggestionKeys.flatMap { db.suggestions(it, prefix) }.distinct().take(10)
                        adapter.clear()
                        adapter.addAll(matches)
                        adapter.notifyDataSetChanged()
                        if (matches.isNotEmpty() && hasFocus()) post { if (hasFocus()) showDropDown() } else dismissDropDown()
                    }
                })
            }
        }
    }

    private fun spinnerField(items: List<String>, selectedValue: String? = null): Spinner {
        return Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, items)
            if (!selectedValue.isNullOrBlank()) {
                val i = items.indexOf(selectedValue)
                if (i >= 0) setSelection(i)
            }
            setPadding(dp(8), dp(6), dp(8), dp(6))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(11).toFloat()
                setStroke(dp(1), borderLight)
            }
        }
    }

    private fun matchWidth() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

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
    // تسجيل الدخول وإنشاء المدير (كرت أبيض على خلفية كحلية)
    // ---------------------------------------------------------

    private fun showCreateAdmin() {
        page("إنشاء حساب المدير", "تهيئة النظام لأول مرة", showBack = false, showBottomNav = false, darkAuthBackground = true)
        val card = createCard("بيانات حساب المدير", gold, cardWhite)

        val username = field("اسم المستخدم")
        val name = field("اسم المدير")
        val password = field("كلمة المرور", "password")
        val confirm = field("تأكيد كلمة المرور", "password")

        listOf(username, name, password, confirm).forEach {
            card.addView(it, matchWidth().apply { setMargins(0, dp(4), 0, dp(6)) })
        }

        val submit = {
            if (username.text.isBlank() || name.text.isBlank() || password.text.length < 6) {
                toast("أكمل البيانات، وكلمة المرور 6 أحرف على الأقل")
            } else if (password.text.toString() != confirm.text.toString()) {
                toast("كلمتا المرور غير متطابقتين")
            } else if (db.createAdmin(username.text.toString(), name.text.toString(), password.text.toString())) {
                loggedUser = db.currentUser()
                if (loggedUser != null) navigateTo(Screen.TAB_HOME, clearStack = true)
                else navigateTo(Screen.LOGIN, clearStack = true)
            } else {
                toast("تعذر إنشاء الحساب")
            }
        }

        attachSmartKeyboardNavigation(listOf(username, name, password, confirm), contentScroll, submit)
        addPrimaryButton("إنشاء حساب المدير", card, teal, submit)
    }

    private fun showLogin() {
        db.clearActiveUser()
        loggedUser = null
        page("تسجيل الدخول", "مرحباً بك في الورشة", showBack = false, showBottomNav = false, darkAuthBackground = true)

        val card = createCard("بيانات الدخول", teal, cardWhite)
        val username = field("اسم المستخدم")
        val password = field("كلمة المرور", "password")

        listOf(username, password).forEach {
            card.addView(it, matchWidth().apply { setMargins(0, dp(5), 0, dp(7)) })
        }

        val doLogin = {
            if (db.authenticate(username.text.toString(), password.text.toString())) {
                val user = db.currentUser()
                if (user != null) {
                    loggedUser = user
                    navigateTo(Screen.TAB_HOME, clearStack = true)
                } else {
                    db.clearActiveUser()
                    toast("تعذر تحميل بيانات المستخدم")
                }
            } else {
                toast("اسم المستخدم أو كلمة المرور غير صحيحة")
            }
        }

        attachSmartKeyboardNavigation(listOf(username, password), contentScroll, doLogin)
        addPrimaryButton("تسجيل الدخول", card, teal, doLogin)
    }

    private fun logout() {
        db.clearActiveUser()
        loggedUser = null
        navigateTo(Screen.LOGIN, clearStack = true)
    }

    // ---------------------------------------------------------
    // الصفحة 1: لوحة التحكم Dashboard
    // ---------------------------------------------------------

    private fun showHomeDashboard() {
        val user = db.currentUser() ?: run { navigateTo(Screen.LOGIN, clearStack = true); return }
        loggedUser = user
        page("الرئيسية", "لوحة تحكم الورشة")

        // تنبيه نقص المخزون بكرت أحمر واضح إذا وُجد
        if (db.hasPermission("parts", "view")) {
            val lowParts = db.lowStockParts()
            if (lowParts.isNotEmpty()) {
                val alertCard = createCard(
                    "⚠ تنبيه نقص مخزون (${lowParts.size} قطع)",
                    Color.WHITE,
                    dangerRed
                )
                addText(
                    lowParts.take(4).joinToString("\n") { "• ${it.name}: المتبقي ${it.quantity} ${it.unit} (الحد الأدنى ${it.minQuantity})" },
                    13.5f,
                    Color.WHITE,
                    true,
                    alertCard
                )
                addSecondaryButton("عرض قطع تحتاج إعادة طلب", alertCard) {
                    currentTab = Tab.INVENTORY
                    navigateTo(Screen.TAB_INVENTORY, clearStack = true)
                }
            }
        }

        // الإحصائيات
        val statsCard = createCard("إحصائيات الورشة", teal)
        val statLine = mutableListOf<String>()
        if (db.hasPermission("customers", "view")) statLine += "العملاء: ${db.count("customers")}"
        if (db.hasPermission("vehicles", "view")) statLine += "السيارات: ${db.count("vehicles")}"
        if (db.hasPermission("work_orders", "view")) statLine += "الأوامر: ${db.count("work_orders")}"
        if (db.hasPermission("parts", "view")) statLine += "المخزون: ${db.count("parts")}"
        if (statLine.isNotEmpty()) addText(statLine.joinToString("  |  "), 14f, navyDark, true, statsCard)

        if (db.hasPermission("work_orders", "view")) {
            val openC = db.workOrderCountByStatus("مفتوح")
            val progC = db.workOrderCountByStatus("قيد الإصلاح") + db.workOrderCountByStatus("قيد العمل")
            val doneC = db.workOrderCountByStatus("مكتمل")
            addText("مفتوح: $openC  •  قيد الإصلاح: $progC  •  مكتمل: $doneC", 13f, textMuted, false, statsCard)
        }

        // بحث سريع فوري
        val quickSearchCard = createCard("بحث سريع فوري", gold)
        val quickInput = field("ابحث بالاسم، الهاتف، اللوحة، أو رقم الهيكل VIN", suggestionKeys = listOf("customer_name", "customer_phone", "plate", "vin"))
        quickSearchCard.addView(quickInput, matchWidth().apply { setMargins(0, 0, 0, dp(6)) })
        addPrimaryButton("بحث فوري", quickSearchCard, teal) {
            runQuickSearch(quickInput.text.toString())
        }
        addSecondaryButton("فتح صفحة البحث المتقدم", quickSearchCard) {
            navigateTo(Screen.SEARCH)
        }

        // إجراءات سريعة
        val actionsCard = createCard("إجراءات سريعة", teal)
        addPrimaryButton("استقبال سيارة والتحقق من الملكية", actionsCard, navyDark) {
            if (allowed("vehicles", "view")) navigateTo(Screen.RECEPTION)
        }
        if (db.hasPermission("work_orders", "add")) {
            addPrimaryButton("أمر شغل جديد", actionsCard, teal) { workOrderForm() }
        }
        if (db.hasPermission("customers", "add")) {
            addSecondaryButton("إضافة عميل جديد", actionsCard) { customerForm() }
        }
        if (db.hasPermission("vehicles", "add")) {
            addSecondaryButton("إضافة سيارة جديدة", actionsCard) { vehicleForm() }
        }

        // أحدث الأوامر والعملاء
        if (db.hasPermission("work_orders", "view")) {
            val recentWoCard = createCard("أحدث أوامر الشغل", navyDark)
            val recentOrders = db.getAllWorkOrders(limit = 4)
            if (recentOrders.isEmpty()) {
                addText("لا توجد أوامر شغل بعد.", 13.5f, textMuted, false, recentWoCard)
            } else {
                recentOrders.forEach { wo ->
                    addText(
                        "#${wo.id} • ${wo.customerName} (${wo.vehicleName}) — ${wo.status}\nالمتبقي: ${wo.remainingAmount} من ${wo.totalCost}",
                        13.5f,
                        textDark,
                        false,
                        recentWoCard
                    )
                    addSecondaryButton("فتح أمر #${wo.id}", recentWoCard) { workOrderDetailsDialog(wo.id) }
                }
            }
        }

        if (db.hasPermission("customers", "view")) {
            val recentCustCard = createCard("أحدث العملاء", gold)
            val recentCustomers = db.getAllCustomers(limit = 4)
            if (recentCustomers.isEmpty()) {
                addText("لا يوجد عملاء بعد.", 13.5f, textMuted, false, recentCustCard)
            } else {
                recentCustomers.forEach { c ->
                    val debtText = if (c.totalDebt > 0.0) " | دين: ${c.totalDebt}" else ""
                    addText("${c.name} — ${c.phone}$debtText", 13.5f, textDark, false, recentCustCard)
                    addSecondaryButton("ملف ${c.name}", recentCustCard) { showCustomerProfile(c.id) }
                }
            }
        }
    }

    private fun runQuickSearch(q: String) {
        val clean = q.trim()
        if (clean.isBlank()) {
            toast("أدخل نص البحث")
            return
        }
        val items = mutableListOf<Pair<String, () -> Unit>>()
        if (db.hasPermission("customers", "view")) {
            db.getAllCustomers(200).filter {
                it.name.contains(clean, true) || it.phone.contains(WorkshopDb.normalizePhone(clean).ifBlank { clean })
            }.forEach { c ->
                items += "عميل: ${c.name} (${c.phone})" to { showCustomerProfile(c.id) }
            }
        }
        if (db.hasPermission("vehicles", "view")) {
            db.searchVehicles(clean).forEach { v ->
                items += "سيارة: ${v.make} ${v.model} — ${v.plate} [${v.vin}]" to { showVehicleProfile(v.id) }
            }
        }
        if (items.isEmpty()) {
            toast("لا توجد نتائج مطابقة")
        } else {
            AlertDialog.Builder(this)
                .setTitle("نتائج البحث الفوري")
                .setItems(items.map { it.first }.toTypedArray()) { _, i -> items[i].second.invoke() }
                .setNegativeButton("إغلاق", null)
                .show()
        }
    }

    // ---------------------------------------------------------
    // الصفحة 2: العملاء
    // ---------------------------------------------------------

    private fun showCustomersScreen() {
        if (!allowed("customers", "view")) return
        page("العملاء", "إدارة ملفات العملاء والديون")

        val topCard = createCard("عمليات العملاء", teal)
        if (db.hasPermission("customers", "add")) {
            addPrimaryButton("+ إضافة عميل جديد", topCard, teal) { customerForm() }
        }
        val searchBox = field("بحث بالاسم أو رقم الهاتف", suggestionKeys = listOf("customer_name", "customer_phone"))
        topCard.addView(searchBox, matchWidth().apply { setMargins(0, dp(4), 0, dp(6)) })

        val listCard = createCard("قائمة العملاء", navyDark)
        fun populate(filterQuery: String) {
            listCard.removeAllViewsInLayout()
            val titleTv = TextView(this).apply {
                text = "قائمة العملاء"
                textSize = 15.5f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(navyDark)
                setPadding(dp(2), 0, dp(2), dp(8))
            }
            listCard.addView(titleTv, matchWidth())

            val q = filterQuery.trim()
            val normPhone = WorkshopDb.normalizePhone(q)
            val list = db.getAllCustomers(100).filter {
                q.isBlank() || it.name.contains(q, true) ||
                    (normPhone.isNotBlank() && it.phone.contains(normPhone)) ||
                    it.city.contains(q, true)
            }
            if (list.isEmpty()) {
                addText("لا توجد سجلات عملاء مطابقة.", 13.5f, textMuted, false, listCard)
                return
            }
            list.forEach { c ->
                addText(
                    "${c.name} (${c.customerType}) — ${c.phone}\nالمدينة: ${c.city.ifBlank { "-" }}  |  إجمالي الدين: ${c.totalDebt}",
                    13.5f,
                    textDark,
                    true,
                    listCard
                )
                addSecondaryButton("عرض ملف العميل", listCard) { showCustomerProfile(c.id) }
            }
        }

        populate("")
        searchBox.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) { populate(s?.toString().orEmpty()) }
        })
    }

    private fun showCustomerProfile(customerId: Long) {
        if (!allowed("customers", "view")) return
        val c = db.getCustomerProfile(customerId) ?: return

        val info =
            "الاسم: ${c.name} (${c.customerType})\n" +
                "الهاتف: ${c.phone}\n" +
                "هاتف إضافي: ${c.alternatePhones.ifBlank { "-" }}\n" +
                "الإيميل: ${c.email.ifBlank { "-" }}\n" +
                "المدينة: ${c.city.ifBlank { "-" }}\n" +
                "العنوان: ${c.address.ifBlank { "-" }}\n" +
                "إجمالي الدين المستحق: ${c.totalDebt}\n" +
                "ملاحظات: ${c.notes.ifBlank { "-" }}"

        val actions = mutableListOf<Pair<String, () -> Unit>>()
        if (db.hasPermission("vehicles", "view")) {
            val vCount = db.getCustomerVehicles(customerId).size
            actions += "سيارات العميل ($vCount)" to { showCustomerVehicles(customerId) }
        }
        if (db.hasPermission("vehicles", "add")) {
            actions += "+ إضافة سيارة للعميل" to { vehicleForm(customerId) }
        }
        if (db.hasPermission("work_orders", "view")) {
            actions += "سجل أوامر العميل" to { showCustomerWorkOrdersDialog(customerId) }
        }
        if (db.hasPermission("customers", "edit")) {
            actions += "تعديل بيانات العميل" to { editCustomerDialog(customerId) }
        }

        AlertDialog.Builder(this)
            .setTitle("ملف العميل")
            .setMessage(info)
            .setItems(actions.map { it.first }.toTypedArray()) { _, i -> actions[i].second.invoke() }
            .setPositiveButton("إغلاق", null)
            .show()
    }

    private fun editCustomerDialog(customerId: Long) {
        if (!allowed("customers", "edit")) return
        val cur = db.getCustomerProfile(customerId) ?: return

        val name = field("اسم العميل").apply { setText(cur.name) }
        val phone = field("رقم الهاتف", "phone").apply { setText(cur.phone) }
        val alt = field("هاتف إضافي", "phone").apply { setText(cur.alternatePhones) }
        val email = field("الإيميل", "email").apply { setText(cur.email) }
        val city = field("المدينة").apply { setText(cur.city) }
        val types = listOf("فرد", "شركة", "تاجر", "جهة حكومية")
        val typeSpinner = spinnerField(types, cur.customerType)
        val address = field("العنوان").apply { setText(cur.address) }
        val notes = field("ملاحظات", "multiline").apply { setText(cur.notes) }

        val views = listOf(name, phone, alt, email, city, typeSpinner, address, notes)
        formDialog("تعديل بيانات العميل", views, listOf(name, phone, alt, email, city, address, notes)) { dialog ->
            try {
                val ok = db.updateCustomerDetails(
                    customerId,
                    name.text.toString(),
                    phone.text.toString(),
                    alt.text.toString(),
                    address.text.toString(),
                    notes.text.toString(),
                    email.text.toString(),
                    city.text.toString(),
                    typeSpinner.selectedItem.toString()
                )
                if (ok) {
                    dialog.dismiss()
                    toast("تم تحديث بيانات العميل")
                    showCustomerProfile(customerId)
                } else toast("تعذر التحديث")
            } catch (e: Exception) {
                toast(e.message ?: "تعذر التحديث")
            }
        }
    }

    // ---------------------------------------------------------
    // الصفحة 3: السيارات والصور ونقل الملكية
    // ---------------------------------------------------------

    private fun showVehiclesScreen() {
        if (!allowed("vehicles", "view")) return
        page("السيارات", "ملفات السيارات، الصور، وسجل الملكية")

        val topCard = createCard("عمليات السيارات", teal)
        if (db.hasPermission("vehicles", "add")) {
            addPrimaryButton("+ إضافة سيارة جديدة", topCard, teal) { vehicleForm() }
        }
        addSecondaryButton("استقبال سيارة / التحقق من الملكية", topCard) { navigateTo(Screen.RECEPTION) }

        val searchBox = field("بحث باللوحة، VIN، الطراز، أو المالك", suggestionKeys = listOf("plate", "vin", "customer_name"))
        topCard.addView(searchBox, matchWidth().apply { setMargins(0, dp(4), 0, dp(6)) })

        val listCard = createCard("السيارات المسجلة", navyDark)
        fun populate(q: String) {
            listCard.removeAllViewsInLayout()
            listCard.addView(TextView(this).apply {
                text = "السيارات المسجلة"
                textSize = 15.5f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(navyDark)
                setPadding(dp(2), 0, dp(2), dp(8))
            }, matchWidth())

            val items = if (q.isBlank()) db.getAllCustomers(100).flatMap { db.getCustomerVehicles(it.id) }.distinctBy { it.id } else db.searchVehicles(q)
            if (items.isEmpty()) {
                addText("لا توجد سيارات مطابقة.", 13.5f, textMuted, false, listCard)
                return
            }
            items.forEach { v ->
                addText(
                    "${v.make} ${v.model} (${v.year}) — اللوحة: ${v.plate}\nVIN: ${v.vin}  |  المالك: ${v.ownerName}",
                    13.5f,
                    textDark,
                    true,
                    listCard
                )
                addSecondaryButton("فتح ملف السيارة", listCard) { showVehicleProfile(v.id) }
            }
        }

        populate("")
        searchBox.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) { populate(s?.toString().orEmpty()) }
        })
    }

    private fun showVehicleProfile(vehicleId: Long) {
        if (!allowed("vehicles", "view")) return
        val v = db.getVehicleProfile(vehicleId) ?: return

        val details =
            "السيارة: ${v.make} ${v.model} (${v.year})\n" +
                "المحرك: ${v.engine.ifBlank { "-" }}  |  اللون: ${v.color.ifBlank { "-" }}\n" +
                "الوقود: ${v.fuelType}  |  الناقل: ${v.transmission}\n" +
                "اللوحة: ${v.plate.ifBlank { "-" }}  |  العداد: ${v.odometer}\n" +
                "رقم الهيكل VIN: ${v.vin}\n" +
                "المالك الحالي: ${v.ownerName} (${v.ownerPhone})\n" +
                "عدد الصور المحفوظة: ${v.photos.size}\n" +
                "ملاحظات: ${v.notes.ifBlank { "-" }}"

        val actions = mutableListOf<Pair<String, () -> Unit>>()
        if (db.hasPermission("vehicles", "edit") || db.hasPermission("vehicles", "add")) {
            actions += "+ إضافة صورة للسيارة / الوقود" to {
                choosePhotoLabelAndAttach(vehicleId) { showVehicleProfile(vehicleId) }
            }
        }
        if (v.photos.isNotEmpty()) {
            actions += "عرض صور السيارة (${v.photos.size})" to { showVehiclePhotosGalleryDialog(v) }
        } else if (v.carPhotoPath.isNotBlank() || v.fuelGaugePhotoPath.isNotBlank()) {
            actions += "معاينة صورة السيارة" to { showPhotoPreview("صورة السيارة", v.carPhotoPath) }
            actions += "معاينة صورة مؤشر الوقود" to { showPhotoPreview("مؤشر الوقود", v.fuelGaugePhotoPath) }
        }
        if (db.hasPermission("work_orders", "add")) {
            actions += "+ أمر شغل جديد لهذه السيارة" to { workOrderForm(v.customerId, vehicleId) }
        }
        if (db.hasPermission("work_orders", "view")) {
            actions += "سجل أوامر السيارة" to { showVehicleWorkOrdersDialog(vehicleId) }
        }
        if (db.hasPermission("vehicles", "edit")) {
            actions += "نقل ملكية السيارة" to { transferOwnershipDialog(vehicleId, currentOwnerId = v.customerId) }
        }
        actions += "سجل ملاك السيارة" to { showOwnerHistory(vehicleId) }

        AlertDialog.Builder(this)
            .setTitle("ملف السيارة")
            .setMessage(details)
            .setItems(actions.map { it.first }.toTypedArray()) { _, i -> actions[i].second.invoke() }
            .setPositiveButton("إغلاق", null)
            .show()
    }

    private fun choosePhotoLabelAndAttach(vehicleId: Long, onDone: () -> Unit) {
        val labels = arrayOf("صورة السيارة", "صورة مؤشر الوقود", "صورة عداد / ملاحظة")
        AlertDialog.Builder(this)
            .setTitle("اختر نوع الصورة")
            .setItems(labels) { _, idx ->
                val chosenLabel = labels[idx]
                pickOrCapturePhoto(chosenLabel) { savedPath ->
                    db.addVehiclePhoto(vehicleId, chosenLabel, savedPath)
                    onDone()
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun showVehiclePhotosGalleryDialog(v: VehicleProfile) {
        val list = v.photos
        if (list.isEmpty()) {
            toast("لا توجد صور مضافة")
            return
        }
        val labels = list.map { "${it.label} — ${it.createdAt}" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("صور ${v.make} ${v.model}")
            .setItems(labels) { _, idx ->
                val item = list[idx]
                showPhotoPreview(item.label, item.photoPath)
            }
            .setNegativeButton("إغلاق", null)
            .show()
    }

    // ---------------------------------------------------------
    // الصفحة 4: أوامر الشغل، الدفعات الجزئية، الفاتورة، والفلترة
    // ---------------------------------------------------------

    private fun showWorkOrdersScreen() {
        if (!allowed("work_orders", "view")) return
        page("أوامر الشغل", "متابعة الإصلاحات، الدفعات، والفواتير")

        val filterCard = createCard("تصفية وإضافة أوامر الشغل", teal)
        if (db.hasPermission("work_orders", "add")) {
            addPrimaryButton("+ أمر شغل جديد", filterCard, teal) { workOrderForm() }
        }

        val filters = listOf("الكل", "مفتوح", "قيد الإصلاح", "مكتمل")
        val filterSpinner = spinnerField(filters, workOrderFilter)
        filterCard.addView(filterSpinner, matchWidth().apply { setMargins(0, dp(4), 0, dp(6)) })

        val listCard = createCard("قائمة الأوامر ($workOrderFilter)", navyDark)
        fun renderOrders(selectedFilter: String) {
            workOrderFilter = selectedFilter
            listCard.removeAllViewsInLayout()
            listCard.addView(TextView(this).apply {
                text = "قائمة الأوامر ($workOrderFilter)"
                textSize = 15.5f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(navyDark)
                setPadding(dp(2), 0, dp(2), dp(6))
            }, matchWidth())

            val orders = db.getAllWorkOrders(statusFilter = selectedFilter, limit = 80)
            if (orders.isEmpty()) {
                addText("لا توجد أوامر شغل في هذا التصنيف.", 13.5f, textMuted, false, listCard)
                return
            }

            orders.forEach { wo ->
                val itemBox = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(10), dp(10), dp(10), dp(10))
                    background = GradientDrawable().apply {
                        setColor(bgLight)
                        cornerRadius = dp(12).toFloat()
                        setStroke(dp(1), borderLight)
                    }
                }
                addStatusBadge(wo.status, itemBox)
                addText(
                    "أمر #${wo.id} • أولوية: ${wo.priority}\n" +
                        "العميل: ${wo.customerName}  |  السيارة: ${wo.vehicleName} (${wo.vehiclePlate})\n" +
                        "الشكوى: ${wo.complaint.ifBlank { "-" }}\n" +
                        "الإجمالي: ${wo.totalCost}  |  المدفوع: ${wo.paidAmount}  |  المتبقي: ${wo.remainingAmount} (${wo.paymentPercent}%)",
                    13f,
                    textDark,
                    false,
                    itemBox
                )

                val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                    max = 100
                    progress = wo.paymentPercent
                    progressTintList = android.content.res.ColorStateList.valueOf(teal)
                }
                itemBox.addView(progress, matchWidth().apply { setMargins(0, dp(2), 0, dp(6)) })

                addPrimaryButton("إدارة الأمر / دفعة / فاتورة", itemBox, navyDark) {
                    workOrderDetailsDialog(wo.id)
                }

                listCard.addView(itemBox, matchWidth().apply { setMargins(0, dp(4), 0, dp(8)) })
            }
        }

        renderOrders(workOrderFilter)
        filterSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                renderOrders(filters[position])
            }
        }
    }

    private fun workOrderDetailsDialog(workOrderId: Long) {
        val wo = db.getAllWorkOrders(limit = 500).firstOrNull { it.id == workOrderId } ?: return
        val payments = db.getWorkOrderPayments(workOrderId)

        val summary =
            "أمر شغل #${wo.id} — الحالة: ${wo.status} (${wo.priority})\n" +
                "العميل: ${wo.customerName} (${wo.customerPhone})\n" +
                "السيارة: ${wo.vehicleName} — اللوحة: ${wo.vehiclePlate}\n" +
                "الفني: ${wo.technicianName.ifBlank { "-" }}  |  التسليم المتوقع: ${wo.expectedDelivery.ifBlank { "-" }}\n" +
                "الشكوى: ${wo.complaint.ifBlank { "-" }}\n" +
                "التشخيص: ${wo.diagnosis.ifBlank { "-" }}\n" +
                "الإصلاح: ${wo.repairNotes.ifBlank { "-" }}\n\n" +
                "الأجور: ${wo.laborCost}  |  القطع: ${wo.partsCost}  |  الخصم: ${wo.discount}\n" +
                "الإجمالي: ${wo.totalCost}\n" +
                "المدفوع: ${wo.paidAmount} (${wo.paymentPercent}%)\n" +
                "المتبقي (الدين): ${wo.remainingAmount}\n" +
                "عدد الدفعات المسجلة: ${payments.size}"

        val actions = mutableListOf<Pair<String, () -> Unit>>()
        if (db.hasPermission("work_orders", "edit") || db.hasPermission("work_orders", "add")) {
            actions += "+ إضافة دفعة" to { addPaymentDialog(wo) }
        }
        if (db.hasPermission("work_orders", "edit")) {
            actions += "تعديل الحالة والتشخيص والتكاليف" to {
                editWorkOrderDialog(wo) { workOrderDetailsDialog(workOrderId) }
            }
        }
        actions += "طباعة / مشاركة الفاتورة" to { shareOrPrintInvoice(wo) }
        if (payments.isNotEmpty()) {
            actions += "عرض سجل الدفعات" to {
                val pText = payments.joinToString("\n\n") {
                    "المبلغ: ${it.amount} | الطريقة: ${it.method}\nالتاريخ: ${it.paidAt}\nملاحظة: ${it.note.ifBlank { "-" }}"
                }
                showMessage("دفعات أمر #${wo.id}", pText)
            }
        }

        AlertDialog.Builder(this)
            .setTitle("تفاصيل أمر الشغل #${wo.id}")
            .setMessage(summary)
            .setItems(actions.map { it.first }.toTypedArray()) { _, i -> actions[i].second.invoke() }
            .setPositiveButton("إغلاق", null)
            .show()
    }

    private fun addPaymentDialog(wo: WorkOrderRecord) {
        val amountInput = field("مبلغ الدفعة (المتبقي: ${wo.remainingAmount})", "decimal")
        val methods = listOf("نقدي", "تحويل", "بطاقة", "آجل")
        val methodSpinner = spinnerField(methods, "نقدي")
        val noteInput = field("ملاحظة الدفعة")

        val views = listOf(amountInput, methodSpinner, noteInput)
        formDialog("إضافة دفعة لأمر #${wo.id}", views, listOf(amountInput, noteInput)) { dialog ->
            try {
                val amt = amountInput.text.toString().trim().toDoubleOrNull()
                    ?: throw IllegalArgumentException("أدخل مبلغاً صحيحاً")
                val ok = db.addWorkOrderPayment(wo.id, amt, methodSpinner.selectedItem.toString(), noteInput.text.toString())
                if (ok) {
                    dialog.dismiss()
                    toast("تم تسجيل الدفعة")
                    workOrderDetailsDialog(wo.id)
                } else toast("تعذر تسجيل الدفعة")
            } catch (e: Exception) {
                toast(e.message ?: "تعذر تسجيل الدفعة")
            }
        }
    }

    private fun buildInvoiceText(wo: WorkOrderRecord): String {
        return """
            ====================================
            فاتورة أمر شغل — خبراء هونداي وفورد
            رقم الأمر: #${wo.id} | التاريخ: ${wo.createdAt}
            ====================================
            العميل: ${wo.customerName} (${wo.customerPhone})
            السيارة: ${wo.vehicleName} — اللوحة: ${wo.vehiclePlate}
            الشكوى: ${wo.complaint.ifBlank { "-" }}
            التشخيص/الإصلاح: ${wo.diagnosis.ifBlank { "-" }} / ${wo.repairNotes.ifBlank { "-" }}
            ------------------------------------
            أجور العمل: ${wo.laborCost}
            تكلفة القطع: ${wo.partsCost}
            الخصم: ${wo.discount}
            الإجمالي: ${wo.totalCost}
            المدفوع: ${wo.paidAmount}
            المتبقي: ${wo.remainingAmount}
            ====================================
        """.trimIndent()
    }

    private fun shareOrPrintInvoice(wo: WorkOrderRecord) {
        val invoiceText = buildInvoiceText(wo)
        AlertDialog.Builder(this)
            .setTitle("فاتورة أمر #${wo.id}")
            .setMessage(invoiceText)
            .setPositiveButton("مشاركة (واتساب / تطبيقات)") { _, _ ->
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "فاتورة أمر شغل #${wo.id} - خبراء هونداي وفورد")
                    putExtra(Intent.EXTRA_TEXT, invoiceText)
                }
                startActivity(Intent.createChooser(sendIntent, "مشاركة الفاتورة"))
            }
            .setNeutralButton("طباعة") { _, _ ->
                printPlainText("Invoice_WO_${wo.id}", invoiceText)
            }
            .setNegativeButton("إغلاق", null)
            .show()
    }

    private fun printPlainText(jobName: String, text: String) {
        try {
            val printManager = getSystemService(Context.PRINT_SERVICE) as? PrintManager
            if (printManager == null) {
                toast("خدمة الطباعة غير متوفرة؛ استخدم المشاركة")
                return
            }
            val adapter = object : android.print.PrintDocumentAdapter() {
                override fun onLayout(
                    oldAttributes: PrintAttributes?,
                    newAttributes: PrintAttributes?,
                    cancellationSignal: android.os.CancellationSignal?,
                    callback: LayoutResultCallback?,
                    extras: Bundle?
                ) {
                    val info = android.print.PrintDocumentInfo.Builder("$jobName.pdf")
                        .setContentType(android.print.PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .build()
                    callback?.onLayoutFinished(info, true)
                }

                override fun onWrite(
                    pages: Array<out android.print.PageRange>?,
                    destination: android.os.ParcelFileDescriptor?,
                    cancellationSignal: android.os.CancellationSignal?,
                    callback: WriteResultCallback?
                ) {
                    val pdfDocument = android.graphics.pdf.PdfDocument()
                    val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, 1).create()
                    val page = pdfDocument.startPage(pageInfo)
                    val paint = android.graphics.Paint().apply {
                        textSize = 12f
                        color = Color.BLACK
                    }
                    var y = 48f
                    text.lines().forEach { line ->
                        page.canvas.drawText(line, 36f, y, paint)
                        y += 20f
                    }
                    pdfDocument.finishPage(page)
                    destination?.fileDescriptor?.let { fd ->
                        FileOutputStream(fd).use { out -> pdfDocument.writeTo(out) }
                    }
                    pdfDocument.close()
                    callback?.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES))
                }
            }
            printManager.print(jobName, adapter, PrintAttributes.Builder().build())
        } catch (_: Exception) {
            toast("تعذر تشغيل الطباعة؛ يمكنك مشاركة الفاتورة مباشرة")
        }
    }

    // ---------------------------------------------------------
    // الصفحة 5: المخزون ونقص المخزون وإضافة/خصم الكميات
    // ---------------------------------------------------------

    private fun showInventoryScreen() {
        if (!allowed("parts", "view")) return
        page("المخزون", "قطع الغيار، إعادة الطلب، وتعديل الكميات")

        val topCard = createCard("إدارة المخزون", teal)
        if (db.hasPermission("parts", "add")) {
            addPrimaryButton("+ إضافة قطعة غيار جديدة", topCard, teal) { partForm() }
        }

        val allParts = db.getAllParts(200)
        val lowParts = allParts.filter { it.isLowStock }

        if (lowParts.isNotEmpty()) {
            val reorderCard = createCard("قطع تحتاج إعادة طلب (${lowParts.size})", dangerRed, cardWhite)
            lowParts.forEach { p ->
                addStatusBadge("ناقص", reorderCard)
                addText(
                    "${p.name} (${p.partNumber.ifBlank { "-" }})\nالكمية الحالية: ${p.quantity} ${p.unit}  |  الحد الأدنى: ${p.minQuantity}  |  المورد: ${p.supplier.ifBlank { "-" }}",
                    13.5f,
                    dangerRed,
                    true,
                    reorderCard
                )
                addSecondaryButton("تفاصيل وتعديل كمية ${p.name}", reorderCard) { partDetailsDialog(p.id) }
            }
        }

        val allCard = createCard("جميع قطع الغيار (${allParts.size})", navyDark)
        if (allParts.isEmpty()) {
            addText("لا توجد قطع مسجلة في المخزون.", 13.5f, textMuted, false, allCard)
            return
        }
        allParts.forEach { p ->
            if (p.isLowStock) addStatusBadge("ناقص", allCard)
            addText(
                "${p.name} — رقم: ${p.partNumber.ifBlank { "-" }} — باركود: ${p.barcode.ifBlank { "-" }}\n" +
                    "الكمية: ${p.quantity} ${p.unit}  |  الرف: ${p.shelfLocation.ifBlank { "-" }}  |  البيع: ${p.salePrice}",
                13.5f,
                textDark,
                false,
                allCard
            )
            addSecondaryButton("إدارة / إضافة / خصم كمية", allCard) { partDetailsDialog(p.id) }
        }
    }

    private fun partDetailsDialog(partId: Long) {
        val p = db.getAllParts(500).firstOrNull { it.id == partId } ?: return
        val warningPrefix = if (p.isLowStock) "⚠ تحذير: الكمية وصلت إلى الحد الأدنى أو أقل!\n\n" else ""
        val info = warningPrefix +
            "القطعة: ${p.name}\n" +
            "رقم القطعة: ${p.partNumber.ifBlank { "-" }}\n" +
            "الباركود: ${p.barcode.ifBlank { "-" }}\n" +
            "الكمية الحالية: ${p.quantity} ${p.unit}\n" +
            "الحد الأدنى: ${p.minQuantity} ${p.unit}\n" +
            "سعر الشراء: ${p.purchasePrice}  |  سعر البيع: ${p.salePrice}\n" +
            "المورد: ${p.supplier.ifBlank { "-" }}  |  الرف: ${p.shelfLocation.ifBlank { "-" }}"

        val builder = AlertDialog.Builder(this)
            .setTitle("تفاصيل القطعة")
            .setMessage(info)

        if (db.hasPermission("parts", "edit")) {
            builder.setPositiveButton("إضافة / خصم كمية") { _, _ -> adjustPartDialog(p) }
        }
        builder.setNegativeButton("إغلاق", null).show()
    }

    private fun adjustPartDialog(part: PartItem) {
        val qtyInput = field("أدخل الكمية (مثلاً 2 أو 5)", "decimal")
        val modeSpinner = spinnerField(listOf("إضافة للمخزون (+)", "خصم من المخزون (-)"), "إضافة للمخزون (+)")

        formDialog("تعديل كمية: ${part.name}", listOf(modeSpinner, qtyInput), listOf(qtyInput)) { dialog ->
            val raw = qtyInput.text.toString().trim().toDoubleOrNull()
            if (raw == null || raw <= 0.0) {
                toast("أدخل كمية موجبة")
                return@formDialog
            }
            val delta = if (modeSpinner.selectedItem.toString().startsWith("خصم")) -raw else raw
            if (db.adjustPartQuantity(part.id, delta)) {
                dialog.dismiss()
                toast("تم تحديث الكمية")
                showInventoryScreen()
            } else {
                toast("تعذر تحديث الكمية")
            }
        }
    }

    // ---------------------------------------------------------
    // الصفحة 6: المزيد (التقارير، البحث المتقدم، النسخ الاحتياطي، المستخدمون)
    // ---------------------------------------------------------

    private fun showMoreScreen() {
        val user = db.currentUser() ?: run { navigateTo(Screen.LOGIN, clearStack = true); return }
        loggedUser = user
        page("المزيد", "التقارير، البحث، النسخ الاحتياطي، والمستخدمون")

        val toolsCard = createCard("الأدوات والتقارير", teal)
        if (db.hasPermission("work_orders", "view")) {
            addPrimaryButton("التقرير اليومي وأكبر الديون", toolsCard, teal) { navigateTo(Screen.REPORTS) }
        }
        addSecondaryButton("البحث المتقدم الشامل", toolsCard) { navigateTo(Screen.SEARCH) }
        addPrimaryButton("تصدير نسخة احتياطية ومشاركتها", toolsCard, navyDark) { exportAndShareBackup() }

        val acctCard = createCard("حسابي وإدارة المستخدمين", gold)
        addSecondaryButton("تعديل اسمي أو كلمة مروري", acctCard) { editOwnAccountDialog(user) }
        if (user.role == "مدير" || db.hasPermission("users", "view")) {
            addPrimaryButton("إدارة المستخدمين والصلاحيات", acctCard, navyDark) { navigateTo(Screen.USERS) }
        }

        addDangerButton("تسجيل الخروج", root) { logout() }
    }

    private fun exportAndShareBackup() {
        try {
            val file = db.exportBackupFile()
            lastBackupFile = file
            toast("تم حفظ النسخة الاحتياطية: ${file.name}")

            val uri = Uri.fromFile(file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_SUBJECT, "نسخة احتياطية - خبراء هونداي وفورد")
                putExtra(Intent.EXTRA_TEXT, "نسخة احتياطية من بيانات الورشة (${file.name})")
                putExtra(Intent.EXTRA_STREAM, uri)
            }
            startActivity(Intent.createChooser(shareIntent, "مشاركة النسخة الاحتياطية"))
        } catch (_: Exception) {
            toast("تعذر إنشاء النسخة الاحتياطية")
        }
    }

    // ---------------------------------------------------------
    // التقرير اليومي وأكبر الديون
    // ---------------------------------------------------------

    private fun reportsScreen(initialDate: String) {
        if (!allowed("work_orders", "view")) return
        page("التقرير اليومي والديون", "اختر التاريخ YYYY-MM-DD", showBack = true)

        val dateCard = createCard("تاريخ التقرير", teal)
        val dateInput = field("التاريخ (مثال ${WorkshopDb.todayString()})").apply { setText(initialDate) }
        dateCard.addView(dateInput, matchWidth().apply { setMargins(0, 0, 0, dp(6)) })

        val summaryCard = createCard("ملخص التقرير اليومي", navyDark)
        val debtCard = createCard("أكبر الديون المستحقة", warnAmber)

        fun loadReportFor(dateStr: String) {
            val clean = dateStr.trim().ifBlank { WorkshopDb.todayString() }
            val r = db.getDailyReport(clean)

            summaryCard.removeAllViewsInLayout()
            summaryCard.addView(TextView(this).apply {
                text = "ملخص التقرير (${r.date})"
                textSize = 15.5f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(navyDark)
                setPadding(dp(2), 0, dp(2), dp(6))
            }, matchWidth())

            addText(
                "أوامر اليوم: ${r.ordersCount}  |  المكتملة: ${r.completedCount}\n" +
                    "مفتوح: ${r.openCount}  |  قيد الإصلاح: ${r.inProgressCount}\n" +
                    "إجمالي الفواتير: ${r.totalBilled}\n" +
                    "الأجور: ${r.laborTotal}  |  القطع: ${r.partsTotal}  |  الخصومات: ${r.discountsTotal}\n" +
                    "المدفوعات: ${r.collectedTotal}\n" +
                    "إجمالي الديون المتبقية: ${r.remainingDebtTotal}",
                13.5f,
                textDark,
                true,
                summaryCard
            )

            debtCard.removeAllViewsInLayout()
            debtCard.addView(TextView(this).apply {
                text = "أكبر الديون المستحقة"
                textSize = 15.5f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(warnAmber)
                setPadding(dp(2), 0, dp(2), dp(6))
            }, matchWidth())

            val debts = db.getTopDebts(15)
            if (debts.isEmpty()) {
                addText("لا توجد ديون متبقية حالياً.", 13.5f, textMuted, false, debtCard)
            } else {
                debts.forEachIndexed { i, d ->
                    addText(
                        "${i + 1}. ${d.customerName} (${d.customerPhone}) — المتبقي: ${d.debtAmount}",
                        13.5f,
                        textDark,
                        true,
                        debtCard
                    )
                    addSecondaryButton("ملف ${d.customerName}", debtCard) { showCustomerProfile(d.customerId) }
                }
            }
        }

        addPrimaryButton("عرض تقرير التاريخ", dateCard, teal) { loadReportFor(dateInput.text.toString()) }
        loadReportFor(initialDate)
    }

    // ---------------------------------------------------------
    // البحث المتقدم الشامل
    // ---------------------------------------------------------

    private fun advancedSearchScreen() {
        page("البحث المتقدم", "ابحث في الكل أو قسم محدد", showBack = true)

        val card = createCard("معايير البحث", teal)
        val scopes = listOf("الكل", "عملاء", "سيارات", "أوامر", "قطع")
        val scopeSpinner = spinnerField(scopes, "الكل")
        val queryInput = field("أدخل الاسم، الهاتف، اللوحة، VIN، رقم الأمر، أو القطعة")

        card.addView(scopeSpinner, matchWidth().apply { setMargins(0, 0, 0, dp(6)) })
        card.addView(queryInput, matchWidth().apply { setMargins(0, 0, 0, dp(6)) })

        val resultsCard = createCard("النتائج", navyDark)

        val executeSearch = {
            val q = queryInput.text.toString().trim()
            val scope = scopeSpinner.selectedItem.toString()
            resultsCard.removeAllViewsInLayout()
            resultsCard.addView(TextView(this).apply {
                text = "النتائج ($scope)"
                textSize = 15.5f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(navyDark)
                setPadding(dp(2), 0, dp(2), dp(6))
            }, matchWidth())

            if (q.isBlank()) {
                addText("اكتب كلمة للبحث.", 13.5f, textMuted, false, resultsCard)
            } else {
                val normPhone = WorkshopDb.normalizePhone(q)
                var countFound = 0

                if ((scope == "الكل" || scope == "عملاء") && db.hasPermission("customers", "view")) {
                    db.getAllCustomers(200).filter {
                        it.name.contains(q, true) || (normPhone.isNotBlank() && it.phone.contains(normPhone)) || it.city.contains(q, true)
                    }.forEach { c ->
                        countFound++
                        addText("[عميل] ${c.name} — ${c.phone} (${c.city})", 13.5f, textDark, true, resultsCard)
                        addSecondaryButton("فتح ملف العميل", resultsCard) { showCustomerProfile(c.id) }
                    }
                }

                if ((scope == "الكل" || scope == "سيارات") && db.hasPermission("vehicles", "view")) {
                    db.searchVehicles(q).forEach { v ->
                        countFound++
                        addText("[سيارة] ${v.make} ${v.model} — اللوحة: ${v.plate} — VIN: ${v.vin}", 13.5f, textDark, true, resultsCard)
                        addSecondaryButton("فتح ملف السيارة", resultsCard) { showVehicleProfile(v.id) }
                    }
                }

                if ((scope == "الكل" || scope == "أوامر") && db.hasPermission("work_orders", "view")) {
                    db.getAllWorkOrders(limit = 200).filter {
                        it.id.toString() == q.removePrefix("#") ||
                            it.customerName.contains(q, true) ||
                            it.vehicleName.contains(q, true) ||
                            it.complaint.contains(q, true)
                    }.forEach { w ->
                        countFound++
                        addText("[أمر #${w.id}] ${w.customerName} — ${w.vehicleName} (${w.status})", 13.5f, textDark, true, resultsCard)
                        addSecondaryButton("فتح أمر #${w.id}", resultsCard) { workOrderDetailsDialog(w.id) }
                    }
                }

                if ((scope == "الكل" || scope == "قطع") && db.hasPermission("parts", "view")) {
                    db.getAllParts(200).filter {
                        it.name.contains(q, true) ||
                            it.partNumber.contains(q, true) ||
                            it.barcode.contains(q, true) ||
                            it.supplier.contains(q, true)
                    }.forEach { p ->
                        countFound++
                        addText("[قطعة] ${p.name} — رقم: ${p.partNumber} — الكمية: ${p.quantity}", 13.5f, textDark, true, resultsCard)
                        addSecondaryButton("فتح تفاصيل القطعة", resultsCard) { partDetailsDialog(p.id) }
                    }
                }

                if (countFound == 0) {
                    addText("لم يتم العثور على نتائج مطابقة.", 13.5f, textMuted, false, resultsCard)
                }
            }
        }

        attachSmartKeyboardNavigation(listOf(queryInput), contentScroll, executeSearch)
        addPrimaryButton("بحث الآن", card, teal, executeSearch)
    }

    // ---------------------------------------------------------
    // الاستقبال والتحقق من الملكية
    // ---------------------------------------------------------

    private fun receptionScreen() {
        if (!allowed("vehicles", "view")) return
        page("استقبال سيارة", "تحقق بالهاتف ورقم الهيكل أو اللوحة", showBack = true)

        val card = createCard("بيانات الاستقبال", teal)
        val phone = field("رقم هاتف العميل", "phone", listOf("customer_phone"))
        val identifier = field("رقم الهيكل VIN أو رقم اللوحة", suggestionKeys = listOf("vin", "plate"))

        card.addView(phone, matchWidth().apply { setMargins(0, 0, 0, dp(6)) })
        card.addView(identifier, matchWidth().apply { setMargins(0, 0, 0, dp(6)) })

        val doCheck = { receptionSearch(phone.text.toString(), identifier.text.toString()) }
        attachSmartKeyboardNavigation(listOf(phone, identifier), contentScroll, doCheck)
        addPrimaryButton("بحث والتحقق من البيانات", card, teal, doCheck)
    }

    private fun receptionSearch(phone: String, identifier: String) {
        val cleanPhone = WorkshopDb.normalizePhone(phone)
        val cleanIdentifier = identifier.trim()
        if (cleanPhone.isBlank() || cleanIdentifier.isBlank()) {
            toast("أدخل رقم الهاتف ورقم الهيكل أو اللوحة")
            return
        }

        val customers = db.searchReception(cleanPhone).filter { it.type == "customer" }.distinctBy { it.id }
        val normId = WorkshopDb.normalizeVin(cleanIdentifier)
        val vehicles = db.searchVehicles(cleanIdentifier).filter { v ->
            (normId.length == 17 && v.vin.equals(normId, ignoreCase = true)) ||
                v.plate.contains(cleanIdentifier, ignoreCase = true)
        }.distinctBy { it.id }

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
                        toast("تعذر تحميل البيانات")
                    } else if (vehicle.customerId == customer.id) {
                        showMessage("تم التحقق من الملكية", "العميل: ${customer.name}\nالسيارة: ${vehicle.make} ${vehicle.model}\nاللوحة: ${vehicle.plate}")
                        receptionChoices(customer.id, vehicle.id)
                    } else {
                        ownershipConflict(customer.id, vehicle.id)
                    }
                }
                customerId != null -> {
                    val customer = db.getCustomerProfile(customerId) ?: return
                    val cVehicles = db.getCustomerVehicles(customerId)
                    val choices = mutableListOf<String>()
                    if (cVehicles.isNotEmpty() && db.hasPermission("vehicles", "view")) choices += "عرض سيارات العميل (${cVehicles.size})"
                    if (db.hasPermission("vehicles", "add")) choices += "إضافة سيارة جديدة لهذا العميل"
                    if (choices.isEmpty()) toast("لا توجد صلاحية")
                    else AlertDialog.Builder(this)
                        .setTitle("العميل موجود — ${customer.name}")
                        .setItems(choices.toTypedArray()) { _, i ->
                            if (choices[i].startsWith("عرض")) showCustomerVehicles(customer.id) else vehicleForm(customer.id)
                        }
                        .setNegativeButton("إلغاء", null)
                        .show()
                }
                vehicleId != null -> {
                    val vehicle = db.getVehicleProfile(vehicleId) ?: return
                    showMessage("السيارة موجودة", "المالك الحالي: ${vehicle.ownerName}\nالهاتف: ${vehicle.ownerPhone}")
                    newCustomerForExistingVehicle(vehicle.id)
                }
            }
        }

        fun chooseVehicle(customerId: Long?) {
            when {
                vehicles.isEmpty() -> continueWith(customerId, null)
                vehicles.size == 1 -> continueWith(customerId, vehicles.first().id)
                else -> AlertDialog.Builder(this)
                    .setTitle("اختر السيارة")
                    .setItems(vehicles.map { "${it.make} ${it.model} — ${it.plate}" }.toTypedArray()) { _, i ->
                        continueWith(customerId, vehicles[i].id)
                    }
                    .setNegativeButton("إلغاء", null)
                    .show()
            }
        }

        when {
            customers.isEmpty() -> chooseVehicle(null)
            customers.size == 1 -> chooseVehicle(customers.first().id)
            else -> AlertDialog.Builder(this)
                .setTitle("اختر العميل")
                .setItems(customers.map { "${it.title}\n${it.subtitle}" }.toTypedArray()) { _, i ->
                    chooseVehicle(customers[i].id)
                }
                .setNegativeButton("إلغاء", null)
                .show()
        }
    }

    private fun receptionChoices(customerId: Long, vehicleId: Long) {
        AlertDialog.Builder(this)
            .setTitle("متابعة الاستقبال")
            .setItems(arrayOf("فتح ملف السيارة", "بدء أمر شغل جديد")) { _, which ->
                if (which == 0) showVehicleProfile(vehicleId) else workOrderForm(customerId, vehicleId)
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun ownershipConflict(newCustomerId: Long, vehicleId: Long) {
        val vehicle = db.getVehicleProfile(vehicleId) ?: return
        AlertDialog.Builder(this)
            .setTitle("تعارض في الملكية")
            .setMessage("السيارة مسجلة حالياً باسم: ${vehicle.ownerName}\nلن يتم تغيير الملكية تلقائياً.")
            .setItems(arrayOf("الإبقاء على المالك الحالي", "نقل الملكية إلى العميل الجديد", "إلغاء")) { _, which ->
                when (which) {
                    0 -> receptionChoices(vehicle.customerId, vehicleId)
                    1 -> transferOwnershipDialog(vehicleId, newCustomerId)
                }
            }
            .show()
    }

    private fun transferOwnershipDialog(vehicleId: Long, newCustomerId: Long? = null, currentOwnerId: Long? = null) {
        if (!allowed("vehicles", "edit")) return
        val reason = field("سبب نقل الملكية", "multiline")

        var targetCustomerId = newCustomerId
        val candidates = db.customerOptions().filter { it.first != currentOwnerId }
        val spinner = if (targetCustomerId == null) {
            if (candidates.isEmpty()) {
                toast("لا يوجد عميل آخر للنقل إليه")
                return
            }
            spinnerField(candidates.map { it.second })
        } else null

        val views = if (spinner != null) listOf(spinner, reason) else listOf(reason)
        formDialog("نقل ملكية السيارة", views, listOf(reason)) { dialog ->
            if (reason.text.isBlank()) {
                toast("أدخل سبب نقل الملكية")
                return@formDialog
            }
            if (targetCustomerId == null && spinner != null) {
                targetCustomerId = candidates.getOrNull(spinner.selectedItemPosition)?.first
            }
            val destId = targetCustomerId ?: return@formDialog
            if (db.transferVehicleOwnership(vehicleId, destId, reason.text.toString())) {
                dialog.dismiss()
                toast("تم نقل الملكية")
                showVehicleProfile(vehicleId)
            } else {
                toast("تعذر نقل الملكية")
            }
        }
    }

    private fun newCustomerForExistingVehicle(vehicleId: Long) {
        if (!allowed("customers", "add")) return
        val name = field("اسم العميل الجديد", suggestionKeys = listOf("customer_name"))
        val phone = field("رقم الهاتف", "phone", listOf("customer_phone"))
        val alt = field("هاتف إضافي", "phone")
        val address = field("العنوان")

        formDialog("تسجيل عميل جديد", listOf(name, phone, alt, address), listOf(name, phone, alt, address)) { dialog ->
            try {
                val id = db.addCustomer(name.text.toString(), phone.text.toString(), alt.text.toString(), address.text.toString())
                dialog.dismiss()
                ownershipConflict(id, vehicleId)
            } catch (e: Exception) {
                toast(e.message ?: "تعذر إضافة العميل")
            }
        }
    }

    private fun newCustomerAndVehicleForm(phoneValue: String, identifierValue: String) {
        if (!allowed("customers", "add") || !allowed("vehicles", "add")) return

        val name = field("اسم العميل", suggestionKeys = listOf("customer_name"))
        val phone = field("رقم الهاتف", "phone", listOf("customer_phone")).apply { setText(phoneValue) }
        val alt = field("هاتف إضافي", "phone")
        val email = field("الإيميل", "email")
        val city = field("المدينة")
        val custTypeSpinner = spinnerField(listOf("فرد", "شركة", "تاجر", "جهة حكومية"))
        val address = field("العنوان")
        val customerNotes = field("ملاحظات العميل", "multiline")

        val make = field("الشركة / الصانع")
        val model = field("طراز السيارة")
        val year = field("سنة الصنع", "number")
        val engine = field("نوع المحرك")
        val color = field("لون السيارة")
        val fuelSpinner = spinnerField(listOf("بنزين", "ديزل", "هجين", "كهرباء", "غاز"))
        val transSpinner = spinnerField(listOf("أوتوماتيك", "عادي", "CVT"))
        val plate = field("رقم اللوحة", suggestionKeys = listOf("plate"))
        val vin = field("رقم الهيكل VIN", suggestionKeys = listOf("vin"))
        val odometer = field("قراءة العداد", "number")
        val vehicleNotes = field("ملاحظات السيارة", "multiline")

        if (identifierValue.length == 17) vin.setText(identifierValue) else plate.setText(identifierValue)

        var carPhoto = ""
        var fuelPhoto = ""

        val carPhotoBtn = Button(this).apply {
            text = "إرفاق صورة السيارة"
            setTextColor(Color.WHITE)
            background = buttonBg(teal, teal, tealDark)
            setOnClickListener { pickOrCapturePhoto("صورة السيارة") { carPhoto = it } }
        }
        val fuelPhotoBtn = Button(this).apply {
            text = "إرفاق صورة مؤشر الوقود"
            setTextColor(Color.WHITE)
            background = buttonBg(gold, gold, navyDark)
            setOnClickListener { pickOrCapturePhoto("صورة مؤشر الوقود") { fuelPhoto = it } }
        }

        val textInputs = listOf(name, phone, alt, email, city, address, customerNotes, make, model, year, engine, color, plate, vin, odometer, vehicleNotes)
        val views = listOf(
            name, phone, alt, email, city, custTypeSpinner, address, customerNotes,
            make, model, year, engine, color, fuelSpinner, transSpinner, plate, vin, odometer, vehicleNotes,
            carPhotoBtn, fuelPhotoBtn
        )

        formDialog("عميل وسيارة جديدة", views, textInputs) { dialog ->
            if (name.text.isBlank() || phone.text.isBlank() || make.text.isBlank() || model.text.isBlank() || vin.text.isBlank()) {
                toast("أكمل بيانات العميل والسيارة ورقم الهيكل VIN")
                return@formDialog
            }
            val res = db.addCustomerAndVehicle(
                name.text.toString(), phone.text.toString(), alt.text.toString(), address.text.toString(),
                make.text.toString(), model.text.toString(), year.text.toString(), engine.text.toString(),
                plate.text.toString(), vin.text.toString(), odometer.text.toString(),
                customerNotes.text.toString(), vehicleNotes.text.toString(),
                email.text.toString(), city.text.toString(), custTypeSpinner.selectedItem.toString(),
                color.text.toString(), fuelSpinner.selectedItem.toString(), transSpinner.selectedItem.toString(),
                carPhoto, fuelPhoto
            )
            if (res == null) toast("تعذر الحفظ. تحقق من رقم الهيكل وعدم تكرار البيانات")
            else {
                dialog.dismiss()
                toast("تم التسجيل بنجاح")
                receptionChoices(res.first, res.second)
            }
        }
    }

    // ---------------------------------------------------------
    // نماذج الإدخال الموحدة (مع تنبيه فوري للتكرار وتعويم الحقول)
    // ---------------------------------------------------------

    private fun formDialog(
        title: String,
        views: List<View>,
        focusableInputs: List<AutoCompleteTextView> = emptyList(),
        save: (AlertDialog) -> Unit
    ) {
        activeFormDialog?.dismiss()

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(28))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        views.forEach { v ->
            box.addView(v, matchWidth().apply { setMargins(0, dp(4), 0, dp(7)) })
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            isSmoothScrollingEnabled = true
        }
        scroll.addView(box)
        activeFormScroll = scroll

        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(scroll)
            .setPositiveButton("حفظ", null)
            .setNegativeButton("إلغاء", null)
            .create()

        activeFormDialog = dialog
        val submitAction = { save(dialog) }
        activeFormSubmit = submitAction

        attachSmartKeyboardNavigation(focusableInputs, scroll, submitAction)

        dialog.setOnDismissListener {
            if (activeFormDialog === dialog) {
                activeFormDialog = null
                activeFormScroll = null
                activeFormSubmit = null
            }
        }
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { submitAction() }
        }
        dialog.show()
    }

    private fun customerForm() {
        if (!allowed("customers", "add")) return

        val name = field("اسم العميل", suggestionKeys = listOf("customer_name"))
        val phone = field("رقم الهاتف", "phone", listOf("customer_phone"))
        val alt = field("هاتف إضافي", "phone")
        val email = field("الإيميل", "email")
        val city = field("المدينة")
        val typeSpinner = spinnerField(listOf("فرد", "شركة", "تاجر", "جهة حكومية"))
        val address = field("العنوان")
        val notes = field("ملاحظات", "multiline")

        // تنبيه فوري إذا رقم الهاتف مكرر
        phone.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val p = WorkshopDb.normalizePhone(s?.toString().orEmpty())
                if (p.length >= 6 && db.suggestions("customer_phone", p).any { it == p }) {
                    phone.error = "تنبيه: رقم الهاتف مسجل مسبقاً"
                } else {
                    phone.error = null
                }
            }
        })

        val inputs = listOf(name, phone, alt, email, city, address, notes)
        formDialog("إضافة عميل", listOf(name, phone, alt, email, city, typeSpinner, address, notes), inputs) { dialog ->
            try {
                db.addCustomer(
                    name.text.toString(), phone.text.toString(), alt.text.toString(),
                    address.text.toString(), notes.text.toString(),
                    email.text.toString(), city.text.toString(), typeSpinner.selectedItem.toString()
                )
                dialog.dismiss()
                toast("تم حفظ العميل")
                navigateTo(Screen.TAB_CUSTOMERS, clearStack = true)
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

        val custSpinner = spinnerField(customers.map { it.second })
        if (customerId != null) {
            val idx = customers.indexOfFirst { it.first == customerId }
            if (idx >= 0) custSpinner.setSelection(idx)
        }

        val make = field("الشركة / الصانع")
        val model = field("طراز السيارة")
        val year = field("سنة الصنع", "number")
        val engine = field("نوع المحرك")
        val color = field("اللون")
        val fuelSpinner = spinnerField(listOf("بنزين", "ديزل", "هجين", "كهرباء", "غاز"))
        val transSpinner = spinnerField(listOf("أوتوماتيك", "عادي", "CVT"))
        val plate = field("رقم اللوحة", suggestionKeys = listOf("plate"))
        val vin = field("رقم الهيكل VIN (17 خانة)", suggestionKeys = listOf("vin"))
        val odometer = field("قراءة العداد", "number")
        val notes = field("ملاحظات السيارة", "multiline")

        var carPhotoPath = ""
        var fuelPhotoPath = ""

        val carPhotoBtn = Button(this).apply {
            text = "إرفاق صورة السيارة"
            setTextColor(Color.WHITE)
            background = buttonBg(teal, teal, tealDark)
            setOnClickListener { pickOrCapturePhoto("صورة السيارة") { carPhotoPath = it } }
        }
        val fuelPhotoBtn = Button(this).apply {
            text = "إرفاق صورة مؤشر الوقود"
            setTextColor(Color.WHITE)
            background = buttonBg(gold, gold, navyDark)
            setOnClickListener { pickOrCapturePhoto("صورة مؤشر الوقود") { fuelPhotoPath = it } }
        }

        // تنبيه فوري إذا رقم اللوحة مكرر
        plate.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val pl = s?.toString().orEmpty().trim()
                if (pl.isNotBlank() && db.suggestions("plate", pl).any { it.equals(pl, true) }) {
                    plate.error = "تنبيه: رقم اللوحة مسجل مسبقاً"
                } else {
                    plate.error = null
                }
            }
        })

        val inputs = listOf(make, model, year, engine, color, plate, vin, odometer, notes)
        val views = mutableListOf<View>()
        if (customerId == null) views += custSpinner
        views += listOf(make, model, year, engine, color, fuelSpinner, transSpinner, plate, vin, odometer, notes, carPhotoBtn, fuelPhotoBtn)

        formDialog("إضافة سيارة", views, inputs) { dialog ->
            try {
                val selectedId = customerId ?: customers.getOrNull(custSpinner.selectedItemPosition)?.first
                    ?: throw IllegalArgumentException("اختر العميل")
                val vId = db.addVehicle(
                    selectedId, make.text.toString(), model.text.toString(), year.text.toString(),
                    engine.text.toString(), plate.text.toString(), vin.text.toString(), odometer.text.toString(),
                    notes.text.toString(), color.text.toString(), fuelSpinner.selectedItem.toString(), transSpinner.selectedItem.toString()
                )
                if (carPhotoPath.isNotBlank()) db.addVehiclePhoto(vId, "صورة السيارة", carPhotoPath)
                if (fuelPhotoPath.isNotBlank()) db.addVehiclePhoto(vId, "صورة مؤشر الوقود", fuelPhotoPath)
                dialog.dismiss()
                toast("تم حفظ السيارة")
                navigateTo(Screen.TAB_VEHICLES, clearStack = true)
            } catch (e: Exception) {
                toast(e.message ?: "تعذر حفظ السيارة")
            }
        }
    }

    private fun workOrderForm(selectedCustomerId: Long? = null, selectedVehicleId: Long? = null) {
        if (!allowed("work_orders", "add")) return
        val customers = db.customerOptions()
        if (customers.isEmpty()) {
            toast("أضف عميلاً أولاً")
            return
        }

        val custSpinner = spinnerField(customers.map { it.second })
        if (selectedCustomerId != null) {
            val idx = customers.indexOfFirst { it.first == selectedCustomerId }
            if (idx >= 0) custSpinner.setSelection(idx)
            custSpinner.isEnabled = false
        }

        var vehicles = db.vehicleOptions(selectedCustomerId)
        val vehSpinner = spinnerField(vehicles.map { it.third })
        val vehAdapter = vehSpinner.adapter as ArrayAdapter<String>

        fun refreshVehicles(custId: Long?, prefVehId: Long? = null) {
            vehicles = db.vehicleOptions(custId)
            vehAdapter.clear()
            vehAdapter.addAll(vehicles.map { it.third })
            vehAdapter.notifyDataSetChanged()
            val pIdx = prefVehId?.let { id -> vehicles.indexOfFirst { it.first == id } } ?: -1
            if (pIdx >= 0) vehSpinner.setSelection(pIdx)
            vehSpinner.isEnabled = vehicles.isNotEmpty()
        }

        if (selectedCustomerId == null) {
            custSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) = refreshVehicles(null)
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    refreshVehicles(customers.getOrNull(position)?.first)
                }
            }
            refreshVehicles(customers.getOrNull(custSpinner.selectedItemPosition)?.first)
        } else {
            refreshVehicles(selectedCustomerId, selectedVehicleId)
        }

        val prioritySpinner = spinnerField(listOf("عادية", "مستعجلة", "عاجلة جداً"))
        val techName = field("اسم الفني")
        val expectedDelivery = field("تاريخ التسليم المتوقع (YYYY-MM-DD)")
        val odometer = field("قراءة العداد", "number")
        val complaint = field("شكوى العميل", "multiline")
        val diagnosis = field("التشخيص", "multiline")
        val notes = field("ملاحظات الإصلاح", "multiline")
        val labor = field("أجور العمل", "decimal")
        val parts = field("تكلفة القطع", "decimal")
        val discount = field("الخصم", "decimal")
        val downPayment = field("دفعة مقدمة", "decimal")

        val inputs = listOf(techName, expectedDelivery, odometer, complaint, diagnosis, notes, labor, parts, discount, downPayment)
        val views = listOf(
            custSpinner, vehSpinner, prioritySpinner, techName, expectedDelivery,
            odometer, complaint, diagnosis, notes, labor, parts, discount, downPayment
        )

        formDialog("أمر شغل جديد", views, inputs) { dialog ->
            try {
                val cId = selectedCustomerId ?: customers.getOrNull(custSpinner.selectedItemPosition)?.first
                    ?: throw IllegalArgumentException("اختر العميل")
                val vItem = vehicles.getOrNull(vehSpinner.selectedItemPosition)
                    ?: throw IllegalArgumentException("لا توجد سيارة مرتبطة بالعميل")
                require(vItem.second == cId) { "السيارة لا تتبع العميل المحدد" }

                db.addWorkOrder(
                    cId, vItem.first, odometer.text.toString(), complaint.text.toString(),
                    diagnosis.text.toString(), notes.text.toString(), "مفتوح",
                    labor.text.toString(), parts.text.toString(),
                    prioritySpinner.selectedItem.toString(), techName.text.toString(),
                    discount.text.toString(), expectedDelivery.text.toString(), downPayment.text.toString()
                )
                dialog.dismiss()
                toast("تم إنشاء أمر الشغل")
                navigateTo(Screen.TAB_WORK_ORDERS, clearStack = true)
            } catch (e: Exception) {
                toast(e.message ?: "تعذر حفظ أمر الشغل")
            }
        }
    }

    private fun editWorkOrderDialog(wo: WorkOrderRecord, onSaved: () -> Unit) {
        if (!allowed("work_orders", "edit")) return
        val statuses = listOf("مفتوح", "قيد الإصلاح", "مكتمل")
        val statusSpinner = spinnerField(statuses, if (wo.status == "قيد العمل") "قيد الإصلاح" else wo.status)
        val prioritySpinner = spinnerField(listOf("عادية", "مستعجلة", "عاجلة جداً"), wo.priority)
        val techName = field("اسم الفني").apply { setText(wo.technicianName) }
        val expectedDelivery = field("تاريخ التسليم المتوقع").apply { setText(wo.expectedDelivery) }
        val diagnosis = field("التشخيص", "multiline").apply { setText(wo.diagnosis) }
        val notes = field("ملاحظات الإصلاح", "multiline").apply { setText(wo.repairNotes) }
        val labor = field("أجور العمل", "decimal").apply { setText(wo.laborCost.toString()) }
        val parts = field("تكلفة القطع", "decimal").apply { setText(wo.partsCost.toString()) }
        val discount = field("الخصم", "decimal").apply { setText(wo.discount.toString()) }

        val inputs = listOf(techName, expectedDelivery, diagnosis, notes, labor, parts, discount)
        val views = listOf(statusSpinner, prioritySpinner, techName, expectedDelivery, diagnosis, notes, labor, parts, discount)

        formDialog("تعديل أمر #${wo.id}", views, inputs) { dialog ->
            try {
                val ok = db.updateWorkOrder(
                    wo.id, statusSpinner.selectedItem.toString(), diagnosis.text.toString(),
                    notes.text.toString(), labor.text.toString(), parts.text.toString(),
                    prioritySpinner.selectedItem.toString(), techName.text.toString(),
                    discount.text.toString(), expectedDelivery.text.toString()
                )
                if (ok) {
                    dialog.dismiss()
                    toast("تم التحديث")
                    onSaved()
                } else toast("تعذر التحديث")
            } catch (e: Exception) {
                toast(e.message ?: "تعذر التحديث")
            }
        }
    }

    private fun partForm() {
        if (!allowed("parts", "add")) return

        val number = field("رقم القطعة", suggestionKeys = listOf("part_number"))
        val name = field("اسم القطعة", suggestionKeys = listOf("part_name"))
        val barcode = field("الباركود")
        val supplier = field("المورد")
        val shelf = field("رقم الرف")
        val qty = field("الكمية", "decimal")
        val minQty = field("الحد الأدنى للتنبيه", "decimal")
        val unit = field("الوحدة")
        val buy = field("سعر الشراء", "decimal")
        val sell = field("سعر البيع", "decimal")

        // تنبيه فوري إذا رقم القطعة مكرر
        number.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val num = s?.toString().orEmpty().trim()
                if (num.isNotBlank() && db.suggestions("part_number", num).any { it.equals(num, true) }) {
                    number.error = "تنبيه: رقم القطعة مسجل مسبقاً"
                } else {
                    number.error = null
                }
            }
        })

        val inputs = listOf(number, name, barcode, supplier, shelf, qty, minQty, unit, buy, sell)
        formDialog("إضافة قطعة غيار", inputs, inputs) { dialog ->
            try {
                db.addPart(
                    number.text.toString(), name.text.toString(), qty.text.toString(),
                    unit.text.toString(), buy.text.toString(), sell.text.toString(),
                    minQty.text.toString(), supplier.text.toString(), shelf.text.toString(), barcode.text.toString()
                )
                dialog.dismiss()
                toast("تم حفظ القطعة")
                navigateTo(Screen.TAB_INVENTORY, clearStack = true)
            } catch (e: Exception) {
                toast(e.message ?: "تعذر حفظ القطعة")
            }
        }
    }

    private fun showCustomerVehicles(customerId: Long) {
        if (!allowed("vehicles", "view")) return
        val list = db.getCustomerVehicles(customerId)
        if (list.isEmpty()) {
            toast("لا توجد سيارات لهذا العميل")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("سيارات العميل")
            .setItems(list.map { "${it.make} ${it.model} — ${it.plate}" }.toTypedArray()) { _, i ->
                showVehicleProfile(list[i].id)
            }
            .setNegativeButton("إغلاق", null)
            .show()
    }

    private fun showVehicleWorkOrdersDialog(vehicleId: Long) {
        if (!allowed("work_orders", "view")) return
        val list = db.getAllWorkOrders(limit = 500).filter { it.vehicleId == vehicleId }
        if (list.isEmpty()) {
            toast("لا توجد أوامر لهذه السيارة")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("أوامر السيارة")
            .setItems(list.map { "#${it.id} • ${it.status} — المتبقي: ${it.remainingAmount}" }.toTypedArray()) { _, i ->
                workOrderDetailsDialog(list[i].id)
            }
            .setNegativeButton("إغلاق", null)
            .show()
    }

    private fun showCustomerWorkOrdersDialog(customerId: Long) {
        if (!allowed("work_orders", "view")) return
        val list = db.getAllWorkOrders(limit = 500).filter { it.customerId == customerId }
        if (list.isEmpty()) {
            toast("لا توجد أوامر لهذا العميل")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("أوامر العميل")
            .setItems(list.map { "#${it.id} • ${it.vehicleName} — ${it.status} — المتبقي: ${it.remainingAmount}" }.toTypedArray()) { _, i ->
                workOrderDetailsDialog(list[i].id)
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
        val text = records.joinToString("\n\n") {
            "من: ${it.oldOwner}\nإلى: ${it.newOwner}\nالسبب: ${it.reason}\nالتاريخ: ${it.changedAt}"
        }
        showMessage("سجل ملاك السيارة", text)
    }

    // ---------------------------------------------------------
    // إدارة المستخدمين والصلاحيات
    // ---------------------------------------------------------

    private fun editOwnAccountDialog(user: AuthUser) {
        val nameInput = field("الاسم الكامل").apply { setText(user.fullName) }
        val passInput = field("كلمة مرور جديدة (6 أحرف+ - اختياري)", "password")
        formDialog("تعديل حسابي", listOf(nameInput, passInput), listOf(nameInput, passInput)) { dialog ->
            val nOk = db.updateUserProfile(user.id, nameInput.text.toString())
            val pOk = if (passInput.text.isNotBlank()) db.updateUserPassword(user.id, passInput.text.toString()) else true
            if (nOk && pOk) {
                loggedUser = db.currentUser()
                dialog.dismiss()
                toast("تم تحديث الحساب")
                renderScreen(screenStack.last())
            } else toast("تعذر تحديث الحساب")
        }
    }

    private fun usersScreen() {
        val actor = db.currentUser() ?: run { navigateTo(Screen.LOGIN, clearStack = true); return }
        if (actor.role != "مدير" && !db.hasPermission("users", "view")) {
            toast("ليس لديك صلاحية إدارة المستخدمين")
            return
        }
        page("إدارة المستخدمين", "الاسم، كلمة المرور، الصلاحيات، والحذف", showBack = true)

        if (actor.role == "مدير" || db.hasPermission("users", "add")) {
            val addCard = createCard("إضافة مستخدم", teal)
            addPrimaryButton("+ إنشاء مستخدم جديد", addCard, teal) { createUserForm() }
        }

        val listCard = createCard("قائمة المستخدمين", navyDark)
        db.listUsers().forEach { u ->
            addText("${u.fullName} (@${u.username}) — ${u.role}", 14f, textDark, true, listCard)
            addSecondaryButton("خيارات المستخدم (${u.fullName})", listCard) { userActionsDialog(u) }
        }
    }

    private fun userActionsDialog(target: AuthUser) {
        val actor = db.currentUser() ?: return
        val isManager = actor.role == "مدير"
        val canEdit = isManager || db.hasPermission("users", "edit") || actor.id == target.id
        val canDelete = (isManager || db.hasPermission("users", "delete")) && target.role != "مدير" && actor.id != target.id

        val opts = mutableListOf<Pair<String, () -> Unit>>()
        if (canEdit) {
            opts += "تغيير الاسم" to { editUserNameDialog(target) }
            opts += "تغيير كلمة المرور" to { editUserPasswordDialog(target) }
        }
        if (isManager && target.role != "مدير") {
            opts += "تعديل الصلاحيات" to {
                selectedPermissionUser = target
                navigateTo(Screen.PERMISSIONS)
            }
        }
        if (canDelete) {
            opts += "حذف المستخدم" to { confirmDeleteUser(target) }
        }

        if (opts.isEmpty()) {
            showMessage("بيانات المستخدم", "الاسم: ${target.fullName}\nالدور: ${target.role}")
            return
        }

        AlertDialog.Builder(this)
            .setTitle(target.fullName)
            .setItems(opts.map { it.first }.toTypedArray()) { _, i -> opts[i].second.invoke() }
            .setNegativeButton("إغلاق", null)
            .show()
    }

    private fun editUserNameDialog(target: AuthUser) {
        val nameInput = field("الاسم الكامل الجديد").apply { setText(target.fullName) }
        formDialog("تغيير اسم ${target.fullName}", listOf(nameInput), listOf(nameInput)) { dialog ->
            if (db.updateUserProfile(target.id, nameInput.text.toString())) {
                if (loggedUser?.id == target.id) loggedUser = db.currentUser()
                dialog.dismiss()
                toast("تم تحديث الاسم")
                usersScreen()
            } else toast("تعذر تحديث الاسم")
        }
    }

    private fun editUserPasswordDialog(target: AuthUser) {
        val passInput = field("كلمة المرور الجديدة (6 أحرف على الأقل)", "password")
        formDialog("تغيير كلمة مرور ${target.fullName}", listOf(passInput), listOf(passInput)) { dialog ->
            if (db.updateUserPassword(target.id, passInput.text.toString())) {
                dialog.dismiss()
                toast("تم تغيير كلمة المرور")
                usersScreen()
            } else toast("تعذر تغيير كلمة المرور")
        }
    }

    private fun confirmDeleteUser(target: AuthUser) {
        AlertDialog.Builder(this)
            .setTitle("تأكيد الحذف")
            .setMessage("هل تريد حذف المستخدم (${target.fullName})؟")
            .setPositiveButton("حذف") { _, _ ->
                if (db.deleteUser(target.id)) {
                    toast("تم حذف المستخدم")
                    usersScreen()
                } else toast("تعذر حذف المستخدم")
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun createUserForm() {
        val username = field("اسم المستخدم")
        val name = field("الاسم الكامل")
        val password = field("كلمة المرور", "password")
        val roleSpinner = spinnerField(listOf("فني", "موظف استقبال", "محاسب", "مخزن"))

        formDialog("إنشاء مستخدم", listOf(username, name, password, roleSpinner), listOf(username, name, password)) { dialog ->
            val ok = db.createUser(username.text.toString(), name.text.toString(), password.text.toString(), roleSpinner.selectedItem.toString())
            if (ok) {
                dialog.dismiss()
                toast("تم إنشاء المستخدم")
                usersScreen()
            } else toast("تعذر إنشاء المستخدم")
        }
    }

    private fun permissionsScreen(user: AuthUser) {
        val modules = listOf(
            "customers" to "العملاء",
            "vehicles" to "السيارات",
            "work_orders" to "أوامر الشغل",
            "parts" to "قطع الغيار",
            "users" to "المستخدمون"
        )
        val perms = db.getPermissions(user.id)
        val checks = mutableListOf<Triple<String, String, CheckBox>>()

        page("صلاحيات المستخدم", user.fullName, showBack = true)
        modules.forEach { (mod, title) ->
            val card = createCard(title, teal)
            val p = perms.firstOrNull { it.module == mod }
            listOf(
                Triple("view", "عرض", p?.canView ?: false),
                Triple("add", "إضافة", p?.canAdd ?: false),
                Triple("edit", "تعديل", p?.canEdit ?: false),
                Triple("delete", "حذف", p?.canDelete ?: false)
            ).forEach { (act, lbl, checked) ->
                val cb = CheckBox(this).apply {
                    text = lbl
                    isChecked = checked
                    textSize = 14f
                }
                card.addView(cb, matchWidth())
                checks += Triple(mod, act, cb)
            }
        }

        addPrimaryButton("حفظ الصلاحيات", root, teal) {
            val allOk = checks.all { (mod, act, cb) -> db.setPermission(user.id, mod, act, cb.isChecked) }
            if (allOk) {
                toast("تم حفظ الصلاحيات")
                navigateTo(Screen.USERS)
            } else toast("تعذر حفظ بعض الصلاحيات")
        }
    }

    // ---------------------------------------------------------
    // الصور من المعرض والكاميرا ومعاينتها
    // ---------------------------------------------------------

    private fun pickOrCapturePhoto(label: String, onSaved: (String) -> Unit) {
        pendingPhotoLabel = label
        pendingPhotoCallback = onSaved

        AlertDialog.Builder(this)
            .setTitle(label)
            .setItems(arrayOf("اختيار من معرض الصور", "التقاط بالكاميرا")) { _, which ->
                if (which == 0) {
                    val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                    @Suppress("DEPRECATION")
                    startActivityForResult(intent, REQ_PICK_PHOTO)
                } else {
                    val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                    if (intent.resolveActivity(packageManager) != null) {
                        @Suppress("DEPRECATION")
                        startActivityForResult(intent, REQ_CAPTURE_PHOTO)
                    } else toast("تعذر فتح الكاميرا")
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) return
        val cb = pendingPhotoCallback ?: return

        val savedPath = try {
            val dir = File(filesDir, "vehicle_photos").apply { mkdirs() }
            val out = File(dir, "img_${System.currentTimeMillis()}.jpg")
            when (requestCode) {
                REQ_PICK_PHOTO -> {
                    val uri: Uri = data.data ?: return
                    contentResolver.openInputStream(uri)?.use { inp ->
                        FileOutputStream(out).use { fos -> inp.copyTo(fos) }
                    } ?: return
                    out.absolutePath
                }
                REQ_CAPTURE_PHOTO -> {
                    @Suppress("DEPRECATION")
                    val bmp = data.extras?.get("data") as? Bitmap ?: return
                    FileOutputStream(out).use { fos -> bmp.compress(Bitmap.CompressFormat.JPEG, 85, fos) }
                    out.absolutePath
                }
                else -> return
            }
        } catch (_: Exception) {
            toast("تعذر حفظ الصورة")
            return
        } finally {
            pendingPhotoCallback = null
        }

        cb(savedPath)
        toast("تم حفظ الصورة")
    }

    private fun showPhotoPreview(title: String, path: String) {
        val file = File(path)
        if (path.isBlank() || !file.exists()) {
            showMessage(title, "الصورة غير متوفرة.")
            return
        }
        val bmp = BitmapFactory.decodeFile(file.absolutePath)
        if (bmp == null) {
            showMessage(title, "تعذر قراءة الصورة.")
            return
        }
        val iv = ImageView(this).apply {
            setImageBitmap(bmp)
            adjustViewBounds = true
            maxHeight = dp(360)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(iv)
            .setPositiveButton("إغلاق", null)
            .show()
    }
}
