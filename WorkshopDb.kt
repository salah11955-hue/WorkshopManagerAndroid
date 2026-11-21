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

// ==================== Data Classes ====================

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
    val ownerPhone: String,
    val color: String = "",
    val fuelType: String = "",
    val transmission: String = ""
)

data class CustomerProfile(
    val id: Long,
    val name: String,
    val phone: String,
    val alternatePhones: String,
    val address: String,
    val notes: String,
    val email: String = "",
    val city: String = "",
    val type: String = "فرد",
    val debt: Double = 0.0
)

data class CustomerWorkOrderRecord(
    val id: Long,
    val vehicleId: Long,
    val vehicleName: String,
    val complaint: String,
    val diagnosis: String,
    val repairNotes: String,
    val status: String,
    val laborCost: Double,
    val partsCost: Double,
    val createdAt: String,
    val priority: String = "عادية",
    val technician: String = "",
    val discount: Double = 0.0
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
    val createdAt: String,
    val priority: String = "عادية",
    val technician: String = "",
    val discount: Double = 0.0,
    val expectedDate: String = ""
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

data class PaymentRecord(
    val id: Long,
    val workOrderId: Long,
    val amount: Double,
    val method: String,
    val note: String,
    val createdAt: String
)

data class VehiclePhoto(
    val id: Long,
    val vehicleId: Long,
    val uri: String,
    val caption: String,
    val createdAt: String
)

data class DashboardStats(
    val totalCustomers: Int,
    val totalVehicles: Int,
    val openOrders: Int,
    val completedToday: Int,
    val revenueToday: Double,
    val totalDebt: Double,
    val lowStockCount: Int,
    val totalParts: Int
)

data class DailyReport(
    val date: String,
    val ordersCount: Int,
    val completedCount: Int,
    val revenue: Double,
    val laborTotal: Double,
    val partsTotal: Double,
    val paymentsReceived: Double,
    val debtRemaining: Double
)

data class PartItem(
    val id: Long,
    val partNumber: String,
    val name: String,
    val quantity: Double,
    val unit: String,
    val purchasePrice: Double,
    val salePrice: Double,
    val minQuantity: Double,
    val supplier: String = "",
    val shelf: String = "",
    val barcode: String = ""
)

data class ClickableRecord(
    val id: Long,
    val title: String,
    val subtitle: String,
    val vehicleId: Long = 0
)


class WorkshopDb(context: Context) :
    SQLiteOpenHelper(context, "workshop.db", null, 5) {

    @Volatile
    private var activeUserId: Long? = null

    private val permissionModules = setOf(
        "customers", "vehicles", "work_orders", "parts", "users", "reports", "payments"
    )

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        createBusinessTables(db)
        createUsersTable(db)
        createPermissionsTable(db)
        createPaymentsTable(db)
        createPhotosTable(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) createUsersTable(db)
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
            // --- ترحيل v5: حقول جديدة + جداول دفعات وصور ---
            safeAddColumn(db, "customers", "email", "TEXT NOT NULL DEFAULT ''")
            safeAddColumn(db, "customers", "city", "TEXT NOT NULL DEFAULT ''")
            safeAddColumn(db, "customers", "type", "TEXT NOT NULL DEFAULT 'فرد'")
            safeAddColumn(db, "vehicles", "color", "TEXT NOT NULL DEFAULT ''")
            safeAddColumn(db, "vehicles", "fuel_type", "TEXT NOT NULL DEFAULT ''")
            safeAddColumn(db, "vehicles", "transmission", "TEXT NOT NULL DEFAULT ''")
            safeAddColumn(db, "work_orders", "priority", "TEXT NOT NULL DEFAULT 'عادية'")
            safeAddColumn(db, "work_orders", "technician", "TEXT NOT NULL DEFAULT ''")
            safeAddColumn(db, "work_orders", "discount", "REAL NOT NULL DEFAULT 0")
            safeAddColumn(db, "work_orders", "expected_date", "TEXT NOT NULL DEFAULT ''")
            safeAddColumn(db, "parts", "supplier", "TEXT NOT NULL DEFAULT ''")
            safeAddColumn(db, "parts", "shelf", "TEXT NOT NULL DEFAULT ''")
            safeAddColumn(db, "parts", "barcode", "TEXT NOT NULL DEFAULT ''")
            createPaymentsTable(db)
            createPhotosTable(db)
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_payments_work ON payments(work_order_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_photos_vehicle ON vehicle_photos(vehicle_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_parts_barcode ON parts(barcode)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_work_status ON work_orders(status)")
        }
    }

    private fun safeAddColumn(db: SQLiteDatabase, table: String, col: String, def: String) {
        try { db.execSQL("ALTER TABLE " + table + " ADD COLUMN " + col + " " + def) } catch (e: Exception) { }
    }

    private fun createBusinessTables(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE customers (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            phone TEXT NOT NULL UNIQUE,
            name TEXT NOT NULL DEFAULT '',
            alternate_phones TEXT NOT NULL DEFAULT '',
            address TEXT NOT NULL DEFAULT '',
            notes TEXT NOT NULL DEFAULT '',
            email TEXT NOT NULL DEFAULT '',
            city TEXT NOT NULL DEFAULT '',
            type TEXT NOT NULL DEFAULT 'فرد',
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
            color TEXT NOT NULL DEFAULT '',
            fuel_type TEXT NOT NULL DEFAULT '',
            transmission TEXT NOT NULL DEFAULT '',
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
            priority TEXT NOT NULL DEFAULT 'عادية',
            technician TEXT NOT NULL DEFAULT '',
            discount REAL NOT NULL DEFAULT 0,
            expected_date TEXT NOT NULL DEFAULT '',
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
            min_quantity REAL NOT NULL DEFAULT 0,
            supplier TEXT NOT NULL DEFAULT '',
            shelf TEXT NOT NULL DEFAULT '',
            barcode TEXT NOT NULL DEFAULT ''
        )""")
        db.execSQL("""CREATE TABLE vehicle_owners (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            vehicle_id INTEGER NOT NULL,
            old_owner TEXT NOT NULL DEFAULT '',
            new_owner TEXT NOT NULL DEFAULT '',
            reason TEXT NOT NULL DEFAULT '',
            changed_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY(vehicle_id) REFERENCES vehicles(id)
        )""")
    }

    private fun createPaymentsTable(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS payments (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            work_order_id INTEGER NOT NULL,
            amount REAL NOT NULL DEFAULT 0,
            method TEXT NOT NULL DEFAULT 'نقدي',
            note TEXT NOT NULL DEFAULT '',
            created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY(work_order_id) REFERENCES work_orders(id)
        )""")
    }

    private fun createPhotosTable(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS vehicle_photos (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            vehicle_id INTEGER NOT NULL,
            uri TEXT NOT NULL DEFAULT '',
            caption TEXT NOT NULL DEFAULT '',
            created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY(vehicle_id) REFERENCES vehicles(id)
        )""")
    }

    private fun createUsersTable(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS users (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            username TEXT NOT NULL UNIQUE,
            password_hash TEXT NOT NULL DEFAULT '',
            salt TEXT NOT NULL DEFAULT '',
            full_name TEXT NOT NULL DEFAULT '',
            role TEXT NOT NULL DEFAULT 'موظف',
            created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
        )""")
    }

    private fun createPermissionsTable(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS user_permissions (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id INTEGER NOT NULL,
            module TEXT NOT NULL,
            can_view INTEGER NOT NULL DEFAULT 0,
            can_add INTEGER NOT NULL DEFAULT 0,
            can_edit INTEGER NOT NULL DEFAULT 0,
            can_delete INTEGER NOT NULL DEFAULT 0,
            UNIQUE(user_id, module),
            FOREIGN KEY(user_id) REFERENCES users(id)
        )""")
    }

    private fun seedManagerPermissions(db: SQLiteDatabase) {
        try {
            db.rawQuery("SELECT id, role FROM users", null).use { c ->
                while (c.moveToNext()) {
                    val uid = c.getLong(0); val role = c.getString(1) ?: ""
                    if (role == "مدير" || role == "admin" || role == "manager") {
                        for (m in permissionModules) {
                            db.execSQL("INSERT OR REPLACE INTO user_permissions(user_id, module, can_view, can_add, can_edit, can_delete) VALUES (?,?,?,?,1,1)",
                                arrayOf(uid.toString(), m, "1", "1"))
                        }
                    }
                }
            }
        } catch (e: Exception) { }
    }

    // ---------- Users & Auth ----------
    fun hasUsers(): Boolean {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM users", null).use { c -> return c.moveToFirst() && c.getInt(0) > 0 }
    }
    fun setActiveUser(id: Long?) { activeUserId = id }
    fun getActiveUserId(): Long? = activeUserId

    fun createUser(username: String, password: String, fullName: String, role: String, perms: Map<String, UserPermission>? = null): Long {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val saltB64 = Base64.encodeToString(salt, Base64.NO_WRAP)
        val hash = hashPassword(password, salt)
        val values = ContentValues().apply {
            put("username", username.trim()); put("password_hash", hash); put("salt", saltB64)
            put("full_name", fullName.trim()); put("role", role.trim())
        }
        val id = writableDatabase.insertOrThrow("users", null, values)
        val modules = if (role == "مدير") permissionModules else (perms?.keys ?: permissionModules)
        for (m in modules) {
            val p = if (role == "مدير") UserPermission(m, true, true, true, true) else perms?.get(m) ?: UserPermission(m, true, false, false, false)
            savePermission(id, p)
        }
        return id
    }

    fun authenticate(username: String, password: String): AuthUser? {
        readableDatabase.rawQuery("SELECT id, username, full_name, role, password_hash, salt FROM users WHERE username=? LIMIT 1", arrayOf(username.trim())).use { c ->
            if (!c.moveToFirst()) return null
            val salt = Base64.decode(c.getString(5), Base64.NO_WRAP)
            if (hashPassword(password, salt) != c.getString(4)) return null
            val user = AuthUser(c.getLong(0), c.getString(1), c.getString(2), c.getString(3))
            activeUserId = user.id
            return user
        }
    }

    fun hasPermission(module: String, action: String): Boolean {
        val uid = activeUserId ?: return false
        readableDatabase.rawQuery("SELECT role FROM users WHERE id=?", arrayOf(uid.toString())).use { c ->
            if (c.moveToFirst() && c.getString(0) == "مدير") return true
        }
        val col = when (action) { "view" -> "can_view"; "add" -> "can_add"; "edit" -> "can_edit"; "delete" -> "can_delete"; else -> "can_view" }
        readableDatabase.rawQuery("SELECT " + col + " FROM user_permissions WHERE user_id=? AND module=? LIMIT 1", arrayOf(uid.toString(), module)).use { c ->
            return c.moveToFirst() && c.getInt(0) == 1
        }
    }

    fun savePermission(userId: Long, p: UserPermission) {
        val v = ContentValues().apply {
            put("user_id", userId); put("module", p.module)
            put("can_view", if (p.canView) 1 else 0); put("can_add", if (p.canAdd) 1 else 0)
            put("can_edit", if (p.canEdit) 1 else 0); put("can_delete", if (p.canDelete) 1 else 0)
        }
        writableDatabase.insertWithOnConflict("user_permissions", null, v, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getUserPermissions(userId: Long): List<UserPermission> {
        val out = mutableListOf<UserPermission>()
        readableDatabase.rawQuery("SELECT module, can_view, can_add, can_edit, can_delete FROM user_permissions WHERE user_id=?", arrayOf(userId.toString())).use { c ->
            while (c.moveToNext()) out.add(UserPermission(c.getString(0), c.getInt(1)==1, c.getInt(2)==1, c.getInt(3)==1, c.getInt(4)==1))
        }
        return out
    }

    fun getAllUsers(): List<AuthUser> {
        val out = mutableListOf<AuthUser>()
        readableDatabase.rawQuery("SELECT id, username, full_name, role FROM users ORDER BY id DESC", null).use { c ->
            while (c.moveToNext()) out.add(AuthUser(c.getLong(0), c.getString(1), c.getString(2), c.getString(3)))
        }
        return out
    }

    fun deleteUser(userId: Long): Int = writableDatabase.delete("users", "id=?", arrayOf(userId.toString()))

    private fun hashPassword(password: String, salt: ByteArray): String {
        val spec = PBEKeySpec(password.toCharArray(), salt, 10000, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return Base64.encodeToString(factory.generateSecret(spec).encoded, Base64.NO_WRAP)
    }

    // ---------- Customers ----------
    fun addCustomer(name: String, phone: String, alt: String, address: String, notes: String): Long =
        addCustomer(name, phone, alt, address, notes, "", "", "فرد")

    fun addCustomer(name: String, phone: String, alt: String, address: String, notes: String, email: String, city: String, type: String): Long {
        check(hasPermission("customers", "add")) { "ليس لديك صلاحية إضافة عميل" }
        val cleanName = name.trim(); val cleanPhone = normalizePhone(phone)
        require(cleanName.isNotBlank()) { "يرجى إدخال اسم العميل" }
        require(cleanPhone.isNotBlank()) { "يرجى إدخال رقم هاتف صحيح" }
        val v = ContentValues().apply {
            put("name", cleanName); put("phone", cleanPhone); put("alternate_phones", alt.trim())
            put("address", address.trim()); put("notes", notes.trim())
            put("email", email.trim()); put("city", city.trim()); put("type", type.trim().ifBlank { "فرد" })
        }
        return writableDatabase.insertOrThrow("customers", null, v)
    }

    fun updateCustomer(id: Long, name: String, phone: String, alt: String, address: String, notes: String, email: String, city: String, type: String): Int {
        val v = ContentValues().apply {
            put("name", name.trim()); put("phone", normalizePhone(phone)); put("alternate_phones", alt.trim())
            put("address", address.trim()); put("notes", notes.trim()); put("email", email.trim()); put("city", city.trim()); put("type", type)
        }
        return writableDatabase.update("customers", v, "id=?", arrayOf(id.toString()))
    }

    fun deleteCustomer(id: Long): Int = writableDatabase.delete("customers", "id=?", arrayOf(id.toString()))

    fun getCustomerProfile(id: Long): CustomerProfile? {
        readableDatabase.rawQuery("SELECT id, name, phone, alternate_phones, address, notes, email, city, type FROM customers WHERE id=?", arrayOf(id.toString())).use { c ->
            if (!c.moveToFirst()) return null
            return CustomerProfile(c.getLong(0), c.getString(1) ?: "", c.getString(2) ?: "", c.getString(3) ?: "", c.getString(4) ?: "", c.getString(5) ?: "", c.getString(6) ?: "", c.getString(7) ?: "", c.getString(8) ?: "فرد", getCustomerDebt(id))
        }
    }

    fun getAllCustomers(): List<CustomerProfile> {
        val out = mutableListOf<CustomerProfile>()
        readableDatabase.rawQuery("SELECT id, name, phone, alternate_phones, address, notes, email, city, type FROM customers ORDER BY id DESC LIMIT 200", null).use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                out.add(CustomerProfile(id, c.getString(1) ?: "", c.getString(2) ?: "", c.getString(3) ?: "", c.getString(4) ?: "", c.getString(5) ?: "", c.getString(6) ?: "", c.getString(7) ?: "", c.getString(8) ?: "فرد", 0.0))
            }
        }
        return out
    }

    fun getCustomerDebt(customerId: Long): Double {
        var total = 0.0; var paid = 0.0
        readableDatabase.rawQuery("SELECT COALESCE(SUM(labor_cost + parts_cost - discount),0) FROM work_orders WHERE customer_id=?", arrayOf(customerId.toString())).use { c -> if (c.moveToFirst()) total = c.getDouble(0) }
        readableDatabase.rawQuery("SELECT COALESCE(SUM(p.amount),0) FROM payments p JOIN work_orders w ON w.id=p.work_order_id WHERE w.customer_id=?", arrayOf(customerId.toString())).use { c -> if (c.moveToFirst()) paid = c.getDouble(0) }
        return (total - paid).coerceAtLeast(0.0)
    }

    // ---------- Vehicles ----------
    fun addVehicle(customerId: Long, make: String, model: String, year: String, engine: String, plate: String, vin: String, odometer: String): Long =
        addVehicle(customerId, make, model, year, engine, plate, vin, odometer, "", "", "", "")

    fun addVehicle(customerId: Long, make: String, model: String, year: String, engine: String, plate: String, vin: String, odometer: String, notes: String): Long =
        addVehicle(customerId, make, model, year, engine, plate, vin, odometer, notes, "", "", "")

    fun addVehicle(customerId: Long, make: String, model: String, year: String, engine: String, plate: String, vin: String, odometer: String, notes: String, color: String, fuelType: String, transmission: String): Long {
        check(hasPermission("vehicles", "add")) { "ليس لديك صلاحية إضافة سيارة" }
        val cleanPlate = plate.trim(); val cleanVin = normalizeVin(vin)
        val db = writableDatabase; db.beginTransaction()
        try {
            if (cleanPlate.isNotBlank()) {
                db.rawQuery("SELECT id FROM vehicles WHERE TRIM(plate)=? COLLATE NOCASE LIMIT 1", arrayOf(cleanPlate)).use { cur -> require(!cur.moveToFirst()) { "رقم اللوحة مسجل مسبقاً" } }
            }
            val v = ContentValues().apply {
                put("customer_id", customerId); put("make", make.trim()); put("model", model.trim())
                put("model_year", year.trim().toIntOrNull()); put("engine", engine.trim()); put("plate", cleanPlate)
                put("vin", cleanVin); put("current_odometer", odometer.trim().toLongOrNull() ?: 0L); put("notes", notes.trim())
                put("color", color.trim()); put("fuel_type", fuelType.trim()); put("transmission", transmission.trim())
            }
            val id = db.insertOrThrow("vehicles", null, v)
            db.setTransactionSuccessful(); return id
        } finally { db.endTransaction() }
    }

    fun getVehicleProfile(id: Long): VehicleProfile? {
        readableDatabase.rawQuery("""SELECT v.id, v.customer_id, v.make, v.model, COALESCE(v.model_year,''), v.engine, v.plate, v.vin, v.current_odometer, v.notes, COALESCE(c.name,''), COALESCE(c.phone,''), COALESCE(v.color,''), COALESCE(v.fuel_type,''), COALESCE(v.transmission,'') FROM vehicles v LEFT JOIN customers c ON c.id=v.customer_id WHERE v.id=?""", arrayOf(id.toString())).use { c ->
            if (!c.moveToFirst()) return null
            return VehicleProfile(c.getLong(0), c.getLong(1), c.getString(2) ?: "", c.getString(3) ?: "", c.getString(4) ?: "", c.getString(5) ?: "", c.getString(6) ?: "", c.getString(7) ?: "", c.getLong(8), c.getString(9) ?: "", c.getString(10) ?: "", c.getString(11) ?: "", c.getString(12) ?: "", c.getString(13) ?: "", c.getString(14) ?: "")
        }
    }

    fun getAllVehicles(): List<VehicleProfile> {
        val out = mutableListOf<VehicleProfile>()
        readableDatabase.rawQuery("SELECT v.id, v.customer_id, v.make, v.model, COALESCE(v.model_year,''), v.engine, v.plate, v.vin, v.current_odometer, v.notes, COALESCE(c.name,''), COALESCE(c.phone,''), COALESCE(v.color,''), COALESCE(v.fuel_type,''), COALESCE(v.transmission,'') FROM vehicles v LEFT JOIN customers c ON c.id=v.customer_id ORDER BY v.id DESC LIMIT 200", null).use { c ->
            while (c.moveToNext()) out.add(VehicleProfile(c.getLong(0), c.getLong(1), c.getString(2) ?: "", c.getString(3) ?: "", c.getString(4) ?: "", c.getString(5) ?: "", c.getString(6) ?: "", c.getString(7) ?: "", c.getLong(8), c.getString(9) ?: "", c.getString(10) ?: "", c.getString(11) ?: "", c.getString(12) ?: "", c.getString(13) ?: "", c.getString(14) ?: ""))
        }
        return out
    }

    fun transferOwnership(vehicleId: Long, newCustomerId: Long, reason: String) {
        val db = writableDatabase; db.beginTransaction()
        try {
            var oldName = ""; var oldId = 0L
            db.rawQuery("SELECT v.customer_id, COALESCE(c.name,'') FROM vehicles v LEFT JOIN customers c ON c.id=v.customer_id WHERE v.id=?", arrayOf(vehicleId.toString())).use { c -> if (c.moveToFirst()) { oldId = c.getLong(0); oldName = c.getString(1) } }
            var newName = ""
            db.rawQuery("SELECT name FROM customers WHERE id=?", arrayOf(newCustomerId.toString())).use { c -> if (c.moveToFirst()) newName = c.getString(0) }
            db.execSQL("UPDATE vehicles SET customer_id=? WHERE id=?", arrayOf(newCustomerId.toString(), vehicleId.toString()))
            val v = ContentValues().apply { put("vehicle_id", vehicleId); put("old_owner", oldName); put("new_owner", newName); put("reason", reason) }
            db.insertOrThrow("vehicle_owners", null, v)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun getVehicleOwnerHistory(vehicleId: Long): List<OwnerHistoryRecord> {
        val out = mutableListOf<OwnerHistoryRecord>()
        readableDatabase.rawQuery("SELECT old_owner, new_owner, reason, changed_at FROM vehicle_owners WHERE vehicle_id=? ORDER BY id DESC", arrayOf(vehicleId.toString())).use { c ->
            while (c.moveToNext()) out.add(OwnerHistoryRecord(c.getString(0) ?: "", c.getString(1) ?: "", c.getString(2) ?: "", c.getString(3) ?: ""))
        }
        return out
    }

    // ---------- Vehicle Photos ----------
    fun addVehiclePhoto(vehicleId: Long, uri: String, caption: String): Long {
        val v = ContentValues().apply { put("vehicle_id", vehicleId); put("uri", uri); put("caption", caption) }
        return writableDatabase.insertOrThrow("vehicle_photos", null, v)
    }
    fun getVehiclePhotos(vehicleId: Long): List<VehiclePhoto> {
        val out = mutableListOf<VehiclePhoto>()
        readableDatabase.rawQuery("SELECT id, vehicle_id, uri, caption, created_at FROM vehicle_photos WHERE vehicle_id=? ORDER BY id DESC", arrayOf(vehicleId.toString())).use { c ->
            while (c.moveToNext()) out.add(VehiclePhoto(c.getLong(0), c.getLong(1), c.getString(2) ?: "", c.getString(3) ?: "", c.getString(4) ?: ""))
        }
        return out
    }
    fun deleteVehiclePhoto(id: Long): Int = writableDatabase.delete("vehicle_photos", "id=?", arrayOf(id.toString()))

    // ---------- Work Orders + Payments ----------
    fun addWorkOrder(customerId: Long, vehicleId: Long, odometer: Long, complaint: String, diagnosis: String, repairNotes: String, status: String, labor: Double, partsCost: Double): Long =
        addWorkOrder(customerId, vehicleId, odometer, complaint, diagnosis, repairNotes, status, labor, partsCost, "عادية", "", 0.0, "")

    fun addWorkOrder(customerId: Long, vehicleId: Long, odometer: Long, complaint: String, diagnosis: String, repairNotes: String, status: String, labor: Double, partsCost: Double, priority: String, technician: String, discount: Double, expectedDate: String): Long {
        val v = ContentValues().apply {
            put("customer_id", customerId); put("vehicle_id", vehicleId); put("odometer_at_visit", odometer)
            put("complaint", complaint.trim()); put("diagnosis", diagnosis.trim()); put("repair_notes", repairNotes.trim())
            put("status", status); put("labor_cost", labor); put("parts_cost", partsCost)
            put("priority", priority); put("technician", technician.trim()); put("discount", discount); put("expected_date", expectedDate.trim())
        }
        val id = writableDatabase.insertOrThrow("work_orders", null, v)
        writableDatabase.execSQL("UPDATE vehicles SET current_odometer=? WHERE id=?", arrayOf(odometer.toString(), vehicleId.toString()))
        return id
    }

    fun updateWorkOrderStatus(id: Long, status: String): Int {
        val v = ContentValues().apply { put("status", status) }
        return writableDatabase.update("work_orders", v, "id=?", arrayOf(id.toString()))
    }

    fun getWorkOrderById(id: Long): WorkOrderHistoryRecord? {
        readableDatabase.rawQuery("SELECT w.id, COALESCE(c.name,''), w.odometer_at_visit, w.complaint, w.diagnosis, w.repair_notes, w.status, w.labor_cost, w.parts_cost, w.created_at, COALESCE(w.priority,'عادية'), COALESCE(w.technician,''), COALESCE(w.discount,0), COALESCE(w.expected_date,'') FROM work_orders w LEFT JOIN customers c ON c.id=w.customer_id WHERE w.id=?", arrayOf(id.toString())).use { c ->
            if (!c.moveToFirst()) return null
            return WorkOrderHistoryRecord(c.getLong(0), c.getString(1) ?: "", c.getLong(2), c.getString(3) ?: "", c.getString(4) ?: "", c.getString(5) ?: "", c.getString(6) ?: "", c.getDouble(7), c.getDouble(8), c.getString(9) ?: "", c.getString(10) ?: "", c.getString(11) ?: "", c.getDouble(12), c.getString(13) ?: "")
        }
    }

    fun getAllWorkOrders(): List<WorkOrderHistoryRecord> {
        val out = mutableListOf<WorkOrderHistoryRecord>()
        readableDatabase.rawQuery("SELECT w.id, COALESCE(c.name,''), w.odometer_at_visit, w.complaint, w.diagnosis, w.repair_notes, w.status, w.labor_cost, w.parts_cost, w.created_at, COALESCE(w.priority,'عادية'), COALESCE(w.technician,''), COALESCE(w.discount,0), COALESCE(w.expected_date,'') FROM work_orders w LEFT JOIN customers c ON c.id=w.customer_id ORDER BY w.id DESC LIMIT 200", null).use { c ->
            while (c.moveToNext()) out.add(WorkOrderHistoryRecord(c.getLong(0), c.getString(1) ?: "", c.getLong(2), c.getString(3) ?: "", c.getString(4) ?: "", c.getString(5) ?: "", c.getString(6) ?: "", c.getDouble(7), c.getDouble(8), c.getString(9) ?: "", c.getString(10) ?: "", c.getString(11) ?: "", c.getDouble(12), c.getString(13) ?: ""))
        }
        return out
    }

    fun getVehicleWorkOrders(vehicleId: Long): List<WorkOrderHistoryRecord> {
        val out = mutableListOf<WorkOrderHistoryRecord>()
        readableDatabase.rawQuery("SELECT w.id, COALESCE(c.name,''), w.odometer_at_visit, w.complaint, w.diagnosis, w.repair_notes, w.status, w.labor_cost, w.parts_cost, w.created_at, COALESCE(w.priority,'عادية'), COALESCE(w.technician,''), COALESCE(w.discount,0), COALESCE(w.expected_date,'') FROM work_orders w LEFT JOIN customers c ON c.id=w.customer_id WHERE w.vehicle_id=? ORDER BY w.id DESC", arrayOf(vehicleId.toString())).use { c ->
            while (c.moveToNext()) out.add(WorkOrderHistoryRecord(c.getLong(0), c.getString(1) ?: "", c.getLong(2), c.getString(3) ?: "", c.getString(4) ?: "", c.getString(5) ?: "", c.getString(6) ?: "", c.getDouble(7), c.getDouble(8), c.getString(9) ?: "", c.getString(10) ?: "", c.getString(11) ?: "", c.getDouble(12), c.getString(13) ?: ""))
        }
        return out
    }

    fun getCustomerWorkOrders(customerId: Long): List<CustomerWorkOrderRecord> {
        val out = mutableListOf<CustomerWorkOrderRecord>()
        readableDatabase.rawQuery("SELECT w.id, w.vehicle_id, COALESCE(v.make,'') || ' ' || COALESCE(v.model,''), w.complaint, w.diagnosis, w.repair_notes, w.status, w.labor_cost, w.parts_cost, w.created_at, COALESCE(w.priority,'عادية'), COALESCE(w.technician,''), COALESCE(w.discount,0) FROM work_orders w LEFT JOIN vehicles v ON v.id=w.vehicle_id WHERE w.customer_id=? ORDER BY w.id DESC", arrayOf(customerId.toString())).use { c ->
            while (c.moveToNext()) out.add(CustomerWorkOrderRecord(c.getLong(0), c.getLong(1), c.getString(2) ?: "", c.getString(3) ?: "", c.getString(4) ?: "", c.getString(5) ?: "", c.getString(6) ?: "", c.getDouble(7), c.getDouble(8), c.getString(9) ?: "", c.getString(10) ?: "", c.getString(11) ?: "", c.getDouble(12)))
        }
        return out
    }

    fun addPayment(workOrderId: Long, amount: Double, method: String, note: String): Long {
        val v = ContentValues().apply { put("work_order_id", workOrderId); put("amount", amount); put("method", method); put("note", note.trim()) }
        return writableDatabase.insertOrThrow("payments", null, v)
    }

    fun getPayments(workOrderId: Long): List<PaymentRecord> {
        val out = mutableListOf<PaymentRecord>()
        readableDatabase.rawQuery("SELECT id, work_order_id, amount, method, note, created_at FROM payments WHERE work_order_id=? ORDER BY id DESC", arrayOf(workOrderId.toString())).use { c ->
            while (c.moveToNext()) out.add(PaymentRecord(c.getLong(0), c.getLong(1), c.getDouble(2), c.getString(3) ?: "", c.getString(4) ?: "", c.getString(5) ?: ""))
        }
        return out
    }

    fun getWorkOrderBalance(workOrderId: Long): Pair<Double, Double> {
        var total = 0.0; var paid = 0.0
        readableDatabase.rawQuery("SELECT COALESCE(labor_cost + parts_cost - discount,0) FROM work_orders WHERE id=?", arrayOf(workOrderId.toString())).use { c -> if (c.moveToFirst()) total = c.getDouble(0) }
        readableDatabase.rawQuery("SELECT COALESCE(SUM(amount),0) FROM payments WHERE work_order_id=?", arrayOf(workOrderId.toString())).use { c -> if (c.moveToFirst()) paid = c.getDouble(0) }
        return Pair(paid, (total - paid).coerceAtLeast(0.0))
    }

    // ---------- Parts ----------
    fun addPart(partNumber: String, name: String, qty: Double, unit: String, purchase: Double, sale: Double, minQty: Double): Long =
        addPart(partNumber, name, qty, unit, purchase, sale, minQty, "", "", "")

    fun addPart(partNumber: String, name: String, qty: Double, unit: String, purchase: Double, sale: Double, minQty: Double, supplier: String, shelf: String, barcode: String): Long {
        val v = ContentValues().apply {
            put("part_number", partNumber.trim()); put("name", name.trim()); put("quantity", qty); put("unit", unit.trim())
            put("purchase_price", purchase); put("sale_price", sale); put("min_quantity", minQty)
            put("supplier", supplier.trim()); put("shelf", shelf.trim()); put("barcode", barcode.trim())
        }
        return writableDatabase.insertOrThrow("parts", null, v)
    }

    fun getAllParts(): List<PartItem> {
        val out = mutableListOf<PartItem>()
        readableDatabase.rawQuery("SELECT id, part_number, name, quantity, unit, purchase_price, sale_price, min_quantity, COALESCE(supplier,''), COALESCE(shelf,''), COALESCE(barcode,'') FROM parts ORDER BY id DESC LIMIT 300", null).use { c ->
            while (c.moveToNext()) out.add(PartItem(c.getLong(0), c.getString(1) ?: "", c.getString(2) ?: "", c.getDouble(3), c.getString(4) ?: "", c.getDouble(5), c.getDouble(6), c.getDouble(7), c.getString(8) ?: "", c.getString(9) ?: "", c.getString(10) ?: ""))
        }
        return out
    }

    fun getLowStockParts(): List<PartItem> = getAllParts().filter { it.quantity <= it.minQuantity }

    fun adjustPartStock(id: Long, delta: Double): Int {
        writableDatabase.execSQL("UPDATE parts SET quantity = MAX(0, quantity + ?) WHERE id=?", arrayOf(delta.toString(), id.toString()))
        return 1
    }

    // ---------- Dashboard & Reports ----------
    fun getDashboardStats(): DashboardStats {
        var cust = 0; var veh = 0; var open = 0; var compToday = 0; var revToday = 0.0; var debt = 0.0; var partsCount = 0
        readableDatabase.rawQuery("SELECT COUNT(*) FROM customers", null).use { if (it.moveToFirst()) cust = it.getInt(0) }
        readableDatabase.rawQuery("SELECT COUNT(*) FROM vehicles", null).use { if (it.moveToFirst()) veh = it.getInt(0) }
        readableDatabase.rawQuery("SELECT COUNT(*) FROM work_orders WHERE status != 'مكتمل' AND status != 'مغلق'", null).use { if (it.moveToFirst()) open = it.getInt(0) }
        readableDatabase.rawQuery("SELECT COUNT(*) FROM work_orders WHERE date(created_at)=date('now') AND status='مكتمل'", null).use { if (it.moveToFirst()) compToday = it.getInt(0) }
        readableDatabase.rawQuery("SELECT COALESCE(SUM(amount),0) FROM payments WHERE date(created_at)=date('now')", null).use { if (it.moveToFirst()) revToday = it.getDouble(0) }
        readableDatabase.rawQuery("SELECT COALESCE(SUM(labor_cost + parts_cost - discount),0) FROM work_orders", null).use { c -> if (c.moveToFirst()) debt = c.getDouble(0) }
        var paidAll = 0.0
        readableDatabase.rawQuery("SELECT COALESCE(SUM(amount),0) FROM payments", null).use { if (it.moveToFirst()) paidAll = it.getDouble(0) }
        readableDatabase.rawQuery("SELECT COUNT(*) FROM parts", null).use { if (it.moveToFirst()) partsCount = it.getInt(0) }
        return DashboardStats(cust, veh, open, compToday, revToday, (debt - paidAll).coerceAtLeast(0.0), getLowStockParts().size, partsCount)
    }

    fun getDailyReport(date: String): DailyReport {
        // date format YYYY-MM-DD, if empty use today
        val d = if (date.isBlank()) "date('now')" else "'$date'"
        var orders = 0; var comp = 0; var labor = 0.0; var partsT = 0.0; var pay = 0.0
        readableDatabase.rawQuery("SELECT COUNT(*), COALESCE(SUM(labor_cost),0), COALESCE(SUM(parts_cost),0) FROM work_orders WHERE date(created_at)=" + d, null).use { if (it.moveToFirst()) { orders = it.getInt(0); labor = it.getDouble(1); partsT = it.getDouble(2) } }
        readableDatabase.rawQuery("SELECT COUNT(*) FROM work_orders WHERE date(created_at)=" + d + " AND status='مكتمل'", null).use { if (it.moveToFirst()) comp = it.getInt(0) }
        readableDatabase.rawQuery("SELECT COALESCE(SUM(amount),0) FROM payments WHERE date(created_at)=" + d, null).use { if (it.moveToFirst()) pay = it.getDouble(0) }
        val totalDebt = getDashboardStats().totalDebt
        return DailyReport(if (date.isBlank()) "اليوم" else date, orders, comp, labor + partsT, labor, partsT, pay, totalDebt)
    }

    // ---------- Search & Lists ----------
    fun searchAdvanced(query: String, filter: String): List<ReceptionSearchResult> {
        val q = "%" + query.trim() + "%"
        val out = mutableListOf<ReceptionSearchResult>()
        if (filter == "الكل" || filter == "عملاء") {
            readableDatabase.rawQuery("SELECT id, name, phone FROM customers WHERE name LIKE ? OR phone LIKE ? OR city LIKE ? LIMIT 20", arrayOf(q, q, q)).use { c -> while (c.moveToNext()) out.add(ReceptionSearchResult("customer", c.getLong(0), c.getString(1) ?: "", "هاتف: " + (c.getString(2) ?: ""))) }
        }
        if (filter == "الكل" || filter == "سيارات") {
            readableDatabase.rawQuery("SELECT id, make || ' ' || model, plate FROM vehicles WHERE make LIKE ? OR model LIKE ? OR plate LIKE ? OR vin LIKE ? LIMIT 20", arrayOf(q, q, q, q)).use { c -> while (c.moveToNext()) out.add(ReceptionSearchResult("vehicle", c.getLong(0), c.getString(1) ?: "", "لوحة: " + (c.getString(2) ?: ""))) }
        }
        if (filter == "الكل" || filter == "أوامر") {
            readableDatabase.rawQuery("SELECT id, complaint, status FROM work_orders WHERE complaint LIKE ? OR diagnosis LIKE ? OR status LIKE ? LIMIT 20", arrayOf(q, q, q)).use { c -> while (c.moveToNext()) out.add(ReceptionSearchResult("work_order", c.getLong(0), "أمر #" + c.getLong(0) + " - " + (c.getString(1) ?: ""), "الحالة: " + (c.getString(2) ?: ""))) }
        }
        if (filter == "الكل" || filter == "قطع") {
            readableDatabase.rawQuery("SELECT id, name, part_number FROM parts WHERE name LIKE ? OR part_number LIKE ? OR barcode LIKE ? LIMIT 20", arrayOf(q, q, q)).use { c -> while (c.moveToNext()) out.add(ReceptionSearchResult("part", c.getLong(0), c.getString(1) ?: "", "رقم: " + (c.getString(2) ?: ""))) }
        }
        return out
    }

    fun recentClickableRecords(table: String): List<ClickableRecord> {
        val out = mutableListOf<ClickableRecord>()
        val sql = when (table) {
            "customers" -> "SELECT id, name, phone FROM customers ORDER BY id DESC LIMIT 30"
            "vehicles" -> "SELECT id, make || ' ' || model, plate FROM vehicles ORDER BY id DESC LIMIT 30"
            "work_orders" -> "SELECT w.id, 'أمر #' || w.id, w.complaint FROM work_orders w ORDER BY w.id DESC LIMIT 30"
            else -> return out
        }
        readableDatabase.rawQuery(sql, null).use { c ->
            while (c.moveToNext()) {
                val vid = if (table == "work_orders") 0L else c.getLong(0)
                out.add(ClickableRecord(c.getLong(0), c.getString(1) ?: "", c.getString(2) ?: "", vid))
            }
        }
        // fix vehicleId for work_orders
        if (table == "work_orders") {
            val fixed = mutableListOf<ClickableRecord>()
            readableDatabase.rawQuery("SELECT id, vehicle_id FROM work_orders ORDER BY id DESC LIMIT 30", null).use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0); val vId = c.getLong(1)
                    val orig = out.find { it.id == id }
                    if (orig != null) fixed.add(orig.copy(vehicleId = vId))
                }
            }
            return fixed
        }
        return out
    }

    fun recentRows(table: String): List<String> {
        val sql = when (table) {
            "parts" -> "SELECT name, 'الكمية: ' || quantity || ' ' || unit || ' | رف: ' || COALESCE(shelf,'') FROM parts ORDER BY id DESC LIMIT 30"
            "customers" -> "SELECT name, phone FROM customers ORDER BY id DESC LIMIT 30"
            "vehicles" -> "SELECT make || ' ' || model, vin FROM vehicles ORDER BY id DESC LIMIT 30"
            "work_orders" -> "SELECT 'أمر #' || id, complaint FROM work_orders ORDER BY id DESC LIMIT 30"
            else -> return emptyList()
        }
        val out = mutableListOf<String>()
        readableDatabase.rawQuery(sql, null).use { c -> while (c.moveToNext()) out.add((c.getString(0) ?: "") + "\n" + (c.getString(1) ?: "")) }
        return out
    }

    fun findCustomerByPhone(phone: String): String? {
        val clean = normalizePhone(phone); if (clean.isBlank()) return null
        readableDatabase.rawQuery("SELECT name FROM customers WHERE phone=? LIMIT 1", arrayOf(clean)).use { c -> return if (c.moveToFirst()) c.getString(0) else null }
    }
    fun findVehicleByVin(vin: String): String? {
        val clean = normalizeVin(vin); if (clean.isBlank()) return null
        readableDatabase.rawQuery("SELECT make, model, plate FROM vehicles WHERE vin=? LIMIT 1", arrayOf(clean)).use { c -> return if (c.moveToFirst()) (c.getString(0) ?: "") + " " + (c.getString(1) ?: "") + " - اللوحة: " + (c.getString(2) ?: "") else null }
    }
    fun findVehicleByPlate(plate: String): String? {
        val clean = plate.trim(); if (clean.isBlank()) return null
        readableDatabase.rawQuery("SELECT make, model, vin FROM vehicles WHERE TRIM(plate)=? COLLATE NOCASE LIMIT 1", arrayOf(clean)).use { c -> return if (c.moveToFirst()) (c.getString(0) ?: "") + " " + (c.getString(1) ?: "") + " - VIN: " + (c.getString(2) ?: "") else null }
    }
    fun findPartByNumber(number: String): String? {
        val clean = number.trim(); if (clean.isBlank()) return null
        readableDatabase.rawQuery("SELECT name FROM parts WHERE TRIM(part_number)=? COLLATE NOCASE OR TRIM(barcode)=? COLLATE NOCASE LIMIT 1", arrayOf(clean, clean)).use { c -> return if (c.moveToFirst()) c.getString(0) else null }
    }

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
        val sql = "SELECT DISTINCT " + column + " FROM " + table + " WHERE " + column + " LIKE ? COLLATE NOCASE AND TRIM(" + column + ") != '' ORDER BY " + column + " LIMIT 10"
        val results = mutableListOf<String>()
        readableDatabase.rawQuery(sql, arrayOf(cleanPrefix + "%")).use { cursor -> while (cursor.moveToNext()) { val v = cursor.getString(0); if (!v.isNullOrBlank()) results.add(v) } }
        return results
    }

    fun exportBackupText(): String {
        val sb = StringBuilder()
        sb.append("=== نسخة احتياطية - ورشة خبارة ===\n")
        sb.append("العملاء:\n")
        getAllCustomers().forEach { sb.append(it.name + " | " + it.phone + " | " + it.city + "\n") }
        sb.append("\nالسيارات:\n")
        getAllVehicles().forEach { sb.append(it.make + " " + it.model + " | " + it.plate + " | " + it.vin + "\n") }
        sb.append("\nأوامر الشغل:\n")
        getAllWorkOrders().forEach { sb.append("أمر #" + it.id + " | " + it.customerName + " | " + it.status + " | " + (it.laborCost + it.partsCost - it.discount) + "\n") }
        sb.append("\nقطع الغيار:\n")
        getAllParts().forEach { sb.append(it.name + " | كمية: " + it.quantity + " | سعر: " + it.salePrice + "\n") }
        return sb.toString()
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
        fun normalizeVin(raw: String): String = raw.uppercase().filter { it.isLetterOrDigit() }
    }
}
