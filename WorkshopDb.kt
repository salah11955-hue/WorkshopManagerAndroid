
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

class WorkshopDb(context: Context) :
    SQLiteOpenHelper(context, "workshop.db", null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        createBusinessTables(db)
        createUsersTable(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            createUsersTable(db)
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

    fun createAdmin(username: String, fullName: String, password: String): Boolean {
        if (hasUsers()) return false

        val cleanUsername = username.trim().lowercase()
        if (cleanUsername.isBlank() || fullName.isBlank() || password.length < 6) {
            return false
        }

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
                val savedHash = Base64.decode(cursor.getString(0), Base64.NO_WRAP)
                val salt = Base64.decode(cursor.getString(1), Base64.NO_WRAP)
                val enteredHash = hashPassword(password, salt)

                MessageDigest.isEqual(savedHash, enteredHash)
            } catch (_: Exception) {
                false
            }
        }
    }

    private fun hashPassword(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, 120000, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    // ---------- بيانات الورشة الحالية ----------

    fun count(table: String): Int {
        require(table in setOf("customers", "vehicles", "work_orders", "parts"))
        readableDatabase.rawQuery("SELECT COUNT(*) FROM $table", null).use {
            return if (it.moveToFirst()) it.getInt(0) else 0
        }
    }

    fun customerOptions(): List<Pair<Long, String>> {
        val out = mutableListOf<Pair<Long, String>>()
        readableDatabase.rawQuery(
            "SELECT id, name, phone FROM customers ORDER BY id DESC", null
        ).use { c ->
            while (c.moveToNext()) {
                out += c.getLong(0) to "${c.getString(1)} — ${c.getString(2)}"
            }
        }
        return out
    }

    fun vehicleOptions(customerId: Long? = null): List<Triple<Long, Long, String>> {
        val out = mutableListOf<Triple<Long, Long, String>>()

        val sql = if (customerId == null) {
            "SELECT id, customer_id, make, model, plate FROM vehicles ORDER BY id DESC"
        } else {
            "SELECT id, customer_id, make, model, plate FROM vehicles WHERE customer_id=? ORDER BY id DESC"
        }

        readableDatabase.rawQuery(
            sql,
            if (customerId == null) null else arrayOf(customerId.toString())
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

    fun addCustomer(name: String, phone: String, alt: String, address: String): Long {
        val values = ContentValues().apply {
            put("name", name.trim())
            put("phone", normalizePhone(phone))
            put("alternate_phones", alt)
            put("address", address)
        }
        return writableDatabase.insertOrThrow("customers", null, values)
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
        val values = ContentValues().apply {
            put("customer_id", customerId)
            put("make", make.trim())
            put("model", model.trim())
            put("model_year", year.toIntOrNull())
            put("engine", engine.trim())
            put("plate", plate.trim())
            put("vin", normalizeVin(vin))
            put("current_odometer", odometer.toLongOrNull() ?: 0L)
        }
        return writableDatabase.insertOrThrow("vehicles", null, values)
    }

    fun addWorkOrder(
        customerId: Long,
        vehicleId: Long,
        odometer: String,
        complaint: String,
        diagnosis: String
    ) {
        val values = ContentValues().apply {
            put("customer_id", customerId)
            put("vehicle_id", vehicleId)
            put("odometer_at_visit", odometer.toLongOrNull() ?: 0L)
            put("complaint", complaint)
            put("diagnosis", diagnosis)
        }

        writableDatabase.insertOrThrow("work_orders", null, values)

        writableDatabase.execSQL(
            "UPDATE vehicles SET current_odometer=MAX(current_odometer, ?) WHERE id=?",
            arrayOf(odometer.toLongOrNull() ?: 0L, vehicleId)
        )
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

    fun recentRows(table: String): List<String> {
        val sql = when (table) {
            "customers" -> "SELECT name, phone FROM customers ORDER BY id DESC LIMIT 30"
            "vehicles" -> "SELECT make || ' ' || model, vin FROM vehicles ORDER BY id DESC LIMIT 30"
            "work_orders" -> "SELECT 'أمر #' || id, complaint FROM work_orders ORDER BY id DESC LIMIT 30"
            else -> "SELECT name, 'الكمية: ' || quantity || ' ' || unit FROM parts ORDER BY id DESC LIMIT 30"
        }

        val out = mutableListOf<String>()
        readableDatabase.rawQuery(sql, null).use { c ->
            while (c.moveToNext()) {
                out += "${c.getString(0)}\n${c.getString(1)}"
            }
        }
        return out
    }

    // ---------- الاقتراحات التلقائية أثناء الكتابة ----------

    fun suggestions(key: String, prefix: String): List<String> {
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
            "customer", "customer_name", "name", "phone", "customer_phone" -> "customers"
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
                    in '٠'..'٩' -> ('0'.code + (ch.code - '٠'.code)).toChar()
                    in '۰'..'۹' -> ('0'.code + (ch.code - '۰'.code)).toChar()
                    else -> null
                }
            }.joinToString("")
        }

        fun normalizeVin(raw: String): String =
            raw.uppercase().filter { it.isLetterOrDigit() }
    }
}
