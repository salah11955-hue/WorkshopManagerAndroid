package com.khubara.workshop

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
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

data class WorkOrderPhotoItem(
    val id: Long,
    val workOrderId: Long,
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

data class WorkOrderPartItem(
    val id: Long,
    val workOrderId: Long,
    val partId: Long,
    val partName: String,
    val quantity: Double,
    val unitPrice: Double,
    val totalPrice: Double,
    val createdAt: String
)

data class WorkOrderTaskItem(
    val id: Long,
    val workOrderId: Long,
    val title: String,
    val status: String,
    val assignedTo: String,
    val notes: String,
    val dueAt: String,
    val completedAt: String,
    val createdAt: String
)

data class AppointmentRecord(
    val id: Long,
    val customerId: Long,
    val vehicleId: Long,
    val workOrderId: Long,
    val customerName: String,
    val vehicleName: String,
    val startsAt: String,
    val status: String,
    val purpose: String,
    val notes: String
)

data class BranchRecord(
    val id: Long,
    val name: String,
    val address: String,
    val phone: String,
    val isActive: Boolean
)

data class AuditRecord(
    val id: Long,
    val username: String,
    val action: String,
    val entityType: String,
    val entityId: Long,
    val details: String,
    val createdAt: String
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
    SQLiteOpenHelper(appContext, "workshop.db", null, 8) {

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
        createWorkOrderPhotosTable(db)
        createWorkshopExtensionTables(db)
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
        if (oldVersion < 7) {
            createWorkOrderPhotosTable(db)
        }
        if (oldVersion < 8) {
            createWorkshopExtensionTables(db)
        }
    }

private fun createWorkshopExtensionTables(db: SQLiteDatabase) {
    db.execSQL(
        """CREATE TABLE IF NOT EXISTS work_order_parts (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            work_order_id INTEGER NOT NULL,
            part_id INTEGER NOT NULL,
            quantity REAL NOT NULL DEFAULT 1,
            unit_price REAL NOT NULL DEFAULT 0,
            created_at TEXT NOT NULL DEFAULT (datetime('now', 'localtime')),
            FOREIGN KEY(work_order_id) REFERENCES work_orders(id) ON DELETE CASCADE,
            FOREIGN KEY(part_id) REFERENCES parts(id)
        )"""
    )
    db.execSQL(
        """CREATE TABLE IF NOT EXISTS work_order_tasks (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            work_order_id INTEGER NOT NULL,
            title TEXT NOT NULL,
            status TEXT NOT NULL DEFAULT 'قيد الانتظار',
            assigned_to TEXT NOT NULL DEFAULT '',
            notes TEXT NOT NULL DEFAULT '',
            due_at TEXT NOT NULL DEFAULT '',
            completed_at TEXT NOT NULL DEFAULT '',
            created_at TEXT NOT NULL DEFAULT (datetime('now', 'localtime')),
            FOREIGN KEY(work_order_id) REFERENCES work_orders(id) ON DELETE CASCADE
        )"""
    )
    db.execSQL(
        """CREATE TABLE IF NOT EXISTS appointments (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            customer_id INTEGER NOT NULL,
            vehicle_id INTEGER NOT NULL DEFAULT 0,
            work_order_id INTEGER NOT NULL DEFAULT 0,
            starts_at TEXT NOT NULL,
            status TEXT NOT NULL DEFAULT 'مجدول',
            purpose TEXT NOT NULL DEFAULT '',
            notes TEXT NOT NULL DEFAULT '',
            created_at TEXT NOT NULL DEFAULT (datetime('now', 'localtime')),
            FOREIGN KEY(customer_id) REFERENCES customers(id)
        )"""
    )
    db.execSQL(
        """CREATE TABLE IF NOT EXISTS branches (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL UNIQUE,
            address TEXT NOT NULL DEFAULT '',
            phone TEXT NOT NULL DEFAULT '',
            is_active INTEGER NOT NULL DEFAULT 1,
            created_at TEXT NOT NULL DEFAULT (datetime('now', 'localtime'))
        )"""
    )
    db.execSQL(
        """CREATE TABLE IF NOT EXISTS audit_log (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id INTEGER NOT NULL DEFAULT 0,
            username TEXT NOT NULL DEFAULT '',
            action TEXT NOT NULL,
            entity_type TEXT NOT NULL,
            entity_id INTEGER NOT NULL DEFAULT 0,
            details TEXT NOT NULL DEFAULT '',
            created_at TEXT NOT NULL DEFAULT (datetime('now', 'localtime'))
        )"""
    )
    db.execSQL(
        """CREATE TABLE IF NOT EXISTS work_order_documents (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            work_order_id INTEGER NOT NULL,
            document_type TEXT NOT NULL,
            title TEXT NOT NULL DEFAULT '',
            content TEXT NOT NULL DEFAULT '',
            signed_by TEXT NOT NULL DEFAULT '',
            signed_at TEXT NOT NULL DEFAULT '',
            created_at TEXT NOT NULL DEFAULT (datetime('now', 'localtime')),
            FOREIGN KEY(work_order_id) REFERENCES work_orders(id) ON DELETE CASCADE
        )"""
    )
    db.execSQL("CREATE INDEX IF NOT EXISTS idx_work_order_parts_order ON work_order_parts(work_order_id)")
    db.execSQL("CREATE INDEX IF NOT EXISTS idx_work_order_tasks_order ON work_order_tasks(work_order_id)")
    db.execSQL("CREATE INDEX IF NOT EXISTS idx_appointments_start ON appointments(starts_at)")
    db.execSQL("CREATE INDEX IF NOT EXISTS idx_audit_created ON audit_log(created_at)")
    db.execSQL("INSERT OR IGNORE INTO branches(name, address, phone) VALUES ('الفرع الرئيسي', '', '')")
}


private fun createWorkOrderPhotosTable(db: SQLiteDatabase) {
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS work_order_photos (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            work_order_id INTEGER NOT NULL,
            label TEXT NOT NULL DEFAULT 'صورة عند الاستلام',
            photo_path TEXT NOT NULL,
            created_at TEXT NOT NULL DEFAULT
                (datetime('now', 'localtime')),
            FOREIGN KEY(work_order_id)
                REFERENCES work_orders(id) ON DELETE CASCADE
        )
        """.trimIndent()
    )

    db.execSQL(
        """
        CREATE INDEX IF NOT EXISTS idx_work_order_photos_order
        ON work_order_photos(work_order_id)
        """.trimIndent()
    )
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
            db.execSQL("ALTER TABLE $table ADD COLUMN $column $definition")
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
        createWorkOrderPhotosTable(db)
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
                        put("can_edit", 1)
                        put("can_delete", 1)
                    }
                )
            }
            db.setTransactionSuccessful()
            true
        } catch (_: Exception) {
            false
        } finally {
            if (db.inTransaction()) db.endTransaction()
            if (createdId > 0) activeUserId = createdId
        }
    }

    private fun hasUsersInDatabase(db: SQLiteDatabase): Boolean {
        db.rawQuery("SELECT COUNT(*) FROM app_users", null).use {
            return it.moveToFirst() && it.getInt(0) > 0
        }
    }

    fun authenticate(username: String, password: String): Boolean {
        activeUserId = null
        readableDatabase.rawQuery(
            "SELECT id, password_hash, password_salt FROM app_users WHERE username=? LIMIT 1",
            arrayOf(username.trim().lowercase())
        ).use { cursor ->
            if (!cursor.moveToFirst()) return false
            return try {
                val userId = cursor.getLong(0)
                val savedHash = Base64.decode(cursor.getString(1), Base64.NO_WRAP)
                val salt = Base64.decode(cursor.getString(2), Base64.NO_WRAP)
                val enteredHash = hashPassword(password, salt)
                val valid = MessageDigest.isEqual(savedHash, enteredHash)
                if (valid) activeUserId = userId
                valid
            } catch (_: Exception) {
                activeUserId = null
                false
            }
        }
    }

    fun currentUser(): AuthUser? {
        val userId = activeUserId ?: return null
        readableDatabase.rawQuery(
            "SELECT id, username, full_name, role FROM app_users WHERE id=? LIMIT 1",
            arrayOf(userId.toString())
        ).use { cursor ->
            if (!cursor.moveToFirst()) {
                activeUserId = null
                return null
            }
            return AuthUser(
                id = cursor.getLong(0),
                username = cursor.getString(1),
                fullName = cursor.getString(2),
                role = cursor.getString(3)
            )
        }
    }

    fun clearActiveUser() {
        activeUserId = null
    }

    fun hasPermission(module: String, action: String): Boolean {
        if (module !in permissionModules) return false
        val column = when (action) {
            "view" -> "can_view"
            "add" -> "can_add"
            "edit" -> "can_edit"
            "delete" -> "can_delete"
            else -> return false
        }
        val user = currentUser() ?: return false
        if (user.role == "مدير") return true

        readableDatabase.rawQuery(
            "SELECT $column FROM user_permissions WHERE user_id=? AND module=? LIMIT 1",
            arrayOf(user.id.toString(), module)
        ).use { cursor ->
            return cursor.moveToFirst() && cursor.getInt(0) == 1
        }
    }

    fun getPermissions(userId: Long): List<UserPermission> {
        if (currentUser()?.role != "مدير" && !hasPermission("users", "view")) return emptyList()
        val result = mutableListOf<UserPermission>()
        readableDatabase.rawQuery(
            "SELECT module, can_view, can_add, can_edit, can_delete FROM user_permissions WHERE user_id=? ORDER BY module",
            arrayOf(userId.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += UserPermission(
                    module = cursor.getString(0),
                    canView = cursor.getInt(1) == 1,
                    canAdd = cursor.getInt(2) == 1,
                    canEdit = cursor.getInt(3) == 1,
                    canDelete = cursor.getInt(4) == 1
                )
            }
        }
        return result
    }

    fun setPermission(userId: Long, module: String, action: String, allowed: Boolean): Boolean {
        if (currentUser()?.role != "مدير" && !hasPermission("users", "edit")) return false
        if (module !in permissionModules) return false
        val column = when (action) {
            "view" -> "can_view"
            "add" -> "can_add"
            "edit" -> "can_edit"
            "delete" -> "can_delete"
            else -> return false
        }
        val targetRole = readableDatabase.rawQuery(
            "SELECT role FROM app_users WHERE id=? LIMIT 1",
            arrayOf(userId.toString())
        ).use { cursor ->
            if (!cursor.moveToFirst()) return false
            cursor.getString(0)
        }
        if (targetRole == "مدير") return false

        return writableDatabase.update(
            "user_permissions",
            ContentValues().apply { put(column, if (allowed) 1 else 0) },
            "user_id=? AND module=?",
            arrayOf(userId.toString(), module)
        ) > 0
    }

    fun listUsers(): List<AuthUser> {
        val actor = currentUser() ?: return emptyList()
        if (actor.role != "مدير" && !hasPermission("users", "view")) return emptyList()
        val result = mutableListOf<AuthUser>()
        readableDatabase.rawQuery("SELECT id, username, full_name, role FROM app_users ORDER BY id", null).use { cursor ->
            while (cursor.moveToNext()) {
                result += AuthUser(
                    id = cursor.getLong(0),
                    username = cursor.getString(1),
                    fullName = cursor.getString(2),
                    role = cursor.getString(3)
                )
            }
        }
        return result
    }

    fun createUser(username: String, fullName: String, password: String, role: String = "فني"): Boolean {
        val actor = currentUser() ?: return false
        if (actor.role != "مدير" && !hasPermission("users", "add")) return false
        val cleanUsername = username.trim().lowercase()
        if (cleanUsername.isBlank() || fullName.isBlank() || password.length < 6 || role.isBlank() || role == "مدير") {
            return false
        }

        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        val hash = hashPassword(password, salt)
        val db = writableDatabase
        return try {
            db.beginTransaction()
            val newUserId = db.insertOrThrow(
                "app_users",
                null,
                ContentValues().apply {
                    put("username", cleanUsername)
                    put("full_name", fullName.trim())
                    put("password_hash", Base64.encodeToString(hash, Base64.NO_WRAP))
                    put("password_salt", Base64.encodeToString(salt, Base64.NO_WRAP))
                    put("role", role.trim())
                }
            )
            for (module in permissionModules) {
                db.insertOrThrow(
                    "user_permissions",
                    null,
                    ContentValues().apply {
                        put("user_id", newUserId)
                        put("module", module)
                        put("can_view", 0)
                        put("can_add", 0)
                        put("can_edit", 0)
                        put("can_delete", 0)
                    }
                )
            }
            db.setTransactionSuccessful()
            true
        } catch (_: Exception) {
            false
        } finally {
            if (db.inTransaction()) db.endTransaction()
        }
    }

    fun updateUserProfile(userId: Long, newFullName: String): Boolean {
        val actor = currentUser() ?: return false
        val canEdit = actor.id == userId || actor.role == "مدير" || hasPermission("users", "edit")
        if (!canEdit) return false
        val cleanName = newFullName.trim()
        if (cleanName.isBlank()) return false
        return writableDatabase.update(
            "app_users",
            ContentValues().apply { put("full_name", cleanName) },
            "id=?",
            arrayOf(userId.toString())
        ) == 1
    }

    fun updateUserPassword(userId: Long, newPassword: String): Boolean {
        val actor = currentUser() ?: return false
        val canEdit = actor.id == userId || actor.role == "مدير" || hasPermission("users", "edit")
        if (!canEdit || newPassword.length < 6) return false
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        val hash = hashPassword(newPassword, salt)
        return writableDatabase.update(
            "app_users",
            ContentValues().apply {
                put("password_hash", Base64.encodeToString(hash, Base64.NO_WRAP))
                put("password_salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            },
            "id=?",
            arrayOf(userId.toString())
        ) == 1
    }

    fun deleteUser(userId: Long): Boolean {
        val actor = currentUser() ?: return false
        if (actor.role != "مدير" && !hasPermission("users", "delete")) return false
        if (actor.id == userId) return false
        val targetRole = readableDatabase.rawQuery(
            "SELECT role FROM app_users WHERE id=? LIMIT 1",
            arrayOf(userId.toString())
        ).use { cursor ->
            if (!cursor.moveToFirst()) return false
            cursor.getString(0)
        }
        if (targetRole == "مدير") return false
        return writableDatabase.delete("app_users", "id=?", arrayOf(userId.toString())) == 1
    }

    private fun hashPassword(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, 120000, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    // ---------- الإحصائيات والبيانات العامة ----------

    fun count(table: String): Int {
        require(table in setOf("customers", "vehicles", "work_orders", "parts"))
        readableDatabase.rawQuery("SELECT COUNT(*) FROM $table", null).use {
            return if (it.moveToFirst()) it.getInt(0) else 0
        }
    }

    fun workOrderCountByStatus(status: String): Int {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM work_orders WHERE status=?", arrayOf(status)).use {
            return if (it.moveToFirst()) it.getInt(0) else 0
        }
    }

    fun lowStockParts(): List<PartItem> {
        return getAllParts().filter { it.isLowStock }
    }

    fun customerOptions(): List<Pair<Long, String>> {
        val out = mutableListOf<Pair<Long, String>>()
        readableDatabase.rawQuery("SELECT id, name, phone FROM customers ORDER BY id DESC", null).use { c ->
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
        readableDatabase.rawQuery(sql, if (customerId == null) null else arrayOf(customerId.toString())).use { c ->
            while (c.moveToNext()) {
                out += Triple(c.getLong(0), c.getLong(1), "${c.getString(2)} ${c.getString(3)} (${c.getString(4)})")
            }
        }
        return out
    }

    // ---------- العملاء ----------

    fun addCustomer(
        name: String,
        phone: String,
        alt: String = "",
        address: String = "",
        notes: String = "",
        email: String = "",
        city: String = "",
        customerType: String = "فرد"
    ): Long {
        check(hasPermission("customers", "add")) { "ليس لديك صلاحية إضافة عميل" }
        val cleanName = name.trim()
        val cleanPhone = normalizePhone(phone)
        require(cleanName.isNotBlank()) { "يرجى إدخال اسم العميل" }
        require(cleanPhone.isNotBlank()) { "يرجى إدخال رقم هاتف صحيح" }

        val values = ContentValues().apply {
            put("name", cleanName)
            put("phone", cleanPhone)
            put("alternate_phones", alt.trim())
            put("email", email.trim())
            put("city", city.trim())
            put("customer_type", customerType.trim().ifBlank { "فرد" })
            put("address", address.trim())
            put("notes", notes.trim())
        }
        return writableDatabase.insertOrThrow("customers", null, values)
    }

    fun updateCustomerDetails(
        customerId: Long,
        name: String,
        phone: String,
        alt: String,
        address: String,
        notes: String,
        email: String = "",
        city: String = "",
        customerType: String = "فرد"
    ): Boolean {
        check(hasPermission("customers", "edit")) { "ليس لديك صلاحية تعديل بيانات العميل" }
        val cleanName = name.trim()
        val cleanPhone = normalizePhone(phone)
        require(cleanName.isNotBlank()) { "يرجى إدخال اسم العميل" }
        require(cleanPhone.isNotBlank()) { "يرجى إدخال رقم هاتف صحيح" }

        val values = ContentValues().apply {
            put("name", cleanName)
            put("phone", cleanPhone)
            put("alternate_phones", alt.trim())
            put("email", email.trim())
            put("city", city.trim())
            put("customer_type", customerType.trim().ifBlank { "فرد" })
            put("address", address.trim())
            put("notes", notes.trim())
        }
        return try {
            writableDatabase.update("customers", values, "id=?", arrayOf(customerId.toString())) == 1
        } catch (_: Exception) {
            false
        }
    }

    fun getCustomerDebt(customerId: Long): Double {
        return getAllWorkOrders().filter { it.customerId == customerId }.sumOf { it.remainingAmount }
    }

    fun getTopDebts(limit: Int = 20): List<CustomerDebtEntry> {
        val customers = getAllCustomers()
        return customers.mapNotNull { cust ->
            val debt = getCustomerDebt(cust.id)
            if (debt > 0.0) {
                CustomerDebtEntry(cust.id, cust.name, cust.phone, debt)
            } else null
        }.sortedByDescending { it.debtAmount }.take(limit)
    }

    fun getCustomerProfile(customerId: Long): CustomerProfile? {
        readableDatabase.rawQuery(
            """SELECT id, name, phone, alternate_phones, email, city, customer_type, address, notes
               FROM customers WHERE id=? LIMIT 1""",
            arrayOf(customerId.toString())
        ).use { c ->
            if (!c.moveToFirst()) return null
            return CustomerProfile(
                id = c.getLong(0),
                name = c.getString(1) ?: "",
                phone = c.getString(2) ?: "",
                alternatePhones = c.getString(3) ?: "",
                email = c.getString(4) ?: "",
                city = c.getString(5) ?: "",
                customerType = c.getString(6) ?: "فرد",
                address = c.getString(7) ?: "",
                notes = c.getString(8) ?: "",
                totalDebt = getCustomerDebt(customerId)
            )
        }
    }

    fun getAllCustomers(limit: Int = 100): List<CustomerProfile> {
        val out = mutableListOf<CustomerProfile>()
        readableDatabase.rawQuery(
            "SELECT id, name, phone, alternate_phones, email, city, customer_type, address, notes FROM customers ORDER BY id DESC LIMIT ?",
            arrayOf(limit.toString())
        ).use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                out += CustomerProfile(
                    id = id,
                    name = c.getString(1) ?: "",
                    phone = c.getString(2) ?: "",
                    alternatePhones = c.getString(3) ?: "",
                    email = c.getString(4) ?: "",
                    city = c.getString(5) ?: "",
                    customerType = c.getString(6) ?: "فرد",
                    address = c.getString(7) ?: "",
                    notes = c.getString(8) ?: "",
                    totalDebt = getCustomerDebt(id)
                )
            }
        }
        return out
    }

    // ---------- السيارات والصور والملكية ----------

    fun addVehicle(
        customerId: Long,
        make: String,
        model: String,
        year: String,
        engine: String,
        plate: String,
        vin: String,
        odometer: String,
        notes: String = "",
        color: String = "",
        fuelType: String = "بنزين",
        transmission: String = "أوتوماتيك"
    ): Long {
        check(hasPermission("vehicles", "add")) { "ليس لديك صلاحية إضافة سيارة" }
        val cleanPlate = plate.trim()
        val cleanVin = normalizeVin(vin)
        require(cleanVin.length == 17) { "رقم الهيكل يجب أن يتكون من 17 خانة بالضبط" }
        require(!cleanVin.any { it in "IOQ" }) { "رقم الهيكل لا يسمح بالأحرف I و O و Q" }
        require(customerExists(customerId)) { "العميل المحدد غير موجود" }

        val reading = odometer.toLongOrNull() ?: 0L
        require(reading >= 0L) { "قراءة العداد غير صحيحة" }

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
            val values = ContentValues().apply {
                put("customer_id", customerId)
                put("make", make.trim())
                put("model", model.trim())
                put("model_year", year.toIntOrNull())
                put("engine", engine.trim())
                put("color", color.trim())
                put("fuel_type", fuelType.trim().ifBlank { "بنزين" })
                put("transmission", transmission.trim().ifBlank { "أوتوماتيك" })
                put("plate", cleanPlate)
                put("vin", cleanVin)
                put("current_odometer", reading)
                put("notes", notes.trim())
            }
            val id = database.insertOrThrow("vehicles", null, values)
            database.setTransactionSuccessful()
            return id
        } finally {
            database.endTransaction()
        }
    }

fun addWorkOrderPhoto(
    workOrderId: Long,
    label: String,
    photoPath: String
): Boolean {
    check(
        hasPermission("work_orders", "add") ||
        hasPermission("work_orders", "edit")
    ) {
        "ليس لديك صلاحية إضافة صور لأمر الإصلاح"
    }

    if (photoPath.isBlank()) return false

    val exists = readableDatabase.rawQuery(
        "SELECT id FROM work_orders WHERE id=? LIMIT 1",
        arrayOf(workOrderId.toString())
    ).use { it.moveToFirst() }

    if (!exists) return false

    val values = ContentValues().apply {
        put("work_order_id", workOrderId)
        put("label", label.trim().ifBlank { "صورة عند الاستلام" })
        put("photo_path", photoPath.trim())
    }

    return writableDatabase.insert(
        "work_order_photos",
        null,
        values
    ) > 0
}

fun getWorkOrderPhotos(
    workOrderId: Long
): List<WorkOrderPhotoItem> {
    val result = mutableListOf<WorkOrderPhotoItem>()

    readableDatabase.rawQuery(
        """
        SELECT id, work_order_id, label, photo_path, created_at
        FROM work_order_photos
        WHERE work_order_id=?
        ORDER BY id DESC
        """.trimIndent(),
        arrayOf(workOrderId.toString())
    ).use { cursor ->
        while (cursor.moveToNext()) {
            result += WorkOrderPhotoItem(
                id = cursor.getLong(0),
                workOrderId = cursor.getLong(1),
                label = cursor.getString(2) ?: "",
                photoPath = cursor.getString(3) ?: "",
                createdAt = cursor.getString(4) ?: ""
            )
        }
    }

    return result
}

    fun addCustomerAndVehicle(
        name: String,
        phone: String,
        alt: String,
        address: String,
        make: String,
        model: String,
        year: String,
        engine: String,
        plate: String,
        vin: String,
        odometer: String,
        customerNotes: String = "",
        vehicleNotes: String = "",
        email: String = "",
        city: String = "",
        customerType: String = "فرد",
        color: String = "",
        fuelType: String = "بنزين",
        transmission: String = "أوتوماتيك",
        carPhotoPath: String = "",
        fuelGaugePhotoPath: String = ""
    ): Pair<Long, Long>? {
        if (!hasPermission("customers", "add") || !hasPermission("vehicles", "add")) return null
        val cleanName = name.trim()
        val cleanPhone = normalizePhone(phone)
        val cleanVin = normalizeVin(vin)
        val reading = odometer.toLongOrNull() ?: 0L
        if (cleanName.isBlank() || cleanPhone.isBlank() || cleanVin.length != 17 || cleanVin.any { it in "IOQ" } || reading < 0L) {
            return null
        }

        val db = writableDatabase
        var result: Pair<Long, Long>? = null
        try {
            db.beginTransaction()
            val customerId = db.insertOrThrow(
                "customers",
                null,
                ContentValues().apply {
                    put("name", cleanName)
                    put("phone", cleanPhone)
                    put("alternate_phones", alt.trim())
                    put("email", email.trim())
                    put("city", city.trim())
                    put("customer_type", customerType.trim().ifBlank { "فرد" })
                    put("address", address.trim())
                    put("notes", customerNotes.trim())
                }
            )
            val vehicleId = db.insertOrThrow(
                "vehicles",
                null,
                ContentValues().apply {
                    put("customer_id", customerId)
                    put("make", make.trim())
                    put("model", model.trim())
                    put("model_year", year.toIntOrNull())
                    put("engine", engine.trim())
                    put("color", color.trim())
                    put("fuel_type", fuelType.trim().ifBlank { "بنزين" })
                    put("transmission", transmission.trim().ifBlank { "أوتوماتيك" })
                    put("plate", plate.trim())
                    put("vin", cleanVin)
                    put("current_odometer", reading)
                    put("notes", vehicleNotes.trim())
                    put("car_photo_path", carPhotoPath.trim())
                    put("fuel_gauge_photo_path", fuelGaugePhotoPath.trim())
                }
            )
            if (carPhotoPath.isNotBlank()) {
                insertVehiclePhotoInternal(db, vehicleId, "صورة السيارة", carPhotoPath.trim())
            }
            if (fuelGaugePhotoPath.isNotBlank()) {
                insertVehiclePhotoInternal(db, vehicleId, "مؤشر الوقود", fuelGaugePhotoPath.trim())
            }
            db.setTransactionSuccessful()
            result = customerId to vehicleId
        } catch (_: Exception) {
            result = null
        } finally {
            if (db.inTransaction()) db.endTransaction()
        }
        return result
    }

    private fun insertVehiclePhotoInternal(db: SQLiteDatabase, vehicleId: Long, label: String, path: String): Long {
        return db.insert(
            "vehicle_photos",
            null,
            ContentValues().apply {
                put("vehicle_id", vehicleId)
                put("label", label.trim().ifBlank { "صورة سيارة" })
                put("photo_path", path.trim())
            }
        )
    }

    fun addVehiclePhoto(vehicleId: Long, label: String, photoPath: String): Boolean {
        check(hasPermission("vehicles", "edit") || hasPermission("vehicles", "add")) {
            "ليس لديك صلاحية إضافة صور للسيارة"
        }
        if (photoPath.isBlank()) return false
        val db = writableDatabase
        val id = insertVehiclePhotoInternal(db, vehicleId, label, photoPath)
        val values = ContentValues()
        if (label.contains("وقود")) {
            values.put("fuel_gauge_photo_path", photoPath.trim())
        } else {
            values.put("car_photo_path", photoPath.trim())
        }
        db.update("vehicles", values, "id=?", arrayOf(vehicleId.toString()))
        return id > 0
    }

    fun getVehiclePhotos(vehicleId: Long): List<VehiclePhotoItem> {
        val out = mutableListOf<VehiclePhotoItem>()
        readableDatabase.rawQuery(
            "SELECT id, vehicle_id, label, photo_path, created_at FROM vehicle_photos WHERE vehicle_id=? ORDER BY id DESC",
            arrayOf(vehicleId.toString())
        ).use { c ->
            while (c.moveToNext()) {
                out += VehiclePhotoItem(
                    id = c.getLong(0),
                    vehicleId = c.getLong(1),
                    label = c.getString(2) ?: "صورة سيارة",
                    photoPath = c.getString(3) ?: "",
                    createdAt = c.getString(4) ?: ""
                )
            }
        }
        return out
    }

    fun updateVehiclePhotos(
        vehicleId: Long,
        carPhotoPath: String? = null,
        fuelGaugePhotoPath: String? = null
    ): Boolean {
        check(hasPermission("vehicles", "edit") || hasPermission("vehicles", "add")) {
            "ليس لديك صلاحية تحديث صور السيارة"
        }
        val db = writableDatabase
        val values = ContentValues()
        if (carPhotoPath != null) {
            values.put("car_photo_path", carPhotoPath.trim())
            if (carPhotoPath.isNotBlank()) insertVehiclePhotoInternal(db, vehicleId, "صورة السيارة", carPhotoPath.trim())
        }
        if (fuelGaugePhotoPath != null) {
            values.put("fuel_gauge_photo_path", fuelGaugePhotoPath.trim())
            if (fuelGaugePhotoPath.isNotBlank()) insertVehiclePhotoInternal(db, vehicleId, "مؤشر الوقود", fuelGaugePhotoPath.trim())
        }
        if (values.size() == 0) return false
        return db.update("vehicles", values, "id=?", arrayOf(vehicleId.toString())) == 1
    }

    private fun customerExists(customerId: Long): Boolean {
        readableDatabase.rawQuery("SELECT id FROM customers WHERE id=? LIMIT 1", arrayOf(customerId.toString())).use {
            return it.moveToFirst()
        }
    }

    private fun mapVehicleRow(c: android.database.Cursor): VehicleProfile {
        val id = c.getLong(0)
        return VehicleProfile(
            id = id,
            customerId = c.getLong(1),
            make = c.getString(2) ?: "",
            model = c.getString(3) ?: "",
            year = if (c.isNull(4)) "" else c.getInt(4).toString(),
            engine = c.getString(5) ?: "",
            color = c.getString(6) ?: "",
            fuelType = c.getString(7) ?: "بنزين",
            transmission = c.getString(8) ?: "أوتوماتيك",
            plate = c.getString(9) ?: "",
            vin = c.getString(10) ?: "",
            odometer = c.getLong(11),
            notes = c.getString(12) ?: "",
            ownerName = c.getString(13) ?: "",
            ownerPhone = c.getString(14) ?: "",
            carPhotoPath = c.getString(15) ?: "",
            fuelGaugePhotoPath = c.getString(16) ?: "",
            photos = getVehiclePhotos(id)
        )
    }

    fun getVehicleProfile(vehicleId: Long): VehicleProfile? {
        val sql = """
            SELECT
                v.id, v.customer_id, v.make, v.model, v.model_year, v.engine,
                v.color, v.fuel_type, v.transmission, v.plate, v.vin,
                v.current_odometer, v.notes, c.name, c.phone,
                v.car_photo_path, v.fuel_gauge_photo_path
            FROM vehicles v
            LEFT JOIN customers c ON c.id = v.customer_id
            WHERE v.id=? LIMIT 1
        """.trimIndent()
        readableDatabase.rawQuery(sql, arrayOf(vehicleId.toString())).use { c ->
            if (!c.moveToFirst()) return null
            return mapVehicleRow(c)
        }
    }

    fun getCustomerVehicles(customerId: Long): List<VehicleProfile> {
        val out = mutableListOf<VehicleProfile>()
        val sql = """
            SELECT
                v.id, v.customer_id, v.make, v.model, v.model_year, v.engine,
                v.color, v.fuel_type, v.transmission, v.plate, v.vin,
                v.current_odometer, v.notes, c.name, c.phone,
                v.car_photo_path, v.fuel_gauge_photo_path
            FROM vehicles v
            LEFT JOIN customers c ON c.id = v.customer_id
            WHERE v.customer_id=? ORDER BY v.id DESC
        """.trimIndent()
        readableDatabase.rawQuery(sql, arrayOf(customerId.toString())).use { c ->
            while (c.moveToNext()) out += mapVehicleRow(c)
        }
        return out
    }

    fun searchVehicles(query: String): List<VehicleProfile> {
        val clean = query.trim()
        if (clean.isBlank()) return emptyList()
        val vinQuery = normalizeVin(clean)
        val sql = """
            SELECT
                v.id, v.customer_id, v.make, v.model, v.model_year, v.engine,
                v.color, v.fuel_type, v.transmission, v.plate, v.vin,
                v.current_odometer, v.notes, c.name, c.phone,
                v.car_photo_path, v.fuel_gauge_photo_path
            FROM vehicles v
            LEFT JOIN customers c ON c.id = v.customer_id
            WHERE v.vin LIKE ?
               OR v.plate LIKE ? COLLATE NOCASE
               OR v.make LIKE ? COLLATE NOCASE
               OR v.model LIKE ? COLLATE NOCASE
               OR c.name LIKE ? COLLATE NOCASE
            ORDER BY v.id DESC LIMIT 50
        """.trimIndent()
        val pattern = "%$clean%"
        val out = mutableListOf<VehicleProfile>()
        readableDatabase.rawQuery(sql, arrayOf("%$vinQuery%", pattern, pattern, pattern, pattern)).use { c ->
            while (c.moveToNext()) out += mapVehicleRow(c)
        }
        return out
    }

    fun getVehicleOwnerHistory(vehicleId: Long): List<OwnerHistoryRecord> {
        val out = mutableListOf<OwnerHistoryRecord>()
        val sql = """
            SELECT old_customer.name, new_customer.name, h.reason, h.changed_at
            FROM vehicle_owners h
            LEFT JOIN customers old_customer ON old_customer.id = h.old_customer_id
            LEFT JOIN customers new_customer ON new_customer.id = h.new_customer_id
            WHERE h.vehicle_id=? ORDER BY h.id DESC
        """.trimIndent()
        readableDatabase.rawQuery(sql, arrayOf(vehicleId.toString())).use { c ->
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

    fun transferVehicleOwnership(vehicleId: Long, newCustomerId: Long, reason: String): Boolean {
        if (!hasPermission("vehicles", "edit")) return false
        if (reason.trim().isBlank()) return false
        val database = writableDatabase
        var success = false
        try {
            database.beginTransaction()
            val oldCustomerId = database.rawQuery(
                "SELECT customer_id FROM vehicles WHERE id=? LIMIT 1",
                arrayOf(vehicleId.toString())
            ).use { c -> if (c.moveToFirst()) c.getLong(0) else null }

            if (oldCustomerId == null || oldCustomerId == newCustomerId) return false
            val exists = database.rawQuery(
                "SELECT id FROM customers WHERE id=? LIMIT 1",
                arrayOf(newCustomerId.toString())
            ).use { it.moveToFirst() }
            if (!exists) return false

            database.insertOrThrow(
                "vehicle_owners",
                null,
                ContentValues().apply {
                    put("vehicle_id", vehicleId)
                    put("old_customer_id", oldCustomerId)
                    put("new_customer_id", newCustomerId)
                    put("reason", reason.trim())
                }
            )
            val changed = database.update(
                "vehicles",
                ContentValues().apply { put("customer_id", newCustomerId) },
                "id=?",
                arrayOf(vehicleId.toString())
            )
            if (changed != 1) return false
            database.setTransactionSuccessful()
            success = true
        } catch (_: Exception) {
            success = false
        } finally {
            if (database.inTransaction()) database.endTransaction()
        }
        return success
    }

    // ---------- أوامر الشغل والدفعات والتقارير ----------

    fun addWorkOrder(
        customerId: Long,
        vehicleId: Long,
        odometer: String,
        complaint: String,
        diagnosis: String,
        repairNotes: String = "",
        status: String = "مفتوح",
        laborCost: String = "0",
        partsCost: String = "0",
        priority: String = "عادية",
        technicianName: String = "",
        discount: String = "0",
        expectedDelivery: String = "",
        downPayment: String = "0"
): Long {
        check(hasPermission("work_orders", "add")) { "ليس لديك صلاحية إضافة أمر إصلاح" }
        val reading = odometer.toLongOrNull() ?: 0L
        require(reading >= 0L) { "قراءة العداد غير صحيحة" }

        val labor = laborCost.trim().ifBlank { "0" }.toDoubleOrNull()
            ?: throw IllegalArgumentException("قيمة أجور العمل غير صحيحة")
        val parts = partsCost.trim().ifBlank { "0" }.toDoubleOrNull()
            ?: throw IllegalArgumentException("قيمة قطع الغيار غير صحيحة")
        val disc = discount.trim().ifBlank { "0" }.toDoubleOrNull()
            ?: throw IllegalArgumentException("قيمة الخصم غير صحيحة")
        val downPay = downPayment.trim().ifBlank { "0" }.toDoubleOrNull()
            ?: throw IllegalArgumentException("قيمة الدفعة المقدمة غير صحيحة")

        require(labor.isFinite() && labor >= 0.0) { "أجور العمل يجب أن تكون رقماً موجباً" }
        require(parts.isFinite() && parts >= 0.0) { "تكلفة القطع يجب أن تكون رقماً موجباً" }
        require(disc.isFinite() && disc >= 0.0) { "الخصم يجب أن يكون رقماً موجباً" }
        require(downPay.isFinite() && downPay >= 0.0) { "الدفعة المقدمة يجب أن تكون رقماً موجباً" }

        val cleanStatus = status.trim().ifBlank { "مفتوح" }
        require(cleanStatus in setOf("مفتوح", "قيد الإصلاح", "قيد العمل", "مكتمل")) {
            "حالة أمر الإصلاح غير صحيحة"
        }

        val database = writableDatabase
        database.beginTransaction()
        try {
            val vehicleCustomerId = database.rawQuery(
                "SELECT customer_id FROM vehicles WHERE id=? LIMIT 1",
                arrayOf(vehicleId.toString())
            ).use { cursor ->
                if (!cursor.moveToFirst()) throw IllegalArgumentException("السيارة المحددة غير موجودة")
                cursor.getLong(0)
            }
            require(customerExists(customerId)) { "العميل المحدد غير موجود" }
            require(vehicleCustomerId == customerId) { "السيارة غير مرتبطة بالعميل المحدد. تحقق من الملكية أولاً" }

            val woId = database.insertOrThrow(
                "work_orders",
                null,
                ContentValues().apply {
                    put("customer_id", customerId)
                    put("vehicle_id", vehicleId)
                    put("odometer_at_visit", reading)
                    put("complaint", complaint.trim())
                    put("diagnosis", diagnosis.trim())
                    put("repair_notes", repairNotes.trim())
                    put("status", cleanStatus)
                    put("priority", priority.trim().ifBlank { "عادية" })
                    put("technician_name", technicianName.trim())
                    put("expected_delivery", expectedDelivery.trim())
                    put("labor_cost", labor)
                    put("parts_cost", parts)
                    put("discount", disc)
                    put("down_payment", downPay)
                }
            )
            if (downPay > 0.0) {
                database.insert(
                    "work_order_payments",
                    null,
                    ContentValues().apply {
                        put("work_order_id", woId)
                        put("amount", downPay)
                        put("method", "نقدي")
                        put("note", "دفعة مقدمة عند الاستقبال")
                    }
                )
            }
            database.execSQL(
                "UPDATE vehicles SET current_odometer=MAX(current_odometer, ?) WHERE id=?",
                arrayOf(reading, vehicleId)
            )
            database.setTransactionSuccessful()
            return woId
        } finally {
            database.endTransaction()
        }
    }

    fun updateWorkOrder(
        workOrderId: Long,
        status: String,
        diagnosis: String,
        repairNotes: String,
        laborCost: String,
        partsCost: String,
        priority: String = "عادية",
        technicianName: String = "",
        discount: String = "0",
        expectedDelivery: String = ""
    ): Boolean {
        check(hasPermission("work_orders", "edit")) { "ليس لديك صلاحية تعديل أمر الإصلاح" }
        val cleanStatus = status.trim()
        require(cleanStatus in setOf("مفتوح", "قيد الإصلاح", "قيد العمل", "مكتمل")) {
            "حالة أمر الإصلاح غير صحيحة"
        }
        val labor = laborCost.trim().ifBlank { "0" }.toDoubleOrNull()
            ?: throw IllegalArgumentException("قيمة أجور العمل غير صحيحة")
        val parts = partsCost.trim().ifBlank { "0" }.toDoubleOrNull()
            ?: throw IllegalArgumentException("قيمة قطع الغيار غير صحيحة")
        val disc = discount.trim().ifBlank { "0" }.toDoubleOrNull()
            ?: throw IllegalArgumentException("قيمة الخصم غير صحيحة")

        require(labor.isFinite() && labor >= 0.0 && parts.isFinite() && parts >= 0.0 && disc.isFinite() && disc >= 0.0) {
            "القيم المالية يجب أن تكون أرقاماً موجبة"
        }

        return writableDatabase.update(
            "work_orders",
            ContentValues().apply {
                put("status", cleanStatus)
                put("diagnosis", diagnosis.trim())
                put("repair_notes", repairNotes.trim())
                put("labor_cost", labor)
                put("parts_cost", parts)
                put("priority", priority.trim().ifBlank { "عادية" })
                put("technician_name", technicianName.trim())
                put("discount", disc)
                put("expected_delivery", expectedDelivery.trim())
            },
            "id=?",
            arrayOf(workOrderId.toString())
        ) == 1
    }

    fun addWorkOrderPayment(workOrderId: Long, amount: Double, method: String, note: String = ""): Boolean {
        check(hasPermission("work_orders", "edit") || hasPermission("work_orders", "add")) {
            "ليس لديك صلاحية تسجيل دفعة"
        }
        require(amount.isFinite() && amount > 0.0) { "قيمة الدفعة يجب أن تكون أكبر من صفر" }
        val cleanMethod = method.trim().ifBlank { "نقدي" }
        return writableDatabase.insert(
            "work_order_payments",
            null,
            ContentValues().apply {
                put("work_order_id", workOrderId)
                put("amount", amount)
                put("method", cleanMethod)
                put("note", note.trim())
            }
        ) > 0
    }

    fun getWorkOrderPayments(workOrderId: Long): List<WorkOrderPaymentRecord> {
        val out = mutableListOf<WorkOrderPaymentRecord>()
        readableDatabase.rawQuery(
            "SELECT id, work_order_id, amount, method, note, paid_at FROM work_order_payments WHERE work_order_id=? ORDER BY id DESC",
            arrayOf(workOrderId.toString())
        ).use { c ->
            while (c.moveToNext()) {
                out += WorkOrderPaymentRecord(
                    id = c.getLong(0),
                    workOrderId = c.getLong(1),
                    amount = c.getDouble(2),
                    method = c.getString(3) ?: "نقدي",
                    note = c.getString(4) ?: "",
                    paidAt = c.getString(5) ?: ""
                )
            }
        }
        return out
    }

    private fun extraPaymentsSum(workOrderId: Long): Double {
        readableDatabase.rawQuery(
            "SELECT COALESCE(SUM(amount), 0) FROM work_order_payments WHERE work_order_id=?",
            arrayOf(workOrderId.toString())
        ).use { c ->
            return if (c.moveToFirst()) c.getDouble(0) else 0.0
        }
    }

    fun getAllWorkOrders(statusFilter: String? = null, limit: Int = 100): List<WorkOrderRecord> {
        val baseSql = """
            SELECT
                w.id, w.customer_id, COALESCE(c.name, 'غير معروف'), COALESCE(c.phone, ''),
                w.vehicle_id, COALESCE(v.make || ' ' || v.model, 'سيارة'), COALESCE(v.plate, ''),
                w.odometer_at_visit, w.complaint, w.diagnosis, w.repair_notes,
                w.status, w.priority, w.technician_name, w.expected_delivery,
                w.labor_cost, w.parts_cost, w.discount, w.down_payment, w.created_at
            FROM work_orders w
            LEFT JOIN customers c ON c.id = w.customer_id
            LEFT JOIN vehicles v ON v.id = w.vehicle_id
        """.trimIndent()

        val (sql, args) = if (!statusFilter.isNullOrBlank() && statusFilter != "الكل") {
            if (statusFilter == "قيد الإصلاح") {
                "$baseSql WHERE w.status IN ('قيد الإصلاح', 'قيد العمل') ORDER BY w.id DESC LIMIT ?" to arrayOf(limit.toString())
            } else {
                "$baseSql WHERE w.status=? ORDER BY w.id DESC LIMIT ?" to arrayOf(statusFilter, limit.toString())
            }
        } else {
            "$baseSql ORDER BY w.id DESC LIMIT ?" to arrayOf(limit.toString())
        }

        val out = mutableListOf<WorkOrderRecord>()
        readableDatabase.rawQuery(sql, args).use { c ->
            while (c.moveToNext()) {
                val woId = c.getLong(0)
                val downPay = c.getDouble(18)
                val paymentsTotal = extraPaymentsSum(woId)
                val effectiveAdditional = if (paymentsTotal > 0.0) (paymentsTotal - downPay).coerceAtLeast(0.0) else 0.0
                out += WorkOrderRecord(
                    id = woId,
                    customerId = c.getLong(1),
                    customerName = c.getString(2) ?: "غير معروف",
                    customerPhone = c.getString(3) ?: "",
                    vehicleId = c.getLong(4),
                    vehicleName = c.getString(5) ?: "",
                    vehiclePlate = c.getString(6) ?: "",
                    odometer = c.getLong(7),
                    complaint = c.getString(8) ?: "",
                    diagnosis = c.getString(9) ?: "",
                    repairNotes = c.getString(10) ?: "",
                    status = c.getString(11) ?: "مفتوح",
                    priority = c.getString(12) ?: "عادية",
                    technicianName = c.getString(13) ?: "",
                    expectedDelivery = c.getString(14) ?: "",
                    laborCost = c.getDouble(15),
                    partsCost = c.getDouble(16),
                    discount = c.getDouble(17),
                    downPayment = downPay,
                    additionalPaid = effectiveAdditional,
                    createdAt = c.getString(19) ?: ""
                )
            }
        }
        return out
    }

    fun getDailyReport(dateYyyyMmDd: String): DailyReportSummary {
        val cleanDate = dateYyyyMmDd.trim()
        val orders = getAllWorkOrders(limit = 500).filter {
            it.createdAt.startsWith(cleanDate)
        }
        val completed = orders.count { it.status == "مكتمل" }
        val open = orders.count { it.status == "مفتوح" }
        val inProg = orders.count { it.status == "قيد الإصلاح" || it.status == "قيد العمل" }
        val laborSum = orders.sumOf { it.laborCost }
        val partsSum = orders.sumOf { it.partsCost }
        val discSum = orders.sumOf { it.discount }
        val billedSum = orders.sumOf { it.totalCost }

        var collectedSum = 0.0
        readableDatabase.rawQuery(
            "SELECT COALESCE(SUM(amount), 0) FROM work_order_payments WHERE substr(paid_at, 1, 10)=?",
            arrayOf(cleanDate)
        ).use { c ->
            if (c.moveToFirst()) collectedSum = c.getDouble(0)
        }
        if (collectedSum == 0.0 && orders.isNotEmpty()) {
            collectedSum = orders.sumOf { it.paidAmount }
        }

        val remainingDebt = getAllWorkOrders(limit = 1000).sumOf { it.remainingAmount }

        return DailyReportSummary(
            date = cleanDate,
            ordersCount = orders.size,
            completedCount = completed,
            openCount = open,
            inProgressCount = inProg,
            totalBilled = billedSum,
            laborTotal = laborSum,
            partsTotal = partsSum,
            discountsTotal = discSum,
            collectedTotal = collectedSum,
            remainingDebtTotal = remainingDebt
        )
    }

    fun addUsedPart(workOrderId: Long, partId: Long, quantity: Double): Boolean {
        check(hasPermission("work_orders", "edit")) { "ليس لديك صلاحية استخدام قطع الغيار" }
        require(quantity.isFinite() && quantity > 0.0) { "كمية القطعة غير صحيحة" }
        val database = writableDatabase
        database.beginTransaction()
        try {
            val part = database.rawQuery(
                "SELECT name, quantity, sale_price FROM parts WHERE id=? LIMIT 1",
                arrayOf(partId.toString())
            ).use { cursor ->
                if (!cursor.moveToFirst()) null
                else Triple(cursor.getString(0) ?: "", cursor.getDouble(1), cursor.getDouble(2))
            } ?: throw IllegalArgumentException("القطعة غير موجودة")
            require(part.second >= quantity) { "الكمية المتاحة في المخزون أقل من الكمية المطلوبة" }

            database.insert(
                "work_order_parts",
                null,
                ContentValues().apply {
                    put("work_order_id", workOrderId)
                    put("part_id", partId)
                    put("quantity", quantity)
                    put("unit_price", part.third)
                }
            )
            val newQuantity = part.second - quantity
            database.update("parts", ContentValues().apply { put("quantity", newQuantity) }, "id=?", arrayOf(partId.toString()))
            val addedCost = quantity * part.third
            val currentPartsCost = database.rawQuery(
                "SELECT parts_cost FROM work_orders WHERE id=?",
                arrayOf(workOrderId.toString())
            ).use { cursor -> if (cursor.moveToFirst()) cursor.getDouble(0) else 0.0 }
            database.update(
                "work_orders",
                ContentValues().apply { put("parts_cost", currentPartsCost + addedCost) },
                "id=?",
                arrayOf(workOrderId.toString())
            )
            logAudit("إضافة قطعة لأمر إصلاح", "work_order", workOrderId, "القطعة=${part.first}, الكمية=$quantity")
            database.setTransactionSuccessful()
            return true
        } finally {
            database.endTransaction()
        }
    }

    fun getUsedParts(workOrderId: Long): List<WorkOrderPartItem> {
        val result = mutableListOf<WorkOrderPartItem>()
        readableDatabase.rawQuery(
            """SELECT wp.id, wp.work_order_id, wp.part_id, p.name, wp.quantity, wp.unit_price,
                      wp.quantity * wp.unit_price, wp.created_at
               FROM work_order_parts wp
               JOIN parts p ON p.id = wp.part_id
               WHERE wp.work_order_id=? ORDER BY wp.id DESC""",
            arrayOf(workOrderId.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += WorkOrderPartItem(
                    cursor.getLong(0), cursor.getLong(1), cursor.getLong(2),
                    cursor.getString(3) ?: "", cursor.getDouble(4), cursor.getDouble(5),
                    cursor.getDouble(6), cursor.getString(7) ?: ""
                )
            }
        }
        return result
    }

    fun addWorkOrderTask(workOrderId: Long, title: String, assignedTo: String, dueAt: String, notes: String = ""): Boolean {
        check(hasPermission("work_orders", "edit")) { "ليس لديك صلاحية إدارة مهام الإصلاح" }
        if (title.trim().isBlank()) return false
        val inserted = writableDatabase.insert(
            "work_order_tasks",
            null,
            ContentValues().apply {
                put("work_order_id", workOrderId)
                put("title", title.trim())
                put("assigned_to", assignedTo.trim())
                put("due_at", dueAt.trim())
                put("notes", notes.trim())
            }
        )
        if (inserted > 0) logAudit("إضافة مهمة إصلاح", "work_order", workOrderId, title.trim())
        return inserted > 0
    }

    fun getWorkOrderTasks(workOrderId: Long): List<WorkOrderTaskItem> {
        val result = mutableListOf<WorkOrderTaskItem>()
        readableDatabase.rawQuery(
            """SELECT id, work_order_id, title, status, assigned_to, notes, due_at, completed_at, created_at
               FROM work_order_tasks WHERE work_order_id=? ORDER BY id""",
            arrayOf(workOrderId.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += WorkOrderTaskItem(
                    cursor.getLong(0), cursor.getLong(1), cursor.getString(2) ?: "",
                    cursor.getString(3) ?: "قيد الانتظار", cursor.getString(4) ?: "",
                    cursor.getString(5) ?: "", cursor.getString(6) ?: "",
                    cursor.getString(7) ?: "", cursor.getString(8) ?: ""
                )
            }
        }
        return result
    }

    fun updateWorkOrderTask(taskId: Long, status: String, notes: String = ""): Boolean {
        check(hasPermission("work_orders", "edit")) { "ليس لديك صلاحية تعديل مهام الإصلاح" }
        val completedAt = if (status == "مكتملة") todayStringWithTime() else ""
        val rows = writableDatabase.update(
            "work_order_tasks",
            ContentValues().apply {
                put("status", status)
                put("notes", notes.trim())
                put("completed_at", completedAt)
            },
            "id=?",
            arrayOf(taskId.toString())
        )
        return rows == 1
    }

    fun addAppointment(customerId: Long, vehicleId: Long, workOrderId: Long, startsAt: String, purpose: String, notes: String): Boolean {
        if (startsAt.trim().isBlank()) return false
        val inserted = writableDatabase.insert(
            "appointments",
            null,
            ContentValues().apply {
                put("customer_id", customerId)
                put("vehicle_id", vehicleId)
                put("work_order_id", workOrderId)
                put("starts_at", startsAt.trim())
                put("purpose", purpose.trim())
                put("notes", notes.trim())
            }
        )
        if (inserted > 0) logAudit("إضافة موعد", "appointment", inserted, purpose.trim())
        return inserted > 0
    }

    fun getAppointments(limit: Int = 100): List<AppointmentRecord> {
        val result = mutableListOf<AppointmentRecord>()
        readableDatabase.rawQuery(
            """SELECT a.id, a.customer_id, a.vehicle_id, a.work_order_id,
                      COALESCE(c.name, ''), COALESCE(v.make || ' ' || v.model, ''),
                      a.starts_at, a.status, a.purpose, a.notes
               FROM appointments a
               LEFT JOIN customers c ON c.id=a.customer_id
               LEFT JOIN vehicles v ON v.id=a.vehicle_id
               ORDER BY a.starts_at DESC LIMIT ?""",
            arrayOf(limit.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += AppointmentRecord(
                    cursor.getLong(0), cursor.getLong(1), cursor.getLong(2), cursor.getLong(3),
                    cursor.getString(4) ?: "", cursor.getString(5) ?: "", cursor.getString(6) ?: "",
                    cursor.getString(7) ?: "", cursor.getString(8) ?: "", cursor.getString(9) ?: ""
                )
            }
        }
        return result
    }

    fun updateAppointmentStatus(appointmentId: Long, status: String): Boolean =
        writableDatabase.update(
            "appointments",
            ContentValues().apply { put("status", status) },
            "id=?",
            arrayOf(appointmentId.toString())
        ) == 1

    fun signWorkOrderDocument(workOrderId: Long, documentType: String, title: String, signedBy: String, content: String): Boolean {
        val inserted = writableDatabase.insert(
            "work_order_documents",
            null,
            ContentValues().apply {
                put("work_order_id", workOrderId)
                put("document_type", documentType)
                put("title", title)
                put("content", content)
                put("signed_by", signedBy.trim())
                put("signed_at", todayStringWithTime())
            }
        )
        if (inserted > 0) logAudit("توقيع مستند", "work_order", workOrderId, title)
        return inserted > 0
    }

    fun logAudit(action: String, entityType: String, entityId: Long, details: String = "") {
        val user = currentUserSnapshot()
        writableDatabase.insert(
            "audit_log",
            null,
            ContentValues().apply {
                put("user_id", user?.id ?: 0)
                put("username", user?.username ?: "")
                put("action", action)
                put("entity_type", entityType)
                put("entity_id", entityId)
                put("details", details)
            }
        )
    }

    fun getBranches(): List<BranchRecord> {
        val result = mutableListOf<BranchRecord>()
        readableDatabase.rawQuery(
            "SELECT id, name, address, phone, is_active FROM branches ORDER BY id",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += BranchRecord(
                    cursor.getLong(0), cursor.getString(1) ?: "", cursor.getString(2) ?: "",
                    cursor.getString(3) ?: "", cursor.getInt(4) == 1
                )
            }
        }
        return result
    }

    fun addBranch(name: String, address: String, phone: String): Boolean {
        check(hasPermission("users", "add") || hasPermission("users", "edit")) { "ليس لديك صلاحية إدارة الفروع" }
        if (name.trim().isBlank()) return false
        val inserted = writableDatabase.insert(
            "branches",
            null,
            ContentValues().apply {
                put("name", name.trim())
                put("address", address.trim())
                put("phone", phone.trim())
            }
        )
        if (inserted > 0) logAudit("إضافة فرع", "branch", inserted, name.trim())
        return inserted > 0
    }

    fun getAuditRecords(limit: Int = 100): List<AuditRecord> {
        val result = mutableListOf<AuditRecord>()
        readableDatabase.rawQuery(
            """SELECT id, username, action, entity_type, entity_id, details, created_at
               FROM audit_log ORDER BY id DESC LIMIT ?""",
            arrayOf(limit.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += AuditRecord(
                    cursor.getLong(0), cursor.getString(1) ?: "", cursor.getString(2) ?: "",
                    cursor.getString(3) ?: "", cursor.getLong(4), cursor.getString(5) ?: "",
                    cursor.getString(6) ?: ""
                )
            }
        }
        return result
    }

    private fun currentUserSnapshot(): AuthUser? = currentUser()

    private fun todayStringWithTime(): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale("ar"))
        return fmt.format(Date())
    }

    // ---------- قطع الغيار والمخزون ----------

    fun addPart(
        number: String,
        name: String,
        qty: String,
        unit: String,
        buy: String,
        sell: String,
        minQty: String = "0",
        supplier: String = "",
        shelfLocation: String = "",
        barcode: String = ""
    ) {
        check(hasPermission("parts", "add")) { "ليس لديك صلاحية إضافة قطعة غيار" }
        val quantity = qty.toDoubleOrNull() ?: 0.0
        val purchasePrice = buy.toDoubleOrNull() ?: 0.0
        val salePrice = sell.toDoubleOrNull() ?: 0.0
        val minQuantity = minQty.toDoubleOrNull() ?: 0.0

        require(name.trim().isNotBlank()) { "يرجى إدخال اسم القطعة" }
        require(
            quantity.isFinite() && purchasePrice.isFinite() && salePrice.isFinite() && minQuantity.isFinite() &&
                quantity >= 0.0 && purchasePrice >= 0.0 && salePrice >= 0.0 && minQuantity >= 0.0
        ) { "تحقق من الكميات والأسعار" }

        writableDatabase.insertOrThrow(
            "parts",
            null,
            ContentValues().apply {
                put("part_number", number.trim())
                put("name", name.trim())
                put("quantity", quantity)
                put("unit", unit.trim().ifBlank { "قطعة" })
                put("purchase_price", purchasePrice)
                put("sale_price", salePrice)
                put("min_quantity", minQuantity)
                put("supplier", supplier.trim())
                put("shelf_location", shelfLocation.trim())
                put("barcode", barcode.trim())
            }
        )
    }

    fun adjustPartQuantity(partId: Long, deltaQty: Double): Boolean {
        check(hasPermission("parts", "edit")) { "ليس لديك صلاحية تعديل كميات المخزون" }
        require(deltaQty.isFinite() && deltaQty != 0.0) { "أدخل كمية صحيحة" }

        val db = writableDatabase
        db.beginTransaction()
        try {
            val currentQty = db.rawQuery("SELECT quantity FROM parts WHERE id=? LIMIT 1", arrayOf(partId.toString())).use { c ->
                if (!c.moveToFirst()) return false
                c.getDouble(0)
            }
            val updatedQty = (currentQty + deltaQty).coerceAtLeast(0.0)
            val rows = db.update(
                "parts",
                ContentValues().apply { put("quantity", updatedQty) },
                "id=?",
                arrayOf(partId.toString())
            )
            db.setTransactionSuccessful()
            return rows == 1
        } finally {
            db.endTransaction()
        }
    }

    fun getAllParts(limit: Int = 200): List<PartItem> {
        val out = mutableListOf<PartItem>()
        readableDatabase.rawQuery(
            """SELECT id, part_number, name, quantity, unit, purchase_price, sale_price,
                      min_quantity, supplier, shelf_location, barcode
               FROM parts ORDER BY id DESC LIMIT ?""",
            arrayOf(limit.toString())
        ).use { c ->
            while (c.moveToNext()) {
                out += PartItem(
                    id = c.getLong(0),
                    partNumber = c.getString(1) ?: "",
                    name = c.getString(2) ?: "",
                    quantity = c.getDouble(3),
                    unit = c.getString(4) ?: "قطعة",
                    purchasePrice = c.getDouble(5),
                    salePrice = c.getDouble(6),
                    minQuantity = c.getDouble(7),
                    supplier = c.getString(8) ?: "",
                    shelfLocation = c.getString(9) ?: "",
                    barcode = c.getString(10) ?: ""
                )
            }
        }
        return out
    }

    // ---------- البحث والاستقبال والاقتراحات ----------

    fun searchReception(query: String): List<ReceptionSearchResult> {
        val clean = query.trim()
        if (clean.isBlank()) return emptyList()
        val results = mutableListOf<ReceptionSearchResult>()
        val phoneQuery = normalizePhone(clean)
        if (phoneQuery.isNotBlank()) {
            readableDatabase.rawQuery(
                "SELECT id, name, phone FROM customers WHERE phone LIKE ? ORDER BY id DESC LIMIT 20",
                arrayOf("%$phoneQuery%")
            ).use { c ->
                while (c.moveToNext()) {
                    results.add(
                        ReceptionSearchResult(
                            type = "customer",
                            id = c.getLong(0),
                            title = c.getString(1) ?: "",
                            subtitle = "عميل — هاتف: ${c.getString(2) ?: ""}"
                        )
                    )
                }
            }
        }
        val vinQuery = normalizeVin(clean)
        readableDatabase.rawQuery(
            "SELECT id, make, model, vin, plate FROM vehicles WHERE vin LIKE ? OR plate LIKE ? COLLATE NOCASE ORDER BY id DESC LIMIT 30",
            arrayOf("%$vinQuery%", "%$clean%")
        ).use { c ->
            while (c.moveToNext()) {
                results.add(
                    ReceptionSearchResult(
                        type = "vehicle",
                        id = c.getLong(0),
                        title = "${c.getString(1) ?: ""} ${c.getString(2) ?: ""}".trim(),
                        subtitle = "سيارة — اللوحة: ${c.getString(4) ?: ""} — VIN: ${c.getString(3) ?: ""}"
                    )
                )
            }
        }
        return results
    }

    fun suggestions(key: String, prefix: String): List<String> {
        val cleanPrefix = when (key) {
            "phone", "customer_phone" -> normalizePhone(prefix)
            "vin" -> normalizeVin(prefix)
            else -> prefix.trim()
        }
        if (cleanPrefix.isBlank()) return emptyList()

        val (column, table) = when (key) {
            "customer", "customer_name", "name" -> "customers.name" to "customers"
            "phone", "customer_phone" -> "customers.phone" to "customers"
            "plate" -> "vehicles.plate" to "vehicles"
            "vin" -> "vehicles.vin" to "vehicles"
            "part_number" -> "parts.part_number" to "parts"
            "part_name" -> "parts.name" to "parts"
            "make", "manufacturer" -> "vehicles.make" to "vehicles"
            "model" -> "vehicles.model" to "vehicles"
            "engine" -> "vehicles.engine" to "vehicles"
            "color" -> "vehicles.color" to "vehicles"
            "technician_name", "technician" -> "work_orders.technician_name" to "work_orders"
            "complaint" -> "work_orders.complaint" to "work_orders"
            "diagnosis" -> "work_orders.diagnosis" to "work_orders"
            "repair_notes" -> "work_orders.repair_notes" to "work_orders"
            "supplier" -> "parts.supplier" to "parts"
            "barcode" -> "parts.barcode" to "parts"
            else -> return emptyList()
        }

        val results = mutableListOf<String>()
        val safePrefix = cleanPrefix
            .replace("%", "")
            .replace("_", "")
        if (safePrefix.isBlank()) return emptyList()
        val sql = """
            SELECT DISTINCT $column FROM $table
            WHERE $column LIKE ? COLLATE NOCASE
              AND TRIM($column) != ''
            ORDER BY LENGTH($column), $column
            LIMIT 20
        """.trimIndent()

        try {
            readableDatabase.rawQuery(sql, arrayOf("$safePrefix%")).use { cursor ->
                while (cursor.moveToNext()) {
                    val value = cursor.getString(0)?.trim()
                    if (!value.isNullOrBlank()) results.add(value)
                }
            }
        } catch (_: SQLiteException) {
            return emptyList()
        }
        return results
    }

    // ---------- النسخ الاحتياطي ----------

    fun exportBackupFile(): File {
        val rootObj = JSONObject()
        rootObj.put("exported_at", todayString())
        rootObj.put("app", "خبراء هونداي وفورد")

        val customersArr = JSONArray()
        getAllCustomers(limit = 2000).forEach { c ->
            customersArr.put(
                JSONObject().apply {
                    put("id", c.id)
                    put("name", c.name)
                    put("phone", c.phone)
                    put("alternate_phones", c.alternatePhones)
                    put("email", c.email)
                    put("city", c.city)
                    put("customer_type", c.customerType)
                    put("address", c.address)
                    put("notes", c.notes)
                    put("total_debt", c.totalDebt)
                }
            )
        }
        rootObj.put("customers", customersArr)

        val vehiclesArr = JSONArray()
        readableDatabase.rawQuery(
            """SELECT v.id, v.customer_id, v.make, v.model, v.model_year, v.engine,
                      v.color, v.fuel_type, v.transmission, v.plate, v.vin,
                      v.current_odometer, v.notes, c.name, c.phone,
                      v.car_photo_path, v.fuel_gauge_photo_path
               FROM vehicles v LEFT JOIN customers c ON c.id = v.customer_id ORDER BY v.id DESC""",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val v = mapVehicleRow(cursor)
                vehiclesArr.put(
                    JSONObject().apply {
                        put("id", v.id)
                        put("customer_id", v.customerId)
                        put("owner_name", v.ownerName)
                        put("make", v.make)
                        put("model", v.model)
                        put("year", v.year)
                        put("engine", v.engine)
                        put("color", v.color)
                        put("fuel_type", v.fuelType)
                        put("transmission", v.transmission)
                        put("plate", v.plate)
                        put("vin", v.vin)
                        put("odometer", v.odometer)
                        put("notes", v.notes)
                    }
                )
            }
        }
        rootObj.put("vehicles", vehiclesArr)

        val woArr = JSONArray()
        getAllWorkOrders(limit = 2000).forEach { w ->
            woArr.put(
                JSONObject().apply {
                    put("id", w.id)
                    put("customer_name", w.customerName)
                    put("vehicle_name", w.vehicleName)
                    put("plate", w.vehiclePlate)
                    put("status", w.status)
                    put("priority", w.priority)
                    put("technician_name", w.technicianName)
                    put("complaint", w.complaint)
                    put("diagnosis", w.diagnosis)
                    put("total_cost", w.totalCost)
                    put("paid_amount", w.paidAmount)
                    put("remaining_amount", w.remainingAmount)
                    put("created_at", w.createdAt)
                }
            )
        }
        rootObj.put("work_orders", woArr)

        val partsArr = JSONArray()
        getAllParts(limit = 2000).forEach { p ->
            partsArr.put(
                JSONObject().apply {
                    put("id", p.id)
                    put("part_number", p.partNumber)
                    put("name", p.name)
                    put("quantity", p.quantity)
                    put("unit", p.unit)
                    put("min_quantity", p.minQuantity)
                    put("purchase_price", p.purchasePrice)
                    put("sale_price", p.salePrice)
                    put("supplier", p.supplier)
                    put("shelf_location", p.shelfLocation)
                    put("barcode", p.barcode)
                }
            )
        }
        rootObj.put("parts", partsArr)

        val backupDir = File(appContext.filesDir, "backups").apply { mkdirs() }
        val outFile = File(backupDir, "workshop_backup_${System.currentTimeMillis()}.json")
        outFile.writeText(rootObj.toString(2), Charsets.UTF_8)
        return outFile
    }

    companion object {
        fun todayString(): String =
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

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
