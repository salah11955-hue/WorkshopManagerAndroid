package com.khubara.workshop

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteException
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.provider.MediaStore
import android.text.Editable
import android.text.InputType
import android.text.Layout
import android.text.StaticLayout
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter

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
        VEHICLE_REPORTS,
        VEHICLE_REPORT_DETAIL,
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
    private var selectedReportVehicleId: Long? = null
    private var selectedPermissionUser: AuthUser? = null
    private var lastBackupFile: File? = null

    private var pendingPhotoLabel: String = "صورة سيارة"
    private var pendingPhotoCallback: ((String) -> Unit)? = null

    // الألوان العصرية الجديدة: كحلي داكن + تركوازي + ذهبي + خلفية فاتحة
    private val navyDark = Color.rgb(15, 23, 42)
    private val navyCard = Color.rgb(30, 41, 59)
    private val teal = Color.rgb(14, 165, 164)
    private val tealDark = Color.rgb(15, 118, 110)
    private val gold = Color.rgb(245, 158, 11)
    private val bgLight = Color.rgb(247, 249, 252)
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
            setBackgroundColor(Color.WHITE)
            elevation = dp(12).toFloat()
            setPadding(dp(6), dp(7), dp(6), dp(8))
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
                    cornerRadius = dp(14).toFloat()
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
                setTextColor(if (isSelected) navyDark else textMuted)
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
            Screen.VEHICLE_REPORTS -> vehicleReportsScreen()
            Screen.VEHICLE_REPORT_DETAIL -> selectedReportVehicleId?.let(::vehicleReportScreen)
                ?: vehicleReportsScreen()
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
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), if (cardBackground == dangerRed) dangerDark else borderLight)
            }
            elevation = dp(3).toFloat()
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

    private fun addMetricGrid(parent: LinearLayout, metrics: List<Pair<String, String>>) {
        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        metrics.forEach { (label, value) ->
            val tile = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(10), dp(8), dp(10))
                background = GradientDrawable().apply {
                    setColor(Color.rgb(248, 250, 252))
                    cornerRadius = dp(14).toFloat()
                    setStroke(dp(1), borderLight)
                }
            }
            tile.addView(TextView(this).apply {
                text = value
                textSize = 21f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(tealDark)
            }, matchWidth())
            tile.addView(TextView(this).apply {
                text = label
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(textMuted)
                setPadding(0, dp(3), 0, 0)
            }, matchWidth())
            grid.addView(
                tile,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(dp(3), dp(3), dp(3), dp(3))
                }
            )
        }
        parent.addView(grid, matchWidth().apply { setMargins(0, dp(4), 0, dp(6)) })
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
            cornerRadius = dp(14).toFloat()
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
            elevation = dp(1).toFloat()
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(11).toFloat()
                setStroke(dp(1), borderLight)
            }

            if (suggestionKeys.isNotEmpty() &&
                type != "password" && type != "number" && type != "decimal"
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
                        val matches = suggestionKeys
                            .flatMap { key ->
                                try {
                                    db.suggestions(key, prefix)
                                } catch (_: SQLiteException) {
                                    emptyList()
                                }
                            }
                            .distinct()
                            .take(10)
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
        val metrics = mutableListOf<Pair<String, String>>()
        if (db.hasPermission("customers", "view")) metrics += "العملاء" to db.count("customers").toString()
        if (db.hasPermission("vehicles", "view")) metrics += "السيارات" to db.count("vehicles").toString()
        if (db.hasPermission("work_orders", "view")) metrics += "الأوامر" to db.count("work_orders").toString()
        if (db.hasPermission("parts", "view")) metrics += "قطع المخزون" to db.count("parts").toString()
        if (metrics.isNotEmpty()) addMetricGrid(statsCard, metrics)

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

        showMessageWithActions("ملف العميل", info, actions)
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
            actions += "تقرير السيارة والفواتير" to { openVehicleReport(vehicleId) }
        }
        if (db.hasPermission("vehicles", "edit")) {
            actions += "نقل ملكية السيارة" to { transferOwnershipDialog(vehicleId, currentOwnerId = v.customerId) }
        }
        actions += "سجل ملاك السيارة" to { showOwnerHistory(vehicleId) }

        showMessageWithActions("ملف السيارة", details, actions)
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
            actions += "تحديث حالة العمل والتشخيص والتكاليف" to {
                editWorkOrderDialog(wo) { workOrderDetailsDialog(workOrderId) }
            }
            if (wo.status == "مفتوح") {
                actions += "بدء تنفيذ الأعمال" to { setWorkOrderStatus(wo, "قيد الإصلاح") }
            } else if (wo.status == "مكتمل") {
                actions += "إعادة فتح أمر الشغل" to { setWorkOrderStatus(wo, "مفتوح") }
            } else {
                actions += "اعتماد إكمال الأعمال" to {
                    AlertDialog.Builder(this)
                        .setTitle("إكمال أمر الشغل #${wo.id}")
                        .setMessage("هل تم تنفيذ العمل وجاهز لتسليم السيارة؟")
                        .setPositiveButton("اعتماد الإكمال") { _, _ ->
                            setWorkOrderStatus(wo, "مكتمل")
                        }
                        .setNegativeButton("إلغاء", null)
                        .show()
                }
            }
        }
        val workOrderPhotos = db.getWorkOrderPhotos(workOrderId)
        if (workOrderPhotos.isNotEmpty()) {
            actions += "عرض صور أمر الإصلاح (${workOrderPhotos.size})" to {
                showWorkOrderPhotosDialog(workOrderPhotos)
            }
        }
        if (db.hasPermission("work_orders", "edit") || db.hasPermission("work_orders", "add")) {
            actions += "+ إضافة صورة لأمر الإصلاح" to {
                pickOrCapturePhoto("صورة أمر الإصلاح") { savedPath ->
                    if (db.addWorkOrderPhoto(workOrderId, "صورة من سجل الإصلاح", savedPath)) {
                        toast("تم إضافة الصورة")
                        workOrderDetailsDialog(workOrderId)
                    } else toast("تعذر إضافة الصورة")
                }
            }
        }
        if (db.hasPermission("work_orders", "edit")) {
            actions += "تنفيذ العمل والمهام والقطع والتسليم" to {
                workOrderLifecycleDialog(wo)
            }
        }
        actions += "عرض ملصق QR للأمر" to { showWorkOrderQrDialog(wo) }
        actions += "إصدار / إعادة طباعة الفاتورة" to { shareOrPrintInvoice(wo) }
        if (payments.isNotEmpty()) {
            actions += "عرض سجل الدفعات" to {
                val pText = payments.joinToString("\n\n") {
                    "المبلغ: ${it.amount} | الطريقة: ${it.method}\nالتاريخ: ${it.paidAt}\nملاحظة: ${it.note.ifBlank { "-" }}"
                }
                showMessage("دفعات أمر #${wo.id}", pText)
            }
        }

        showMessageWithActions("تفاصيل أمر الشغل #${wo.id}", summary, actions)
    }

    private fun setWorkOrderStatus(wo: WorkOrderRecord, status: String) {
        if (!allowed("work_orders", "edit")) return
        try {
            if (!db.setWorkOrderStatus(wo.id, status)) {
                toast("تعذر تحديث حالة أمر الشغل")
                return
            }
            toast(if (status == "مكتمل") "تم اعتماد إكمال أمر الشغل" else "تم تحديث حالة أمر الشغل")
            workOrderDetailsDialog(wo.id)
        } catch (e: Exception) {
            toast(e.message ?: "تعذر تحديث حالة أمر الشغل")
        }
    }

    private fun showWorkOrderQrDialog(wo: WorkOrderRecord) {
        try {
            val payload = "WORKSHOP|ORDER:${wo.id}|CUSTOMER:${wo.customerName}|PHONE:${wo.customerPhone}|PLATE:${wo.vehiclePlate}"
            val bitMatrix = MultiFormatWriter().encode(payload, BarcodeFormat.QR_CODE, 640, 640)
            val bitmap = Bitmap.createBitmap(640, 640, Bitmap.Config.RGB_565)
            for (x in 0 until 640) {
                for (y in 0 until 640) {
                    bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
                }
            }
            val image = ImageView(this).apply {
                setImageBitmap(bitmap)
                adjustViewBounds = true
                setPadding(dp(24), dp(24), dp(24), dp(24))
            }
            AlertDialog.Builder(this)
                .setTitle("QR — أمر #${wo.id}")
                .setView(image)
                .setNegativeButton("إغلاق", null)
                .show()
        } catch (_: Exception) {
            toast("تعذر إنشاء رمز QR")
        }
    }

    private fun showActionList(title: String, message: String, actions: List<Pair<String, () -> Unit>>) {
        showMessageWithActions(title, message, actions)
    }

    private fun showMessageWithActions(
        title: String,
        message: String,
        actions: List<Pair<String, () -> Unit>>
    ) {
        val builder = AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setNegativeButton("إغلاق", null)
        if (actions.isNotEmpty()) {
            builder.setPositiveButton("الإجراءات") { _, _ ->
                AlertDialog.Builder(this)
                    .setTitle(title)
                    .setItems(actions.map { it.first }.toTypedArray()) { _, index ->
                        actions[index].second.invoke()
                    }
                    .setNegativeButton("رجوع", null)
                    .show()
            }
        }
        builder.show()
    }

    private fun workOrderLifecycleDialog(wo: WorkOrderRecord) {
        val usedParts = db.getUsedParts(wo.id)
        val tasks = db.getWorkOrderTasks(wo.id)
        val summary = buildString {
            append("القطع المستخدمة: ${usedParts.size}\n")
            usedParts.take(6).forEach { append("${it.quantity} × ${it.partName} — ${formatMoney(it.totalPrice)}\n") }
            append("\nالمهام: ${tasks.size}\n")
            tasks.take(8).forEach { append("${it.title} — ${it.status}${if (it.assignedTo.isNotBlank()) " (${it.assignedTo})" else ""}\n") }
        }

        val actions = mutableListOf<Pair<String, () -> Unit>>()
        if (db.hasPermission("work_orders", "edit")) {
            actions += "إضافة قطعة مستخدمة" to { addUsedPartDialog(wo) }
            actions += "إضافة مهمة للفني" to { addTaskDialog(wo) }
            actions += "عرض وتحديث المهام" to { manageTasksDialog(wo) }
            actions += "حجز موعد تسليم / مراجعة" to { addAppointmentDialog(wo) }
            actions += "موافقة العميل وتسليم السيارة" to { signWorkOrderDocumentDialog(wo) }
        }

        showActionList(
            "دورة أمر الإصلاح #${wo.id}",
            summary.ifBlank { "لا توجد قطع أو مهام مسجلة بعد." },
            actions
        )
    }

    private fun addUsedPartDialog(wo: WorkOrderRecord) {
        val parts = db.getAllParts(500).filter { it.quantity > 0.0 }
        if (parts.isEmpty()) {
            toast("لا توجد قطع متاحة في المخزون")
            return
        }
        val partSpinner = spinnerField(parts.map { "${it.name} — متاح ${it.quantity} ${it.unit}" })
        val quantity = field("الكمية المستخدمة", "decimal")
        formDialog("إضافة قطعة للأمر #${wo.id}", listOf(partSpinner, quantity), listOf(quantity)) { dialog ->
            try {
                val part = parts.getOrNull(partSpinner.selectedItemPosition)
                    ?: throw IllegalArgumentException("اختر قطعة")
                val qty = quantity.text.toString().toDoubleOrNull()
                    ?: throw IllegalArgumentException("أدخل الكمية")
                if (!db.addUsedPart(wo.id, part.id, qty)) throw IllegalArgumentException("تعذر إضافة القطعة")
                dialog.dismiss()
                toast("تم خصم القطعة وتحديث تكلفة الأمر")
                workOrderDetailsDialog(wo.id)
            } catch (e: Exception) {
                toast(e.message ?: "تعذر إضافة القطعة")
            }
        }
    }

    private fun addTaskDialog(wo: WorkOrderRecord) {
        val title = field("اسم المهمة")
        val assigned = field("الفني المسؤول", suggestionKeys = listOf("technician"))
        val due = field("موعد المهمة (YYYY-MM-DD)")
        val notes = field("ملاحظات المهمة", "multiline")
        formDialog("مهمة لأمر #${wo.id}", listOf(title, assigned, due, notes), listOf(title, assigned, due, notes)) { dialog ->
            try {
                if (!db.addWorkOrderTask(wo.id, title.text.toString(), assigned.text.toString(), due.text.toString(), notes.text.toString())) {
                    throw IllegalArgumentException("تعذر إضافة المهمة")
                }
                dialog.dismiss()
                toast("تمت إضافة المهمة")
                workOrderLifecycleDialog(wo)
            } catch (e: Exception) {
                toast(e.message ?: "تعذر إضافة المهمة")
            }
        }
    }

    private fun manageTasksDialog(wo: WorkOrderRecord) {
        val tasks = db.getWorkOrderTasks(wo.id)
        if (tasks.isEmpty()) {
            toast("لا توجد مهام لهذا الأمر")
            return
        }
        val items = tasks.map { "${it.title} — ${it.status}${if (it.assignedTo.isNotBlank()) " — ${it.assignedTo}" else ""}" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("مهام أمر #${wo.id}")
            .setItems(items) { _, index ->
                val task = tasks[index]
                val statuses = arrayOf("قيد الانتظار", "قيد التنفيذ", "مكتملة")
                AlertDialog.Builder(this)
                    .setTitle(task.title)
                    .setItems(statuses) { _, statusIndex ->
                        if (db.updateWorkOrderTask(task.id, statuses[statusIndex])) {
                            toast("تم تحديث المهمة")
                            workOrderLifecycleDialog(wo)
                        } else toast("تعذر تحديث المهمة")
                    }
                    .setNegativeButton("إلغاء", null)
                    .show()
            }
            .setNegativeButton("إغلاق", null)
            .show()
    }

    private fun addAppointmentDialog(wo: WorkOrderRecord) {
        val startsAt = field("موعد الزيارة (YYYY-MM-DD HH:MM)")
        val purpose = field("الغرض", suggestionKeys = listOf("complaint"))
        val notes = field("ملاحظات الموعد", "multiline")
        formDialog("موعد لأمر #${wo.id}", listOf(startsAt, purpose, notes), listOf(startsAt, purpose, notes)) { dialog ->
            try {
                if (!db.addAppointment(wo.customerId, wo.vehicleId, wo.id, startsAt.text.toString(), purpose.text.toString(), notes.text.toString())) {
                    throw IllegalArgumentException("تعذر إضافة الموعد")
                }
                dialog.dismiss()
                toast("تمت جدولة الموعد")
            } catch (e: Exception) {
                toast(e.message ?: "تعذر جدولة الموعد")
            }
        }
    }

    private fun signWorkOrderDocumentDialog(wo: WorkOrderRecord) {
        val signedBy = field("اسم العميل / المفوض بالتوقيع")
        val documentType = spinnerField(listOf("موافقة على التكلفة", "إذن إصلاح", "استلام السيارة"))
        val content = field("تفاصيل الموافقة أو التسليم", "multiline")
        formDialog("موافقة وتسليم أمر #${wo.id}", listOf(documentType, signedBy, content), listOf(signedBy, content)) { dialog ->
            try {
                val title = documentType.selectedItem.toString()
                if (!db.signWorkOrderDocument(wo.id, title, title, signedBy.text.toString(), content.text.toString())) {
                    throw IllegalArgumentException("تعذر حفظ المستند")
                }
                if (title == "استلام السيارة") {
                    if (!db.setWorkOrderStatus(wo.id, "مكتمل")) {
                        throw IllegalStateException("تم حفظ مستند الاستلام لكن تعذر تحديث حالة أمر الشغل")
                    }
                }
                dialog.dismiss()
                toast("تم حفظ الموافقة والتوقيع الرقمي")
                workOrderDetailsDialog(wo.id)
            } catch (e: Exception) {
                toast(e.message ?: "تعذر حفظ المستند")
            }
        }
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

    private fun showWorkOrderPhotosDialog(photos: List<WorkOrderPhotoItem>) {
        val items = photos.map { "${it.label}\n${it.createdAt}" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("صور أمر الإصلاح")
            .setItems(items) { _, index ->
                val photo = photos.getOrNull(index) ?: return@setItems
                showPhotoPreview(photo.label, photo.photoPath)
            }
            .setNegativeButton("إغلاق", null)
            .show()
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
            .setNeutralButton("إنشاء ومشاركة PDF") { _, _ ->
                shareInvoicePdf(wo)
            }
            .setNegativeButton("طباعة") { _, _ ->
                printInvoicePdf(wo)
            }
            .show()
    }

    private fun shareInvoicePdf(wo: WorkOrderRecord) {
        try {
            sharePdfFile(
                createInvoicePdf(wo),
                "فاتورة أمر شغل #${wo.id}",
                "مشاركة فاتورة PDF"
            )
        } catch (e: Exception) {
            toast("تعذر مشاركة الفاتورة: ${e.message ?: "حدث خطأ غير معروف"}")
        }
    }

    private fun sharePdfFile(pdfFile: File, subject: String, chooserTitle: String) {
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", pdfFile)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, chooserTitle))
    }

    private fun createInvoicePdf(wo: WorkOrderRecord): File {
        val pdfDir = File(cacheDir, "pdf").apply { mkdirs() }
        val pdfFile = File(pdfDir, "invoice_${wo.id}.pdf")
        val document = PdfDocument()
        try {
            val pageWidth = 595
            val pageHeight = 842
            var pageNumber = 1
            var page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            var canvas = page.canvas
            var y = 42f

            fun finishPage() {
                document.finishPage(page)
            }

            fun ensureSpace(height: Float) {
                if (y + height <= pageHeight - 48f) return
                finishPage()
                pageNumber++
                page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                canvas = page.canvas
                y = 42f
            }

            fun drawTextBlock(text: String, paint: android.text.TextPaint, alignRight: Boolean = true) {
                val layoutWidth = pageWidth - 72
                val alignment = if (alignRight) Layout.Alignment.ALIGN_OPPOSITE else Layout.Alignment.ALIGN_NORMAL
                val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, layoutWidth)
                    .setAlignment(alignment)
                    .setIncludePad(false)
                    .build()
                ensureSpace(layout.height.toFloat() + 8f)
                canvas.save()
                canvas.translate(36f, y)
                layout.draw(canvas)
                canvas.restore()
                y += layout.height + 8f
            }

            val titlePaint = android.text.TextPaint().apply {
                textSize = 20f
                isFakeBoldText = true
                color = navyDark
                textAlign = android.graphics.Paint.Align.CENTER
            }
            val headerPaint = android.text.TextPaint().apply {
                textSize = 14f
                isFakeBoldText = true
                color = Color.WHITE
                textAlign = android.graphics.Paint.Align.CENTER
            }
            val normalPaint = android.text.TextPaint().apply {
                textSize = 12f
                color = textDark
            }
            val totalPaint = android.text.TextPaint().apply {
                textSize = 15f
                isFakeBoldText = true
                color = navyDark
            }

            canvas.drawColor(Color.rgb(248, 250, 252))
            canvas.drawRect(0f, 0f, pageWidth.toFloat(), 72f, android.graphics.Paint().apply { color = navyDark })
            canvas.drawText("خبراء هونداي وفورد", pageWidth / 2f, 32f, titlePaint.apply { color = Color.WHITE })
            canvas.drawText("فاتورة أمر إصلاح احترافية", pageWidth / 2f, 57f, headerPaint)
            y = 92f

            drawTextBlock("فاتورة رقم: #${wo.id}", totalPaint)
            drawTextBlock("التاريخ: ${wo.createdAt}", normalPaint)
            drawTextBlock("العميل: ${wo.customerName}  |  الهاتف: ${wo.customerPhone}", normalPaint)
            drawTextBlock("السيارة: ${wo.vehicleName}  |  اللوحة: ${wo.vehiclePlate}", normalPaint)
            y += 8f

            val detailLines = listOf(
                "بيانات العمل",
                "الشكوى: ${wo.complaint.ifBlank { "-" }}",
                "التشخيص: ${wo.diagnosis.ifBlank { "-" }}",
                "الإصلاح والتقرير: ${wo.repairNotes.ifBlank { "-" }}",
                "الفني: ${wo.technicianName.ifBlank { "-" }}  |  التسليم المتوقع: ${wo.expectedDelivery.ifBlank { "-" }}",
                "تفاصيل التكلفة",
                "أجور العمل: ${formatMoney(wo.laborCost)}",
                "تكلفة القطع: ${formatMoney(wo.partsCost)}",
                "الخصم: ${formatMoney(wo.discount)}",
                "الإجمالي: ${formatMoney(wo.totalCost)}",
                "المدفوع: ${formatMoney(wo.paidAmount)}",
                "المتبقي: ${formatMoney(wo.remainingAmount)}"
            )
            detailLines.forEachIndexed { index, line ->
                val paint = if (index == 0 || index == 5) headerPaint else if (index == 9) totalPaint else normalPaint
                if (paint === headerPaint) {
                    val layoutHeight = 28f
                    ensureSpace(layoutHeight)
                    canvas.drawRoundRect(36f, y, 559f, y + layoutHeight, 8f, 8f, android.graphics.Paint().apply { color = teal })
                    canvas.drawText(line, pageWidth / 2f, y + 19f, paint)
                    y += layoutHeight + 8f
                } else {
                    drawTextBlock(line, paint)
                }
            }

            drawTextBlock("شكراً لتعاملكم معنا", normalPaint, false)
            finishPage()
            FileOutputStream(pdfFile).use { output -> document.writeTo(output) }
            return pdfFile
        } finally {
            document.close()
        }
    }

    private fun shareVehicleReportPdf(vehicleId: Long) {
        try {
            val vehicle = db.getVehicleProfile(vehicleId)
                ?: throw IllegalArgumentException("السيارة غير موجودة")
            sharePdfFile(
                createVehicleReportPdf(vehicleId),
                "تقرير السيارة ${vehicle.make} ${vehicle.model}",
                "مشاركة تقرير السيارة PDF"
            )
        } catch (e: Exception) {
            toast("تعذرت مشاركة تقرير السيارة: ${e.message ?: "حدث خطأ غير معروف"}")
        }
    }

    private fun printVehicleReportPdf(vehicleId: Long) {
        try {
            printPdfFile(
                createVehicleReportPdf(vehicleId),
                "Vehicle_Report_$vehicleId"
            )
        } catch (e: Exception) {
            toast("تعذرت طباعة تقرير السيارة: ${e.message ?: "يمكنك مشاركة ملف PDF"}")
        }
    }

    private fun createVehicleReportPdf(vehicleId: Long): File {
        val vehicle = db.getVehicleProfile(vehicleId)
            ?: throw IllegalArgumentException("السيارة غير موجودة")
        val orders = db.getAllWorkOrders(limit = 2000)
            .filter { it.vehicleId == vehicleId }
        val customerNote = db.getVehicleReportNote(vehicleId)
        val pdfDir = File(cacheDir, "pdf").apply { mkdirs() }
        val pdfFile = File(pdfDir, "vehicle_report_$vehicleId.pdf")
        val document = PdfDocument()
        try {
            val pageWidth = 595
            val pageHeight = 842
            val contentWidth = pageWidth - 72
            val titlePaint = android.text.TextPaint().apply {
                textSize = 19f
                isFakeBoldText = true
                color = Color.WHITE
                textAlign = android.graphics.Paint.Align.CENTER
            }
            val sectionPaint = android.text.TextPaint().apply {
                textSize = 13f
                isFakeBoldText = true
                color = Color.WHITE
                textAlign = android.graphics.Paint.Align.CENTER
            }
            val bodyPaint = android.text.TextPaint().apply {
                textSize = 12f
                color = textDark
            }
            val totalPaint = android.text.TextPaint(bodyPaint).apply {
                textSize = 13f
                isFakeBoldText = true
            }

            var pageNumber = 0
            var page: PdfDocument.Page? = null
            var canvas: android.graphics.Canvas? = null
            var y = 0f

            fun startPage() {
                pageNumber++
                page = document.startPage(
                    PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                )
                canvas = page?.canvas
                val pageCanvas = canvas ?: throw IllegalStateException("تعذر إنشاء صفحة التقرير")
                pageCanvas.drawColor(Color.rgb(248, 250, 252))
                pageCanvas.drawRect(
                    0f,
                    0f,
                    pageWidth.toFloat(),
                    66f,
                    android.graphics.Paint().apply { color = navyDark }
                )
                val titleLayout = StaticLayout.Builder.obtain(
                    "تقرير السيارة والصيانة",
                    0,
                    "تقرير السيارة والصيانة".length,
                    titlePaint,
                    pageWidth - 48
                )
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .setIncludePad(false)
                    .build()
                pageCanvas.save()
                pageCanvas.translate(24f, 22f)
                titleLayout.draw(pageCanvas)
                pageCanvas.restore()
                y = 88f
            }

            fun finishPage() {
                page?.let { document.finishPage(it) }
                page = null
                canvas = null
            }

            fun ensureSpace(height: Float) {
                if (y + height <= pageHeight - 48f) return
                finishPage()
                startPage()
            }

            fun drawTextBlock(text: String, paint: android.text.TextPaint = bodyPaint) {
                val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, contentWidth)
                    .setAlignment(Layout.Alignment.ALIGN_OPPOSITE)
                    .setIncludePad(false)
                    .build()
                var startLine = 0
                while (startLine < layout.lineCount) {
                    ensureSpace(20f)
                    val availableHeight = pageHeight - 48f - y
                    val top = layout.getLineTop(startLine)
                    var endLine = startLine
                    while (endLine + 1 < layout.lineCount &&
                        layout.getLineBottom(endLine + 1) - top <= availableHeight
                    ) {
                        endLine++
                    }
                    val bottom = layout.getLineBottom(endLine)
                    val pageCanvas = canvas ?: throw IllegalStateException("صفحة التقرير غير جاهزة")
                    pageCanvas.save()
                    pageCanvas.translate(36f, y - top)
                    pageCanvas.clipRect(0f, top.toFloat(), contentWidth.toFloat(), bottom.toFloat())
                    layout.draw(pageCanvas)
                    pageCanvas.restore()
                    y += bottom - top + 8f
                    startLine = endLine + 1
                }
            }

            fun drawSection(title: String) {
                ensureSpace(34f)
                val pageCanvas = canvas ?: throw IllegalStateException("صفحة التقرير غير جاهزة")
                pageCanvas.drawRoundRect(
                    36f,
                    y,
                    (pageWidth - 36).toFloat(),
                    y + 26f,
                    7f,
                    7f,
                    android.graphics.Paint().apply { color = teal }
                )
                val titleLayout = StaticLayout.Builder.obtain(
                    title,
                    0,
                    title.length,
                    sectionPaint,
                    contentWidth
                )
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .setIncludePad(false)
                    .build()
                pageCanvas.save()
                pageCanvas.translate(36f, y + (26f - titleLayout.height) / 2f)
                titleLayout.draw(pageCanvas)
                pageCanvas.restore()
                y += 34f
            }

            startPage()
            drawSection("بيانات السيارة والعميل")
            drawTextBlock("السيارة: ${vehicle.make} ${vehicle.model} (${vehicle.year})")
            drawTextBlock("اللوحة: ${vehicle.plate.ifBlank { "-" }}    رقم الهيكل: ${vehicle.vin}")
            drawTextBlock("العميل: ${vehicle.ownerName}    الهاتف: ${vehicle.ownerPhone}")
            drawTextBlock("قراءة العداد الحالية: ${vehicle.odometer}")

            drawSection("ملخص سجل الصيانة")
            drawTextBlock("عدد أوامر الإصلاح: ${orders.size}", totalPaint)
            drawTextBlock("إجمالي الفواتير: ${formatMoney(orders.sumOf { it.totalCost })}")
            drawTextBlock("إجمالي المدفوع: ${formatMoney(orders.sumOf { it.paidAmount })}")
            drawTextBlock("إجمالي المتبقي: ${formatMoney(orders.sumOf { it.remainingAmount })}")

            if (customerNote.isNotBlank()) {
                drawSection("ملاحظات وتوصيات للعميل")
                drawTextBlock(customerNote)
            }

            drawSection("سجل أوامر الشغل")
            if (orders.isEmpty()) {
                drawTextBlock("لا توجد أوامر إصلاح مسجلة لهذه السيارة.")
            } else {
                orders.forEach { order ->
                    drawSection("أمر #${order.id} — ${order.status} — ${order.createdAt}")
                    drawTextBlock("الشكوى: ${order.complaint.ifBlank { "-" }}")
                    drawTextBlock("التشخيص: ${order.diagnosis.ifBlank { "-" }}")
                    drawTextBlock("الأعمال المنفذة: ${order.repairNotes.ifBlank { "-" }}")
                    drawTextBlock("الفني: ${order.technicianName.ifBlank { "-" }}")
                    drawTextBlock(
                        "الإجمالي: ${formatMoney(order.totalCost)} | " +
                            "المدفوع: ${formatMoney(order.paidAmount)} | " +
                            "المتبقي: ${formatMoney(order.remainingAmount)}",
                        totalPaint
                    )
                }
            }

            finishPage()
            FileOutputStream(pdfFile).use { output -> document.writeTo(output) }
            return pdfFile
        } finally {
            document.close()
        }
    }

    private fun formatMoney(value: Double): String =
        String.format(java.util.Locale("ar"), "%,.2f", value)

    private fun printInvoicePdf(wo: WorkOrderRecord) {
        try {
            printPdfFile(createInvoicePdf(wo), "Invoice_WO_${wo.id}")
        } catch (e: Exception) {
            toast("تعذر تشغيل طباعة الفاتورة: ${e.message ?: "يمكنك مشاركة ملف PDF"}")
        }
    }

    private fun printPdfFile(pdfFile: File, jobName: String) {
        val printManager = getSystemService(Context.PRINT_SERVICE) as? PrintManager
        if (printManager == null) {
            toast("خدمة الطباعة غير متوفرة؛ يمكنك مشاركة ملف PDF")
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
                if (cancellationSignal?.isCanceled == true) {
                    callback?.onLayoutCancelled()
                    return
                }
                val info = android.print.PrintDocumentInfo.Builder("$jobName.pdf")
                    .setContentType(android.print.PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(android.print.PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                    .build()
                callback?.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out android.print.PageRange>?,
                destination: android.os.ParcelFileDescriptor?,
                cancellationSignal: android.os.CancellationSignal?,
                callback: WriteResultCallback?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback?.onWriteCancelled()
                    return
                }
                if (destination == null) {
                    callback?.onWriteFailed("تعذر إنشاء ملف الطباعة")
                    return
                }
                try {
                    FileInputStream(pdfFile).use { input ->
                        android.os.ParcelFileDescriptor.AutoCloseOutputStream(destination).use { output ->
                            input.copyTo(output)
                        }
                    }
                    callback?.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback?.onWriteFailed(e.message ?: "تعذر كتابة ملف الطباعة")
                }
            }
        }
        printManager.print(jobName, adapter, PrintAttributes.Builder().build())
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
        if (db.hasPermission("work_orders", "view")) {
            addPrimaryButton("المواعيد والتنبيهات", toolsCard, gold) { appointmentsDialog() }
        }
        addSecondaryButton("الفروع وسجل التدقيق", toolsCard) { branchesAndAuditDialog() }
        addSecondaryButton("البحث المتقدم الشامل", toolsCard) { navigateTo(Screen.SEARCH) }
        addPrimaryButton("تصدير نسخة احتياطية ومشاركتها", toolsCard, navyDark) { exportAndShareBackup() }

        val acctCard = createCard("حسابي وإدارة المستخدمين", gold)
        addSecondaryButton("تعديل اسمي أو كلمة مروري", acctCard) { editOwnAccountDialog(user) }
        if (user.role == "مدير" || db.hasPermission("users", "view")) {
            addPrimaryButton("إدارة المستخدمين والصلاحيات", acctCard, navyDark) { navigateTo(Screen.USERS) }
        }

        addDangerButton("تسجيل الخروج", root) { logout() }
    }

    private fun branchesAndAuditDialog() {
        val branches = db.getBranches()
        val branchText = branches.joinToString("\n") { "${if (it.isActive) "●" else "○"} ${it.name} — ${it.address.ifBlank { "بدون عنوان" }}" }
        val audit = db.getAuditRecords(20)
        val auditText = audit.joinToString("\n") {
            "${it.createdAt} — ${it.username.ifBlank { "النظام" }} — ${it.action}${if (it.details.isNotBlank()) " (${it.details})" else ""}"
        }
        val message = "الفروع:\n${branchText.ifBlank { "لا توجد فروع" }}\n\nآخر الحركات:\n${auditText.ifBlank { "لا توجد حركات مسجلة" }}"
        val actions = mutableListOf<Pair<String, () -> Unit>>()
        if (db.hasPermission("users", "add") || db.hasPermission("users", "edit")) {
            actions += "إضافة فرع" to { addBranchDialog() }
        }
        showActionList("الفروع وسجل التدقيق", message, actions)
    }

    private fun addBranchDialog() {
        val name = field("اسم الفرع")
        val address = field("عنوان الفرع")
        val phone = field("هاتف الفرع", "phone")
        formDialog("إضافة فرع", listOf(name, address, phone), listOf(name, address, phone)) { dialog ->
            try {
                if (!db.addBranch(name.text.toString(), address.text.toString(), phone.text.toString())) {
                    throw IllegalArgumentException("تعذر إضافة الفرع")
                }
                dialog.dismiss()
                toast("تمت إضافة الفرع")
                branchesAndAuditDialog()
            } catch (e: Exception) {
                toast(e.message ?: "تعذر إضافة الفرع")
            }
        }
    }

    private fun appointmentsDialog() {
        val appointments = db.getAppointments()
        if (appointments.isEmpty()) {
            toast("لا توجد مواعيد مجدولة")
            return
        }
        val items = appointments.map {
            "${it.startsAt} — ${it.customerName}${if (it.vehicleName.isNotBlank()) " — ${it.vehicleName}" else ""}\n${it.purpose.ifBlank { "مراجعة ورشة" }} — ${it.status}"
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("المواعيد والتنبيهات")
            .setItems(items) { _, index ->
                val appointment = appointments[index]
                val statuses = arrayOf("مجدول", "تم التأكيد", "تمت الزيارة", "ملغي")
                AlertDialog.Builder(this)
                    .setTitle("تحديث الموعد")
                    .setItems(statuses) { _, statusIndex ->
                        if (db.updateAppointmentStatus(appointment.id, statuses[statusIndex])) {
                            toast("تم تحديث الموعد")
                            appointmentsDialog()
                        } else toast("تعذر تحديث الموعد")
                    }
                    .setNegativeButton("إلغاء", null)
                    .show()
            }
            .setNegativeButton("إغلاق", null)
            .show()
    }

    private fun exportAndShareBackup() {
        try {
            val file = db.exportBackupFile()
            lastBackupFile = file
            toast("تم حفظ النسخة الاحتياطية: ${file.name}")

            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_SUBJECT, "نسخة احتياطية - خبراء هونداي وفورد")
                putExtra(Intent.EXTRA_TEXT, "نسخة احتياطية من بيانات الورشة (${file.name})")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
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
        if (db.hasPermission("vehicles", "view")) {
            val vehicleReportCard = createCard("تقارير السيارات والفوترة", gold)
            addPrimaryButton("عرض تقارير السيارات وإعادة الفوترة", vehicleReportCard, gold) {
                navigateTo(Screen.VEHICLE_REPORTS)
            }
        }

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

    private fun vehicleReportsScreen() {
        if (!allowed("vehicles", "view") || !allowed("work_orders", "view")) return
        page("تقارير السيارات والفوترة", "سجل الإصلاحات والفواتير لكل سيارة", showBack = true)

        val searchCard = createCard("البحث عن سيارة", teal)
        val queryInput = field("الماركة، الموديل، اللوحة، VIN، أو اسم المالك")
        searchCard.addView(queryInput, matchWidth().apply { setMargins(0, 0, 0, dp(6)) })
        val resultsCard = createCard("السيارات وتقاريرها", navyDark)
        val vehicles = db.getAllVehicles(limit = 500)
        val ordersByVehicle = db.getAllWorkOrders(limit = 2000).groupBy { it.vehicleId }

        val renderVehicles: () -> Unit = {
            val query = queryInput.text.toString().trim()
            val matches = if (query.isBlank()) {
                vehicles
            } else {
                (
                    vehicles.filter { vehicle ->
                        "${vehicle.make} ${vehicle.model} ${vehicle.plate} ${vehicle.vin} ${vehicle.ownerName} ${vehicle.ownerPhone}"
                            .contains(query, ignoreCase = true)
                    } + db.searchVehicles(query)
                ).distinctBy { it.id }
            }
            resultsCard.removeAllViewsInLayout()
            if (matches.isEmpty()) {
                addText(
                    if (vehicles.isEmpty()) "لا توجد سيارات مسجلة." else "لا توجد سيارات مطابقة للبحث.",
                    13.5f,
                    textMuted,
                    false,
                    resultsCard
                )
            } else {
                matches.forEach { vehicle ->
                    val vehicleOrders = ordersByVehicle[vehicle.id].orEmpty()
                    val totalBilled = vehicleOrders.sumOf { it.totalCost }
                    val totalPaid = vehicleOrders.sumOf { it.paidAmount }
                    val totalDebt = vehicleOrders.sumOf { it.remainingAmount }
                    val item = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(dp(10), dp(10), dp(10), dp(10))
                        background = GradientDrawable().apply {
                            setColor(bgLight)
                            cornerRadius = dp(12).toFloat()
                            setStroke(dp(1), borderLight)
                        }
                    }
                    addText(
                        "${vehicle.make} ${vehicle.model} ${vehicle.year}\n" +
                            "اللوحة: ${vehicle.plate.ifBlank { "-" }}  |  المالك: ${vehicle.ownerName}\n" +
                            "أوامر الإصلاح: ${vehicleOrders.size}  |  المفوتر: ${formatMoney(totalBilled)}\n" +
                            "المدفوع: ${formatMoney(totalPaid)}  |  المتبقي: ${formatMoney(totalDebt)}",
                        13.5f,
                        textDark,
                        true,
                        item
                    )
                    addPrimaryButton("عرض التقرير وإعادة طباعة الفواتير", item, teal) {
                        openVehicleReport(vehicle.id)
                    }
                    resultsCard.addView(item, matchWidth().apply { setMargins(0, dp(4), 0, dp(8)) })
                }
            }
        }

        val searchAction = { renderVehicles() }
        attachSmartKeyboardNavigation(listOf(queryInput), contentScroll, searchAction)
        addPrimaryButton("بحث", searchCard, teal, searchAction)
        renderVehicles()
    }

    private fun openVehicleReport(vehicleId: Long) {
        selectedReportVehicleId = vehicleId
        navigateTo(Screen.VEHICLE_REPORT_DETAIL)
    }

    private fun vehicleReportScreen(vehicleId: Long) {
        if (!allowed("vehicles", "view") || !allowed("work_orders", "view")) return
        val vehicle = db.getVehicleProfile(vehicleId) ?: run {
            toast("تعذر تحميل بيانات السيارة")
            navigateTo(Screen.VEHICLE_REPORTS)
            return
        }
        val orders = db.getAllWorkOrders(limit = 2000).filter { it.vehicleId == vehicleId }
        val billed = orders.sumOf { it.totalCost }
        val paid = orders.sumOf { it.paidAmount }
        val debt = orders.sumOf { it.remainingAmount }
        val customerNote = db.getVehicleReportNote(vehicleId)

        page("تقرير السيارة", "${vehicle.make} ${vehicle.model} — ${vehicle.plate}", showBack = true)
        val vehicleCard = createCard("بيانات السيارة والعميل", navyDark)
        addText(
            "السيارة: ${vehicle.make} ${vehicle.model} (${vehicle.year})\n" +
                "اللوحة: ${vehicle.plate.ifBlank { "-" }}  |  VIN: ${vehicle.vin}\n" +
                "المالك: ${vehicle.ownerName} (${vehicle.ownerPhone})\n" +
                "قراءة العداد الحالية: ${vehicle.odometer}",
            14f,
            textDark,
            true,
            vehicleCard
        )

        val summaryCard = createCard("ملخص سجل الصيانة", teal)
        addText(
            "عدد أوامر الإصلاح: ${orders.size}\n" +
                "إجمالي الفواتير: ${formatMoney(billed)}\n" +
                "إجمالي المدفوع: ${formatMoney(paid)}\n" +
                "إجمالي المتبقي: ${formatMoney(debt)}",
            14f,
            textDark,
            true,
            summaryCard
        )

        val printCard = createCard("تقرير العميل", gold)
        addPrimaryButton("طباعة تقرير السيارة PDF", printCard, teal) {
            printVehicleReportPdf(vehicleId)
        }
        addSecondaryButton("مشاركة تقرير السيارة PDF", printCard) {
            shareVehicleReportPdf(vehicleId)
        }

        val noteCard = createCard("ملاحظات العرض للعميل", navyDark)
        addText(
            customerNote.ifBlank { "لا توجد ملاحظات مخصصة لهذا التقرير." },
            13.5f,
            if (customerNote.isBlank()) textMuted else textDark,
            false,
            noteCard
        )
        if (db.hasPermission("work_orders", "edit")) {
            addSecondaryButton("تحرير ملاحظات تقرير العميل", noteCard) {
                editVehicleReportNote(vehicleId)
            }
        }

        val historyCard = createCard("سجل أوامر الشغل", navyDark)
        if (orders.isEmpty()) {
            addText("لا توجد أوامر إصلاح مسجلة لهذه السيارة.", 13.5f, textMuted, false, historyCard)
            return
        }
        orders.forEach { order ->
            val orderCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(10), dp(10), dp(10), dp(10))
                background = GradientDrawable().apply {
                    setColor(bgLight)
                    cornerRadius = dp(12).toFloat()
                    setStroke(dp(1), borderLight)
                }
            }
            addStatusBadge(order.status, orderCard)
            addText(
                "أمر #${order.id} — ${order.createdAt}\n" +
                    "الشكوى: ${order.complaint.ifBlank { "-" }}\n" +
                    "التشخيص: ${order.diagnosis.ifBlank { "-" }}\n" +
                    "الأعمال المنفذة: ${order.repairNotes.ifBlank { "-" }}\n" +
                    "الفني: ${order.technicianName.ifBlank { "-" }}\n" +
                    "الإجمالي: ${formatMoney(order.totalCost)}  |  المدفوع: ${formatMoney(order.paidAmount)}  |  المتبقي: ${formatMoney(order.remainingAmount)}",
                13.5f,
                textDark,
                false,
                orderCard
            )
            if (db.hasPermission("work_orders", "edit")) {
                addSecondaryButton("تحرير بيانات أمر الشغل الأصلية", orderCard) {
                    editWorkOrderDialog(order) { vehicleReportScreen(vehicleId) }
                }
            }
            addPrimaryButton("إصدار / إعادة طباعة فاتورة الأمر #${order.id}", orderCard, navyDark) {
                shareOrPrintInvoice(order)
            }
            historyCard.addView(orderCard, matchWidth().apply { setMargins(0, dp(4), 0, dp(8)) })
        }
    }

    private fun editVehicleReportNote(vehicleId: Long) {
        if (!allowed("work_orders", "edit")) return
        val noteInput = field("ملاحظات وتوصيات للعميل", "multiline").apply {
            setText(db.getVehicleReportNote(vehicleId))
        }
        formDialog("تحرير تقرير العميل", listOf(noteInput), listOf(noteInput)) { dialog ->
            try {
                if (!db.saveVehicleReportNote(vehicleId, noteInput.text.toString())) {
                    throw IllegalStateException("تعذر حفظ ملاحظات التقرير")
                }
                dialog.dismiss()
                toast("تم حفظ ملاحظات تقرير العميل")
                vehicleReportScreen(vehicleId)
            } catch (e: Exception) {
                toast(e.message ?: "تعذر حفظ ملاحظات التقرير")
            }
        }
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

        val executeSearch: () -> Unit = {
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
        if (cleanPhone.isBlank() && cleanIdentifier.isBlank()) {
            toast("أدخل رقم الهاتف أو رقم الهيكل أو رقم اللوحة")
            return
        }

        val customers = if (cleanPhone.isBlank()) {
            emptyList()
        } else {
            db.searchReception(cleanPhone).filter { it.type == "customer" }.distinctBy { it.id }
        }
        val normId = WorkshopDb.normalizeVin(cleanIdentifier)
        val vehicles = if (cleanIdentifier.isBlank()) {
            customers.flatMap { db.getCustomerVehicles(it.id) }.distinctBy { it.id }
        } else {
            db.searchVehicles(cleanIdentifier).filter { v ->
                (normId.length == 17 && v.vin.equals(normId, ignoreCase = true)) ||
                    v.plate.contains(cleanIdentifier, ignoreCase = true)
            }.distinctBy { it.id }
        }

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
        showMessageWithActions(
            "تعارض في الملكية",
            "السيارة مسجلة حالياً باسم: ${vehicle.ownerName}\nلن يتم تغيير الملكية تلقائياً.",
            listOf(
                "الإبقاء على المالك الحالي" to { receptionChoices(vehicle.customerId, vehicleId) },
                "نقل الملكية إلى العميل الجديد" to { transferOwnershipDialog(vehicleId, newCustomerId) }
            )
        )
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
        val techName = field("اسم الفني", suggestionKeys = listOf("technician"))
        val expectedDelivery = field("تاريخ التسليم المتوقع (YYYY-MM-DD)")
        val odometer = field("قراءة العداد", "number")
        val complaint = field("شكوى العميل", "multiline", listOf("complaint"))
        val diagnosis = field("التشخيص", "multiline", listOf("diagnosis"))
        val notes = field("ملاحظات الإصلاح", "multiline", listOf("repair_notes"))
        val labor = field("أجور العمل", "decimal")
        val parts = field("تكلفة القطع", "decimal")
        val discount = field("الخصم", "decimal")
        val downPayment = field("دفعة مقدمة", "decimal")

        val workOrderPhotos = mutableListOf<String>()
        val photosBtn = Button(this).apply {
            text = "إرفاق صور عند الاستلام"
            setTextColor(Color.WHITE)
            background = buttonBg(gold, gold, navyDark)
            setOnClickListener {
                pickOrCapturePhoto("صورة عند الاستلام") { savedPath ->
                    workOrderPhotos += savedPath
                    text = "إرفاق صور عند الاستلام (${workOrderPhotos.size})"
                }
            }
        }

        val inputs = listOf(techName, expectedDelivery, odometer, complaint, diagnosis, notes, labor, parts, discount, downPayment)
        val views = listOf(
            custSpinner, vehSpinner, prioritySpinner, techName, expectedDelivery,
            odometer, complaint, diagnosis, notes, labor, parts, discount, downPayment, photosBtn
        )

        formDialog("أمر شغل جديد", views, inputs) { dialog ->
            try {
                val cId = selectedCustomerId ?: customers.getOrNull(custSpinner.selectedItemPosition)?.first
                    ?: throw IllegalArgumentException("اختر العميل")
                val vItem = vehicles.getOrNull(vehSpinner.selectedItemPosition)
                    ?: throw IllegalArgumentException("لا توجد سيارة مرتبطة بالعميل")
                require(vItem.second == cId) { "السيارة لا تتبع العميل المحدد" }

                val workOrderId = db.addWorkOrder(
                    cId, vItem.first, odometer.text.toString(), complaint.text.toString(),
                    diagnosis.text.toString(), notes.text.toString(), "مفتوح",
                    labor.text.toString(), parts.text.toString(),
                    prioritySpinner.selectedItem.toString(), techName.text.toString(),
                    discount.text.toString(), expectedDelivery.text.toString(), downPayment.text.toString()
                )
                workOrderPhotos.forEachIndexed { index, photoPath ->
                    db.addWorkOrderPhoto(workOrderId, if (index == 0) "صورة عند الاستلام" else "صورة إضافية", photoPath)
                }
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
        if (resultCode != RESULT_OK) {
            pendingPhotoCallback = null
            return
        }
        val cb = pendingPhotoCallback ?: return

        val savedPath: String? = try {
            val dir = File(filesDir, "vehicle_photos").apply { mkdirs() }
            val out = File(dir, "img_${System.currentTimeMillis()}.jpg")
            when (requestCode) {
                REQ_PICK_PHOTO -> {
                    val uri: Uri? = data?.data
                    if (uri == null) {
                        null
                    } else {
                        val copied = contentResolver.openInputStream(uri)?.use { inp ->
                            FileOutputStream(out).use { fos -> inp.copyTo(fos) }
                        }
                        if (copied != null) out.absolutePath else null
                    }
                }
                REQ_CAPTURE_PHOTO -> {
                    @Suppress("DEPRECATION")
                    val bmp = data?.extras?.get("data") as? Bitmap
                    if (bmp == null) {
                        null
                    } else {
                        FileOutputStream(out).use { fos -> bmp.compress(Bitmap.CompressFormat.JPEG, 85, fos) }
                        out.absolutePath
                    }
                }
                else -> null
            }
        } catch (_: Exception) {
            toast("تعذر حفظ الصورة")
            null
        } finally {
            pendingPhotoCallback = null
        }

        if (savedPath.isNullOrBlank()) {
            toast("تعذر حفظ الصورة")
            return
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
