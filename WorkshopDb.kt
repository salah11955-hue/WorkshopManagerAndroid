package com.khubara.workshop

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

data class CustomerProfile(
    val id: Long,
    val name: String,
    val phone: String,
    val alternatePhones: String,
    val email: String,
    val city: String,
    val customerType: String,
    val address: String,
    val notes: String,
    val totalDebt: Double = 0.0
)

data class VehiclePhotoItem(
    val id: Long,
    val vehicleId: Long,
    val label: String,
    val photoPath: String,
    val createdAt: String
)

data class VehicleProfile(
    val id: Long,
    val customerId: Long,
    val make: String,
    val model: String,
    val year: String,
    val engine: String,
    val color: String,
    val fuelType: String,
    val transmission: String,
    val plate: String,
    val vin: String,
    val odometer: Long,
    val notes: String,
    val ownerName: String,
    val ownerPhone: String,
    val carPhotoPath: String = "",
    val fuelGaugePhotoPath: String = "",
    val photos: List<VehiclePhotoItem> = emptyList()
)

data class WorkOrderPaymentRecord(
    val id: Long,
    val workOrderId: Long,
    val amount: Double,
    val method: String,
    val note: String,
    val paidAt: String
)

data class WorkOrderRecord(
    val id: Long,
    val customerId: Long,
    val customerName: String,
    val customerPhone: String,
    val vehicleId: Long,
    val vehicleName: String,
    val vehiclePlate: String,
    val odometer: Long,
    val complaint: String,
    val diagnosis: String,
    val repairNotes: String,
    val status: String,
    val priority: String,
    val technicianName: String,
    val expectedDelivery: String,
    val laborCost: Double,
    val partsCost: Double,
    val discount: Double,
    val downPayment: Double,
    val additionalPaid: Double,
    val createdAt: String
) {
    val totalCost: Double
        get() = (laborCost + partsCost - discount).coerceAtLeast(0.0)

    val paidAmount: Double
        get() = (downPayment + additionalPaid).coerceAtLeast(0.0)

    val remainingAmount: Double
        get() = (totalCost - paidAmount).coerceAtLeast(0.0)

    val paymentPercent: Int
        get() = when {
            totalCost <= 0.0 -> if (paidAmount > 0.0) 100 else 0
            else -> ((paidAmount / totalCost) * 100.0).coerceIn(0.0, 100.0).toInt()
        }
}

data class PartItem(
    val id: Long,
    val partNumber: String,
    val name: String,
    val quantity: Double,
    val unit: String,
    val purchasePrice: Double,
    val salePrice: Double,
    val minQuantity: Double,
    val supplier: String,
    val shelfLocation: String,
    val barcode: String
) {
    val isLowStock: Boolean
        get() = quantity <= minQuantity
}

data class OwnerHistoryRecord(
    val oldOwner: String,
    val newOwner: String,
    val reason: String,
    val changedAt: String
)

data class ReceptionSearchResult(
    val type: String,
    val id: Long,
    val title: String,
    val subtitle: String
)

data class AuthUser(
    val id: Long,
    val username: String,
    val fullName: String,
    val role: String
)

data class UserPermission(
    val module: String,
    val canView: Boolean,
    val canAdd: Boolean,
    val canEdit: Boolean,
    val canDelete: Boolean
)

data class DailyReportSummary(
    val date: String,
    val ordersCount: Int,
    val completedCount: Int,
    val openCount: Int,
    val inProgressCount: Int,
    val totalBilled: Double,
    val laborTotal: Double,
    val partsTotal: Double,
    val discountsTotal: Double,
    val collectedTotal: Double,
    val remainingDebtTotal: Double
)

data class CustomerDebtEntry(
    val customerId: Long,
    val customerName: String,
    val customerPhone: String,
    val debtAmount: Double
)

class WorkshopDb(private val appContext: Context) :
    SQLiteOpenHelper(appContext, "workshop.db", null, 6) {

    @Volatile
    private var activeUserId: Long? = null

    private val permissionModules = setOf(
        "customers",
        "vehicles",
        "work_orders",
        "parts",
        "users"
    )

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        createBusinessTables(db)
        createUsersTable(db)
        createPermissionsTable(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            createUsersTable(db)
        }
        if (oldVersion < 3) {
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_vehicle_plate ON vehicles(plate)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_owner_vehicle ON vehicle_owners(vehicle_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_work_vehicle_date ON work_orders(vehicle_id, created_at)")
        }
        if (oldVersion < 4) {
            createPermissionsTable(db)
            seedManagerPermissions(db)
        }
        if (oldVersion < 5) {
            addColumnIfMissing(db, "vehicles", "car_photo_path", "TEXT NOT NULL DEFAULT ''")
            addColumnIfMissing(db, "vehicles", "fuel_gauge_photo_path", "TEXT NOT NULL DEFAULT ''")
        }
        if (oldVersion < 6) {
            addColumnIfMissing(db, "customers", "email", "TEXT NOT NULL DEFAULT ''")
            addColumnIfMissing(db, "customers", "city", "TEXT NOT NULL DEFAULT ''")
            addColumnIfMissing(db, "customers", "customer_type", "TEXT NOT NULL DEFAULT 'فرد'")

            addColumnIfMissing(db, "vehicles", "color", "TEXT NOT NULL DEFAULT ''")
            addColumnIfMissing(db, "vehicles", "fuel_type", "TEXT NOT NULL DEFAULT 'بنزين'")
            addColumnIfMissing(db, "vehicles", "transmission", "TEXT NOT NULL DEFAULT 'أوتوماتيك'")

            addColumnIfMissing(db, "work_orders", "priority", "TEXT NOT NULL DEFAULT 'عادية'")
            addColumnIfMissing(db, "work_orders", "technician_name", "TEXT NOT NULL DEFAULT ''")
            addColumnIfMissing(db, "work_orders", "discount", "REAL NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "work_orders", "expected_delivery", "TEXT NOT NULL DEFAULT ''")
            addColumnIfMissing(db, "work_orders", "down_payment", "REAL NOT NULL DEFAULT 0")

            addColumnIfMissing(db, "parts", "supplier", "TEXT NOT NULL DEFAULT ''")
            addColumnIfMissing(db, "parts", "shelf_location", "TEXT NOT NULL DEFAULT ''")
            addColumnIfMissing(db, "parts", "barcode", "TEXT NOT NULL DEFAULT ''")

            createPaymentsAndPhotosTables(db)
        }
    }

    private fun addColumnIfMissing(
        db: SQLiteDatabase,
        table: String,
        column: String,
        definition: String
    ) {
        val exists = db.rawQuery("PRAGMA table_info($table)", null).use { cursor ->
            val nameIdx = cursor.getColumnIndex("name")
            var found = false
            while (cursor.moveToNext()) {
                if (nameIdx >= 0 && cursor.getString(nameIdx) == column) {
                    found = true
                    break
                }
            }
            found
        }
        if (!exists) {
            db.execSQL("ALTER TABLE $table ADD COLUMN $column$definition")
        }
    }

    private fun createPaymentsAndPhotosTables(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS work_order_payments (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                work_order_id INTEGER NOT NULL,
                amount REAL NOT NULL DEFAULT 0,
                method TEXT NOT NULL DEFAULT 'نقدي',
                note TEXT NOT NULL DEFAULT '',
                paid_at TEXT NOT NULL DEFAULT (datetime('now', 'localtime')),
                FOREIGN KEY(work_order_id) REFERENCES work_orders(id) ON DELETE CASCADE
            )"""
        )
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS vehicle_photos (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                vehicle_id INTEGER NOT NULL,
                label TEXT NOT NULL DEFAULT 'صورة سيارة',
                photo_path TEXT NOT NULL,
                created_at TEXT NOT NULL DEFAULT (datetime('now', 'localtime')),
                FOREIGN KEY(vehicle_id) REFERENCES vehicles(id) ON DELETE CASCADE
            )"""
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_payments_wo ON work_order_payments(work_order_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_vphotos_vehicle ON vehicle_photos(vehicle_id)")
    }

    private fun createBusinessTables(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE customers (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                phone TEXT NOT NULL UNIQUE,
                name TEXT NOT NULL DEFAULT '',
                alternate_phones TEXT NOT NULL DEFAULT '',
                email TEXT NOT NULL DEFAULT '',
                city TEXT NOT NULL DEFAULT '',
                customer_type TEXT NOT NULL DEFAULT 'فرد',
                address TEXT NOT NULL DEFAULT '',
                notes TEXT NOT NULL DEFAULT '',
                created_at TEXT NOT NULL DEFAULT (datetime('now', 'localtime'))
            )"""
        )

        db.execSQL(
            """CREATE TABLE vehicles (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                customer_id INTEGER NOT NULL,
                make TEXT NOT NULL DEFAULT '',
                model TEXT NOT NULL DEFAULT '',
                model_year INTEGER,
                engine TEXT NOT NULL DEFAULT '',
                color TEXT NOT NULL DEFAULT '',
                fuel_type TEXT NOT NULL DEFAULT 'بنزين',
                transmission TEXT NOT NULL DEFAULT 'أوتوماتيك',
                plate TEXT NOT NULL DEFAULT '',
                vin TEXT NOT NULL UNIQUE,
                current_odometer INTEGER NOT NULL DEFAULT 0,
                notes TEXT NOT NULL DEFAULT '',
                car_photo_path TEXT NOT NULL DEFAULT '',
                fuel_gauge_photo_path TEXT NOT NULL DEFAULT '',
                FOREIGN KEY(customer_id) REFERENCES customers(id)
            )"""
        )

        db.execSQL(
            """CREATE TABLE work_orders (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                customer_id INTEGER NOT NULL,
                vehicle_id INTEGER NOT NULL,
                odometer_at_visit INTEGER NOT NULL DEFAULT 0,
                complaint TEXT NOT NULL DEFAULT '',
                diagnosis TEXT NOT NULL DEFAULT '',
                repair_notes TEXT NOT NULL DEFAULT '',
                status TEXT NOT NULL DEFAULT 'مفتوح',
                priority TEXT NOT NULL DEFAULT 'عادية',
                technician_name TEXT NOT NULL DEFAULT '',
                expected_delivery TEXT NOT NULL DEFAULT '',
                labor_cost REAL NOT NULL DEFAULT 0,
                parts_cost REAL NOT NULL DEFAULT 0,
                discount REAL NOT NULL DEFAULT 0,
                down_payment REAL NOT NULL DEFAULT 0,
                created_at TEXT NOT NULL DEFAULT (datetime('now', 'localtime')),
                FOREIGN KEY(customer_id) REFERENCES customers(id),
                FOREIGN KEY(vehicle_id) REFERENCES vehicles(id)
            )"""
        )

        db.execSQL(
            """CREATE TABLE parts (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                part_number TEXT NOT NULL DEFAULT '',
                name TEXT NOT NULL,
                quantity REAL NOT NULL DEFAULT 0,
                unit TEXT NOT NULL DEFAULT 'قطعة',
                purchase_price REAL NOT NULL DEFAULT 0,
                sale_price REAL NOT NULL DEFAULT 0,
                min_quantity REAL NOT NULL DEFAULT 0,
                supplier TEXT NOT NULL DEFAULT '',
                shelf_location TEXT NOT NULL DEFAULT '',
                barcode TEXT NOT NULL DEFAULT ''
            )"""
        )

        db.execSQL(
            """CREATE TABLE vehicle_owners (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                vehicle_id INTEGER NOT NULL,
                old_customer_id INTEGER NOT NULL,
                new_customer_id INTEGER NOT NULL,
                reason TEXT NOT NULL,
                changed_at TEXT NOT NULL DEFAULT (datetime('now', 'localtime'))
            )"""
        )

        createPaymentsAndPhotosTables(db)

        db.execSQL("CREATE INDEX idx_vehicle_customer ON vehicles(customer_id)")
        db.execSQL("CREATE INDEX idx_work_vehicle ON work_orders(vehicle_id)")
        db.execSQL("CREATE INDEX idx_vehicle_plate ON vehicles(plate)")
        db.execSQL("CREATE INDEX idx_owner_vehicle ON vehicle_owners(vehicle_id)")
        db.execSQL("CREATE INDEX idx_work_vehicle_date ON work_orders(vehicle_id, created_at)")
    }

    private fun createUsersTable(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS app_users (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                username TEXT NOT NULL UNIQUE,
                full_name TEXT NOT NULL,
                password_hash TEXT NOT NULL,
                password_salt TEXT NOT NULL,
                role TEXT NOT NULL DEFAULT 'مدير',
                created_at TEXT NOT NULL DEFAULT (datetime('now', 'localtime'))
            )"""
        )
    }

    private fun createPermissionsTable(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS user_permissions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER NOT NULL,
                module TEXT NOT NULL,
                can_view INTEGER NOT NULL DEFAULT 0,
                can_add INTEGER NOT NULL DEFAULT 0,
                can_edit INTEGER NOT NULL DEFAULT 0,
                can_delete INTEGER NOT NULL DEFAULT 0,
                UNIQUE(user_id, module),
                FOREIGN KEY(user_id) REFERENCES app_users(id) ON DELETE CASCADE
            )"""
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_permissions_user ON user_permissions(user_id)")
    }

    private fun seedManagerPermissions(db: SQLiteDatabase) {
        db.rawQuery("SELECT id FROM app_users WHERE role=?", arrayOf("مدير")).use { cursor ->
            while (cursor.moveToNext()) {
                val userId = cursor.getLong(0)
                for (module in permissionModules) {
                    val values = ContentValues().apply {
                        put("user_id", userId)
                        put("module", module)
                        put("can_view", 1)
                        put("can_add", 1)
                        put("can_edit", 1)
                        put("can_delete", 1)
                    }
                    db.insertWithOnConflict("user_permissions", null, values, SQLiteDatabase.CONFLICT_IGNORE)
                }
            }
        }
    }

    // ---------- حسابات المستخدمين والصلاحيات ----------

    fun hasUsers(): Boolean {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM app_users", null).use {
            return it.moveToFirst() && it.getInt(0) > 0
        }
    }

    fun createAdmin(username: String, fullName: String, password: String): Boolean {
        val cleanUsername = username.trim().lowercase()
        if (cleanUsername.isBlank() || fullName.isBlank() || password.length < 6) return false

        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        val hash = hashPassword(password, salt)

        val db = writableDatabase
        var createdId = -1L
        return try {
            db.beginTransaction()
            if (hasUsersInDatabase(db)) {
                db.endTransaction()
                return false
            }
            val values = ContentValues().apply {
                put("username", cleanUsername)
                put("full_name", fullName.trim())
                put("password_hash", Base64.encodeToString(hash, Base64.NO_WRAP))
                put("password_salt", Base64.encodeToString(salt, Base64.NO_WRAP))
                put("role", "مدير")
            }
            createdId = db.insertOrThrow("app_users", null, values)
            createPermissionsTable(db)
            for (module in permissionModules) {
                db.insertOrThrow(
                    "user_permissions",
                    null,
                    ContentValues().apply {
                        put("user_id", createdId)
                        put("module", module)
                        put("can_view", 1)
                        put("can_add", 1)
                        put("can
