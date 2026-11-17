
package com.khubara.workshop

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

data class VehicleProfile(
    val id: Long,
    val customerId: Long,
    val make: String,
    val model: String,
    val year: String,
    val engine: String,
    val plate: String,
    val vin: String,
    val odometer: Long,
    val notes: String,
    val ownerName: String,
    val ownerPhone: String
)

data class OwnerHistoryRecord(
    val oldOwner: String,
    val newOwner: String,
    val reason: String,
    val changedAt: String
)

data class WorkOrderHistoryRecord(
    val id: Long,
    val customerName: String,
    val odometer: Long,
    val complaint: String,
    val diagnosis: String,
    val repairNotes: String,
    val status: String,
    val laborCost: Double,
    val partsCost: Double,
    val createdAt: String
)

class WorkshopDb(context: Context) :
    SQLiteOpenHelper(context, "workshop.db", null, 3) {
    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }
    override fun onCreate(db: SQLiteDatabase) {
        createBusinessTables(db)
        createUsersTable(db)
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int
    ) {
        if (oldVersion < 2) {
            createUsersTable(db)
        }

        if (oldVersion < 3) {
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_vehicle_plate ON vehicles(plate)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_owner_vehicle ON vehicle_owners(vehicle_id)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_work_vehicle_date ON work_orders(vehicle_id, created_at)"
            )
        }
    }

    private fun createBusinessTables(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE customers (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            phone TEXT NOT NULL UNIQUE,
            name TEXT NOT NULL DEFAULT '',
            alternate_phones TEXT NOT NULL DEFAULT '',
            address TEXT NOT NULL DEFAULT '',
            notes TEXT NOT NULL DEFAULT '',
            created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
        )""")

        db.execSQL("""CREATE TABLE vehicles (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            customer_id INTEGER NOT NULL,
            make TEXT NOT NULL DEFAULT '',
            model TEXT NOT NULL DEFAULT '',
            model_year INTEGER,
            engine TEXT NOT NULL DEFAULT '',
            plate TEXT NOT NULL DEFAULT '',
            vin TEXT NOT NULL UNIQUE,
            current_odometer INTEGER NOT NULL DEFAULT 0,
            notes TEXT NOT NULL DEFAULT '',
            FOREIGN KEY(customer_id) REFERENCES customers(id)
        )""")

        db.execSQL("""CREATE TABLE work_orders (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            customer_id INTEGER NOT NULL,
            vehicle_id INTEGER NOT NULL,
            odometer_at_visit INTEGER NOT NULL DEFAULT 0,
            complaint TEXT NOT NULL DEFAULT '',
            diagnosis TEXT NOT NULL DEFAULT '',
            repair_notes TEXT NOT NULL DEFAULT '',
            status TEXT NOT NULL DEFAULT 'مفتوح',
            labor_cost REAL NOT NULL DEFAULT 0,
            parts_cost REAL NOT NULL DEFAULT 0,
            created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY(customer_id) REFERENCES customers(id),
            FOREIGN KEY(vehicle_id) REFERENCES vehicles(id)
        )""")

        db.execSQL("""CREATE TABLE parts (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            part_number TEXT NOT NULL DEFAULT '',
            name TEXT NOT NULL,
            quantity REAL NOT NULL DEFAULT 0,
            unit TEXT NOT NULL DEFAULT 'قطعة',
            purchase_price REAL NOT NULL DEFAULT 0,
            sale_price REAL NOT NULL DEFAULT 0,
            min_quantity REAL NOT NULL DEFAULT 0
        )""")

        db.execSQL("""CREATE TABLE vehicle_owners (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            vehicle_id INTEGER NOT NULL,
            old_customer_id INTEGER NOT NULL,
            new_customer_id INTEGER NOT NULL,
            reason TEXT NOT NULL,
            changed_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
        )""")

        db.execSQL("CREATE INDEX idx_vehicle_customer ON vehicles(customer_id)")
        db.execSQL("CREATE INDEX idx_work_vehicle ON work_orders(vehicle_id)")
        db.execSQL("CREATE INDEX idx_vehicle_plate ON vehicles(plate)")
        db.execSQL("CREATE INDEX idx_owner_vehicle ON vehicle_owners(vehicle_id)")
        db.execSQL("CREATE INDEX idx_work_vehicle_date ON work_orders(vehicle_id, created_at)")
    }

    private fun createUsersTable(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS app_users (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            username TEXT NOT NULL UNIQUE,
            full_name TEXT NOT NULL,
            password_hash TEXT NOT NULL,
            password_salt TEXT NOT NULL,
            role TEXT NOT NULL DEFAULT 'مدير',
            created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
        )""")
    }

    // ---------- حسابات المستخدمين ----------

    fun hasUsers(): Boolean {
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM app_users", null
        ).use {
            return it.moveToFirst() && it.getInt(0) > 0
        }
    }

    fun createAdmin(
        username: String,
        fullName: String,
        password: String
    ): Boolean {
        if (hasUsers()) return false

        val cleanUsername = username.trim().lowercase()
        if (cleanUsername.isBlank() ||
            fullName.isBlank() ||
            password.length < 6
        ) return false

        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        val hash = hashPassword(password, salt)

        val values = ContentValues().apply {
            put("username", cleanUsername)
            put("full_name", fullName.trim())
            put("password_hash", Base64.encodeToString(hash, Base64.NO_WRAP))
            put("password_salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            put("role", "مدير")
        }

        return try {
            writableDatabase.insertOrThrow("app_users", null, values)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun authenticate(username: String, password: String): Boolean {
        readableDatabase.rawQuery(
            """SELECT password_hash, password_salt
               FROM app_users WHERE username=? LIMIT 1""",
            arrayOf(username.trim().lowercase())
        ).use { cursor ->

            if (!cursor.moveToFirst()) return false

            return try {
                val savedHash =
                    Base64.decode(cursor.getString(0), Base64.NO_WRAP)
                val salt =
                    Base64.decode(cursor.getString(1), Base64.NO_WRAP)
                val enteredHash = hashPassword(password, salt)

                MessageDigest.isEqual(savedHash, enteredHash)
            } catch (_: Exception) {
                false
            }
        }
    }

    private fun hashPassword(
        password: String,
        salt: ByteArray
    ): ByteArray {
        val spec = PBEKeySpec(
            password.toCharArray(),
            salt,
            120000,
            256
        )

        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    // ---------- بيانات الورشة ----------

    fun count(table: String): Int {
        require(
            table in setOf(
                "customers", "vehicles", "work_orders", "parts"
            )
        )

        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM $table", null
        ).use {
            return if (it.moveToFirst()) it.getInt(0) else 0
        }
    }

    fun customerOptions(): List<Pair<Long, String>> {
        val out = mutableListOf<Pair<Long, String>>()

        readableDatabase.rawQuery(
            "SELECT id, name, phone FROM customers ORDER BY id DESC",
            null
        ).use { c ->
            while (c.moveToNext()) {
                out += c.getLong(0) to
                    "${c.getString(1)} — ${c.getString(2)}"
            }
        }

        return out
    }

    fun vehicleOptions(
        customerId: Long? = null
    ): List<Triple<Long, Long, String>> {
        val out = mutableListOf<Triple<Long, Long, String>>()

        val sql = if (customerId == null) {
            """SELECT id, customer_id, make, model, plate
               FROM vehicles ORDER BY id DESC"""
        } else {
            """SELECT id, customer_id, make, model, plate
               FROM vehicles WHERE customer_id=? ORDER BY id DESC"""
        }

        readableDatabase.rawQuery(
            sql,
            if (customerId == null) null
            else arrayOf(customerId.toString())
        ).use { c ->
            while (c.moveToNext()) {
                out += Triple(
                    c.getLong(0),
                    c.getLong(1),
                    "${c.getString(2)} ${c.getString(3)} (${c.getString(4)})"
                )
            }
        }

        return out
    }

    fun addCustomer(
        name: String,
        phone: String,
        alt: String,
        address: String
    ): Long {
        val values = ContentValues().apply {
            put("name", name.trim())
            put("phone", normalizePhone(phone))
            put("alternate_phones", alt)
            put("address", address)
        }

        return writableDatabase.insertOrThrow(
            "customers", null, values
        )
    }

    fun addVehicle(
        customerId: Long,
        make: String,
        model: String,
        year: String,
        engine: String,
        plate: String,
        vin: String,
        odometer: String
    ): Long {
        val cleanVin = normalizeVin(vin)

        require(cleanVin.length == 17) {
            "رقم الهيكل يجب أن يتكون من 17 خانة بالضبط"
        }

        require(!cleanVin.any { it in "IOQ" }) {
            "رقم الهيكل لا يسمح بالأحرف I و O و Q"
        }

        val values = ContentValues().apply {
            put("customer_id", customerId)
            put("make", make.trim())
            put("model", model.trim())
            put("model_year", year.toIntOrNull())
            put("engine", engine.trim())
            put("plate", plate.trim())
            put("vin", cleanVin)
            put("current_odometer", odometer.toLongOrNull() ?: 0L)
        }

        return writableDatabase.insertOrThrow(
            "vehicles", null, values
        )
    }

    
fun addWorkOrder(
    customerId: Long,
    vehicleId: Long,
    odometer: String,
    complaint: String,
    diagnosis: String,
    repairNotes: String = "",
    status: String = "مفتوح",
    laborCost: String = "0",
    partsCost: String = "0"
) {
    val reading = odometer.toLongOrNull() ?: 0L

    val labor = laborCost.trim().ifBlank { "0" }.toDoubleOrNull()
        ?: throw IllegalArgumentException("قيمة أجور العمل غير صحيحة")

    val parts = partsCost.trim().ifBlank { "0" }.toDoubleOrNull()
        ?: throw IllegalArgumentException("قيمة قطع الغيار غير صحيحة")

    require(labor.isFinite() && labor >= 0) {
        "أجور العمل يجب أن تكون رقماً موجباً"
    }

    require(parts.isFinite() && parts >= 0) {
        "تكلفة القطع يجب أن تكون رقماً موجباً"
    }

    val cleanStatus = status.trim().ifBlank { "مفتوح" }

    require(cleanStatus in setOf("مفتوح", "قيد العمل", "مكتمل")) {
        "حالة أمر الإصلاح غير صحيحة"
    }

    val database = writableDatabase

    database.beginTransaction()
    try {
        val values = ContentValues().apply {
            put("customer_id", customerId)
            put("vehicle_id", vehicleId)
            put("odometer_at_visit", reading)
            put("complaint", complaint)
            put("diagnosis", diagnosis)
            put("repair_notes", repairNotes.trim())
            put("status", cleanStatus)
            put("labor_cost", labor)
            put("parts_cost", parts)
        }

        database.insertOrThrow("work_orders", null, values)

        database.execSQL(
            """UPDATE vehicles
               SET current_odometer=MAX(current_odometer, ?)
               WHERE id=?""",
            arrayOf(reading, vehicleId)
        )

        database.setTransactionSuccessful()
    } finally {
        database.endTransaction()
    }
}


    fun addPart(
        number: String,
        name: String,
        qty: String,
        unit: String,
        buy: String,
        sell: String
    ) {
        val values = ContentValues().apply {
            put("part_number", number)
            put("name", name.trim())
            put("quantity", qty.toDoubleOrNull() ?: 0.0)
            put("unit", unit)
            put("purchase_price", buy.toDoubleOrNull() ?: 0.0)
            put("sale_price", sell.toDoubleOrNull() ?: 0.0)
        }

        writableDatabase.insertOrThrow("parts", null, values)
    }

    // ---------- البحث عن السيارة ----------

    fun searchVehicles(query: String): List<VehicleProfile> {
        val clean = query.trim()
        if (clean.isBlank()) return emptyList()

        val vinQuery = normalizeVin(clean)
        val sql = """
            SELECT
                v.id, v.customer_id, v.make, v.model,
                v.model_year, v.engine, v.plate, v.vin,
                v.current_odometer, v.notes,
                c.name, c.phone
            FROM vehicles v
            LEFT JOIN customers c ON c.id = v.customer_id
            WHERE v.vin LIKE ?
               OR v.plate LIKE ? COLLATE NOCASE
               OR v.make LIKE ? COLLATE NOCASE
               OR v.model LIKE ? COLLATE NOCASE
               OR c.name LIKE ? COLLATE NOCASE
            ORDER BY v.id DESC
            LIMIT 50
        """.trimIndent()

        val pattern = "%$clean%"
        val out = mutableListOf<VehicleProfile>()

        readableDatabase.rawQuery(
            sql,
            arrayOf(
                "%$vinQuery%",
                pattern,
                pattern,
                pattern,
                pattern
            )
        ).use { c ->
            while (c.moveToNext()) {
                out += VehicleProfile(
                    id = c.getLong(0),
                    customerId = c.getLong(1),
                    make = c.getString(2) ?: "",
                    model = c.getString(3) ?: "",
                    year = if (c.isNull(4)) "" else c.getInt(4).toString(),
                    engine = c.getString(5) ?: "",
                    plate = c.getString(6) ?: "",
                    vin = c.getString(7) ?: "",
                    odometer = c.getLong(8),
                    notes = c.getString(9) ?: "",
                    ownerName = c.getString(10) ?: "",
                    ownerPhone = c.getString(11) ?: ""
                )
            }
        }

        return out
    }

    // ---------- تفاصيل ملف السيارة ----------

    fun getVehicleProfile(vehicleId: Long): VehicleProfile? {
        val sql = """
            SELECT
                v.id, v.customer_id, v.make, v.model,
                v.model_year, v.engine, v.plate, v.vin,
                v.current_odometer, v.notes,
                c.name, c.phone
            FROM vehicles v
            LEFT JOIN customers c ON c.id = v.customer_id
            WHERE v.id=?
            LIMIT 1
        """.trimIndent()

        readableDatabase.rawQuery(
            sql,
            arrayOf(vehicleId.toString())
        ).use { c ->
            if (!c.moveToFirst()) return null

            return VehicleProfile(
                id = c.getLong(0),
                customerId = c.getLong(1),
                make = c.getString(2) ?: "",
                model = c.getString(3) ?: "",
                year = if (c.isNull(4)) "" else c.getInt(4).toString(),
                engine = c.getString(5) ?: "",
                plate = c.getString(6) ?: "",
                vin = c.getString(7) ?: "",
                odometer = c.getLong(8),
                notes = c.getString(9) ?: "",
                ownerName = c.getString(10) ?: "",
                ownerPhone = c.getString(11) ?: ""
            )
        }
    }

    // ---------- سجل الملاك ----------

    fun getVehicleOwnerHistory(
        vehicleId: Long
    ): List<OwnerHistoryRecord> {
        val out = mutableListOf<OwnerHistoryRecord>()

        val sql = """
            SELECT
                old_customer.name,
                new_customer.name,
                h.reason,
                h.changed_at
            FROM vehicle_owners h
            LEFT JOIN customers old_customer
                ON old_customer.id = h.old_customer_id
            LEFT JOIN customers new_customer
                ON new_customer.id = h.new_customer_id
            WHERE h.vehicle_id=?
            ORDER BY h.id DESC
        """.trimIndent()

        readableDatabase.rawQuery(
            sql,
            arrayOf(vehicleId.toString())
        ).use { c ->
            while (c.moveToNext()) {
                out += OwnerHistoryRecord(
                    oldOwner = c.getString(0) ?: "غير معروف",
                    newOwner = c.getString(1) ?: "غير معروف",
                    reason = c.getString(2) ?: "",
                    changedAt = c.getString(3) ?: ""
                )
            }
        }

        return out
    }

    // ---------- نقل ملكية السيارة ----------

    fun transferVehicleOwnership(
        vehicleId: Long,
        newCustomerId: Long,
        reason: String
    ): Boolean {
        val database = writableDatabase
        database.beginTransaction()

        try {
            val oldCustomerId = database.rawQuery(
                "SELECT customer_id FROM vehicles WHERE id=?",
                arrayOf(vehicleId.toString())
            ).use { c ->
                if (!c.moveToFirst()) return false
                c.getLong(0)
            }

            if (oldCustomerId == newCustomerId) return false

            val customerExists = database.rawQuery(
                "SELECT id FROM customers WHERE id=?",
                arrayOf(newCustomerId.toString())
            ).use { it.moveToFirst() }

            if (!customerExists) return false

            val history = ContentValues().apply {
                put("vehicle_id", vehicleId)
                put("old_customer_id", oldCustomerId)
                put("new_customer_id", newCustomerId)
                put(
                    "reason",
                    reason.trim().ifBlank { "نقل ملكية السيارة" }
                )
            }

            database.insertOrThrow(
                "vehicle_owners", null, history
            )

            val update = ContentValues().apply {
                put("customer_id", newCustomerId)
            }

            val changed = database.update(
                "vehicles",
                update,
                "id=?",
                arrayOf(vehicleId.toString())
            )

            if (changed != 1) return false

            database.setTransactionSuccessful()
            return true
        } catch (_: Exception) {
            return false
        } finally {
            database.endTransaction()
        }
    }

    // ---------- سجل زيارات السيارة ----------

    fun getVehicleWorkOrders(
        vehicleId: Long
    ): List<WorkOrderHistoryRecord> {
        val out = mutableListOf<WorkOrderHistoryRecord>()

        val sql = """
            SELECT
                w.id,
                c.name,
                w.odometer_at_visit,
                w.complaint,
                w.diagnosis,
                w.repair_notes,
                w.status,
                w.labor_cost,
                w.parts_cost,
                w.created_at
            FROM work_orders w
            LEFT JOIN customers c ON c.id = w.customer_id
            WHERE w.vehicle_id=?
            ORDER BY w.id DESC
        """.trimIndent()

        readableDatabase.rawQuery(
            sql,
            arrayOf(vehicleId.toString())
        ).use { c ->
            while (c.moveToNext()) {
                out += WorkOrderHistoryRecord(
                    id = c.getLong(0),
                    customerName = c.getString(1) ?: "غير معروف",
                    odometer = c.getLong(2),
                    complaint = c.getString(3) ?: "",
                    diagnosis = c.getString(4) ?: "",
                    repairNotes = c.getString(5) ?: "",
                    status = c.getString(6) ?: "",
                    laborCost = c.getDouble(7),
                    partsCost = c.getDouble(8),
                    createdAt = c.getString(9) ?: ""
                )
            }
        }

        return out
    }

    // ---------- السجلات العامة ----------

    fun recentRows(table: String): List<String> {
        val sql = when (table) {
            "customers" ->
                "SELECT name, phone FROM customers ORDER BY id DESC LIMIT 30"

            "vehicles" ->
                """SELECT make || ' ' || model, vin
                   FROM vehicles ORDER BY id DESC LIMIT 30"""

            "work_orders" ->
                """SELECT 'أمر #' || id, complaint
                   FROM work_orders ORDER BY id DESC LIMIT 30"""

            else ->
                """SELECT name, 'الكمية: ' || quantity || ' ' || unit
                   FROM parts ORDER BY id DESC LIMIT 30"""
        }

        val out = mutableListOf<String>()

        readableDatabase.rawQuery(sql, null).use { c ->
            while (c.moveToNext()) {
                out += "${c.getString(0)}\n${c.getString(1)}"
            }
        }

        return out
    }

    // ---------- فحص السجلات المكررة ----------

    fun findCustomerByPhone(phone: String): String? {
        val clean = normalizePhone(phone)
        if (clean.isBlank()) return null

        readableDatabase.rawQuery(
            "SELECT name FROM customers WHERE phone=? LIMIT 1",
            arrayOf(clean)
        ).use { c ->
            return if (c.moveToFirst()) c.getString(0) else null
        }
    }

    fun findVehicleByVin(vin: String): String? {
        val clean = normalizeVin(vin)
        if (clean.isBlank()) return null

        readableDatabase.rawQuery(
            """SELECT make, model, plate
               FROM vehicles WHERE vin=? LIMIT 1""",
            arrayOf(clean)
        ).use { c ->
            return if (c.moveToFirst()) {
                "${c.getString(0)} ${c.getString(1)} — اللوحة: ${c.getString(2)}"
            } else null
        }
    }

    fun findVehicleByPlate(plate: String): String? {
        val clean = plate.trim()
        if (clean.isBlank()) return null

        readableDatabase.rawQuery(
            """SELECT make, model, vin
               FROM vehicles
               WHERE TRIM(plate)=? COLLATE NOCASE
               LIMIT 1""",
            arrayOf(clean)
        ).use { c ->
            return if (c.moveToFirst()) {
                "${c.getString(0)} ${c.getString(1)} — VIN: ${c.getString(2)}"
            } else null
        }
    }

    fun findPartByNumber(number: String): String? {
        val clean = number.trim()
        if (clean.isBlank()) return null

        readableDatabase.rawQuery(
            """SELECT name FROM parts
               WHERE TRIM(part_number)=? COLLATE NOCASE
               LIMIT 1""",
            arrayOf(clean)
        ).use { c ->
            return if (c.moveToFirst()) c.getString(0) else null
        }
    }

    // ---------- الاقتراحات التلقائية ----------

    fun suggestions(
        key: String,
        prefix: String
    ): List<String> {
        val cleanPrefix = when (key) {
            "phone", "customer_phone" -> normalizePhone(prefix)
            "vin" -> normalizeVin(prefix)
            else -> prefix.trim()
        }

        if (cleanPrefix.isBlank()) return emptyList()

        val column = when (key) {
            "customer", "customer_name", "name" -> "customers.name"
            "phone", "customer_phone" -> "customers.phone"
            "plate" -> "vehicles.plate"
            "vin" -> "vehicles.vin"
            "part_number" -> "parts.part_number"
            "part_name" -> "parts.name"
            else -> return emptyList()
        }

        val table = when (key) {
            "customer", "customer_name", "name",
            "phone", "customer_phone" -> "customers"

            "plate", "vin" -> "vehicles"

            "part_number", "part_name" -> "parts"

            else -> return emptyList()
        }

        val sql = """
            SELECT DISTINCT $column
            FROM $table
            WHERE $column LIKE ? COLLATE NOCASE
              AND TRIM($column) != ''
            ORDER BY $column
            LIMIT 10
        """.trimIndent()

        val results = mutableListOf<String>()

        readableDatabase.rawQuery(
            sql,
            arrayOf("$cleanPrefix%")
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val value = cursor.getString(0)
                if (!value.isNullOrBlank()) {
                    results.add(value)
                }
            }
        }

        return results
    }

    companion object {
        fun normalizePhone(raw: String): String {
            return raw.trim().mapNotNull { ch ->
                when (ch) {
                    in '0'..'9' -> ch
                    in '٠'..'٩' ->
                        ('0'.code + (ch.code - '٠'.code)).toChar()

                    in '۰'..'۹' ->
                        ('0'.code + (ch.code - '۰'.code)).toChar()

                    else -> null
                }
            }.joinToString("")
        }

        fun normalizeVin(raw: String): String =
            raw.uppercase().filter { it.isLetterOrDigit() }
    }
}
