package com.khubara.workshop

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import android.graphics.Typeface
import android.graphics.Color

class MainActivity : Activity() {
    private lateinit var db: WorkshopDb
    private lateinit var root: LinearLayout
    private val navy = Color.rgb(16, 42, 67)
    private val gold = Color.rgb(199, 154, 53)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = WorkshopDb(this)
        showDashboard()
    }

    private fun showDashboard() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(22, 24, 22, 24)
            setBackgroundColor(Color.rgb(246, 248, 250)); layoutDirection = LinearLayout.LAYOUT_DIRECTION_RTL
        }
        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)
        val header = TextView(this).apply {
            text = "خبراء هونداي وفورد\nإدارة الورشة"; textSize = 25f; setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD); gravity = Gravity.CENTER; setPadding(12, 28, 12, 28)
            setBackgroundColor(navy)
        }
        root.addView(header, matchWidth())
        val subtitle = TextView(this).apply {
            text = "نظام محلي يعمل دون إنترنت"; textSize = 14f; setTextColor(navy)
            gravity = Gravity.CENTER; setPadding(8, 14, 8, 14)
        }
        root.addView(subtitle)
        val stats = listOf("العملاء" to "customers", "السيارات" to "vehicles", "أوامر الإصلاح" to "work_orders", "قطع المخزون" to "parts")
        stats.chunked(2).forEach { pair ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            pair.forEach { (label, table) ->
                val card = TextView(this).apply {
                    text = "$label\n${db.count(table)}"; textSize = 18f; gravity = Gravity.CENTER
                    setTextColor(navy); setTypeface(null, Typeface.BOLD); setPadding(8, 22, 8, 22)
                    setBackgroundColor(Color.WHITE)
                }
                row.addView(card, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(5, 5, 5, 5) })
            }
            root.addView(row)
        }
        addAction("+ تسجيل عميل", gold) { customerForm() }
        addAction("+ تسجيل سيارة لعميل", navy) { vehicleForm() }
        addAction("+ فتح أمر إصلاح", navy) { workOrderForm() }
        addAction("+ إضافة قطعة للمخزون", navy) { partForm() }
        addAction("عرض السجلات", Color.DKGRAY) { recordsDialog() }
        val note = TextView(this).apply {
            text = "الإصدار التأسيسي 0.1.0 — البيانات محفوظة على الهاتف.\nالمزامنة السحابية والطباعة والصلاحيات المتقدمة ستضاف في المراحل التالية."
            textSize = 12f; setTextColor(Color.GRAY); gravity = Gravity.CENTER; setPadding(8, 22, 8, 8)
        }
        root.addView(note)
    }

    private fun addAction(label: String, color: Int, action: () -> Unit) {
        val b = Button(this).apply { text = label; setTextColor(Color.WHITE); setBackgroundColor(color); textSize = 16f; setOnClickListener { action() } }
        root.addView(b, matchWidth().apply { setMargins(0, 7, 0, 7) })
    }

    private fun customerForm() {
        val name = field("اسم العميل")
        val phone = field("رقم الهاتف الأساسي (إلزامي)", "phone")
        val alt = field("أرقام بديلة، افصل بينها بفاصلة")
        val address = field("العنوان")
        formDialog("عميل جديد", listOf(name, phone, alt, address)) {
            if (name.text.isBlank() || phone.text.isBlank()) return@formDialog toast("أدخل الاسم ورقم الهاتف")
            try { db.addCustomer(name.text.toString(), phone.text.toString(), alt.text.toString(), address.text.toString()); done() }
            catch (_: Exception) { toast("رقم الهاتف مسجل مسبقاً أو توجد بيانات غير صحيحة") }
        }
    }

    private fun vehicleForm() {
        val customers = db.customerOptions()
        if (customers.isEmpty()) return toast("سجّل العميل أولاً")
        val owner = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, customers.map { it.second }) }
        val make = field("الشركة، مثال Hyundai")
        val model = field("الموديل")
        val year = field("سنة الصنع")
        val engine = field("المحرك")
        val plate = field("رقم اللوحة")
        val vin = field("رقم الهيكل VIN (إلزامي)")
        val odo = field("عداد الكيلومترات الحالي")
        formDialog("إضافة سيارة", listOf(owner, make, model, year, engine, plate, vin, odo)) {
            if (vin.text.isBlank()) return@formDialog toast("رقم الهيكل مطلوب")
            try { db.addVehicle(customers[owner.selectedItemPosition].first, make.text.toString(), model.text.toString(), year.text.toString(), engine.text.toString(), plate.text.toString(), vin.text.toString(), odo.text.toString()); done() }
            catch (_: Exception) { toast("رقم الهيكل مسجل مسبقاً، راجع ملف المالك الحالي") }
        }
    }

    private fun workOrderForm() {
        val vehicles = db.vehicleOptions()
        if (vehicles.isEmpty()) return toast("سجّل سيارة أولاً")
        val vehicle = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, vehicles.map { it.third }) }
        val odo = field("قراءة العداد عند الزيارة")
        val complaint = field("شكوى العميل")
        val diagnosis = field("التشخيص الفني الأولي")
        formDialog("أمر إصلاح جديد", listOf(vehicle, odo, complaint, diagnosis)) {
            val selected = vehicles[vehicle.selectedItemPosition]
            try { db.addWorkOrder(selected.second, selected.first, odo.text.toString(), complaint.text.toString(), diagnosis.text.toString()); done() }
            catch (_: Exception) { toast("تعذر حفظ أمر الإصلاح") }
        }
    }

    private fun partForm() {
        val number = field("رقم القطعة")
        val name = field("اسم القطعة (إلزامي)")
        val qty = field("الكمية")
        val unit = field("الوحدة، مثال قطعة")
        val buy = field("سعر الشراء")
        val sell = field("سعر البيع")
        formDialog("إضافة قطعة للمخزون", listOf(number, name, qty, unit, buy, sell)) {
            if (name.text.isBlank()) return@formDialog toast("اسم القطعة مطلوب")
            try { db.addPart(number.text.toString(), name.text.toString(), qty.text.toString(), unit.text.toString().ifBlank { "قطعة" }, buy.text.toString(), sell.text.toString()); done() }
            catch (_: Exception) { toast("تعذر حفظ القطعة") }
        }
    }

    private fun recordsDialog() {
        val labels = arrayOf("العملاء", "السيارات", "أوامر الإصلاح", "المخزون")
        val tables = arrayOf("customers", "vehicles", "work_orders", "parts")
        AlertDialog.Builder(this).setTitle("السجلات").setItems(labels) { _, which ->
            val rows = db.recentRows(tables[which])
            val text = if (rows.isEmpty()) "لا توجد سجلات بعد" else rows.joinToString("\n\n────────────\n\n")
            AlertDialog.Builder(this).setTitle(labels[which]).setMessage(text).setPositiveButton("إغلاق", null).show()
        }.setNegativeButton("إغلاق", null).show()
    }

    private fun field(hintText: String, type: String = "text"): EditText = EditText(this).apply {
        hint = hintText; textSize = 15f; singleLine = type == "phone" || hintText.contains("السنة") || hintText.contains("العداد")
        if (type == "phone") inputType = android.text.InputType.TYPE_CLASS_PHONE
        setPadding(12, 8, 12, 8)
    }

    private fun formDialog(title: String, views: List<android.view.View>, save: () -> Unit) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 5, 20, 5); layoutDirection = LinearLayout.LAYOUT_DIRECTION_RTL }
        val scroller = ScrollView(this)
        views.forEach { view -> box.addView(view, matchWidth()) }
        scroller.addView(box)
        AlertDialog.Builder(this).setTitle(title).setView(scroller)
            .setPositiveButton("حفظ", null).setNegativeButton("إلغاء", null).create().also { dialog ->
                dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { save() } }
                dialog.show()
            }
    }

    private fun done() { toast("تم الحفظ بنجاح"); showDashboard() }
    private fun toast(message: String) { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
    private fun matchWidth() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
}
