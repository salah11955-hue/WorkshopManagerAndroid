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

data class CustomerProfile(
    val id: Long,
    val name: String,
    val phone: String,
    val alternatePhones: String,
    val address: String,
    val notes: String
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
    val createdAt: String
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

class WorkshopDb(context: Context) :
    SQLiteOpenHelper(context, "workshop.db", null, 4) {

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

        if (oldVersion < 4) {
            createPermissionsTable(db)
            seedManagerPermissions(db)
        }
    }

    private fun createBusinessTables(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE customers (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                phone TEXT NOT NULL UNIQUE,
                name TEXT NOT NULL DEFAULT '',
                alternate_phones TEXT NOT NULL DEFAULT '',
                address TEXT NOT NULL DEFAULT '',
                notes TEXT NOT NULL DEFAULT '',
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
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
                plate TEXT NOT NULL DEFAULT '',
                vin TEXT NOT NULL UNIQUE,
                current_odometer INTEGER NOT NULL DEFAULT 0,
                notes TEXT NOT NULL DEFAULT '',
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
                labor_cost REAL NOT NULL DEFAULT 0,
                parts_cost REAL NOT NULL DEFAULT 0,
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
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
                min_quantity REAL NOT NULL DEFAULT 0
            )"""
        )

        db.execSQL(
            """CREATE TABLE vehicle_owners (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                vehicle_id INTEGER NOT NULL,
                old_customer_id INTEGER NOT NULL,
                new_customer_id INTEGER NOT NULL,
                reason TEXT NOT NULL,
                changed_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
            )"""
        )

        db.execSQL(
            "CREATE INDEX idx_vehicle_customer ON vehicles(customer_id)"
        )
        db.execSQL(
            "CREATE INDEX idx_work_vehicle ON work_orders(vehicle_id)"
        )
        db.execSQL(
            "CREATE INDEX idx_vehicle_plate ON vehicles(plate)"
        )
        db.execSQL(
            "CREATE INDEX idx_owner_vehicle ON vehicle_owners(vehicle_id)"
        )
        db.execSQL(
            "CREATE INDEX idx_work_vehicle_date ON work_orders(vehicle_id, created_at)"
        )
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
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
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
                FOREIGN KEY(user_id) REFERENCES app_users(id)
                    ON DELETE CASCADE
            )"""
        )

        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_permissions_user ON user_permissions(user_id)"
        )
    }

    private fun seedManagerPermissions(db: SQLiteDatabase) {
        db.rawQuery(
            "SELECT id FROM app_users WHERE role=?",
            arrayOf("مدير")
        ).use { cursor ->
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

                    db.insertWithOnConflict(
                        "user_permissions",
                        null,
                        values,
                        SQLiteDatabase.CONFLICT_IGNORE
                    )
                }
            }
        }
    }

    // ---------- حسابات المستخدمين ----------

    fun hasUsers(): Boolean {
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM app_users",
            null
        ).use {
            return it.moveToFirst() && it.getInt(0) > 0
        }
    }

    fun createAdmin(
        username: String,
        fullName: String,
        password: String
    ): Boolean {
        val cleanUsername = username.trim().lowercase()

        if (cleanUsername.isBlank() ||
            fullName.isBlank() ||
            password.length < 6
        ) {
            return false
        }

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
                put(
                    "password_hash",
                    Base64.encodeToString(hash, Base64.NO_WRAP)
                )
                put(
                    "password_salt",
                    Base64.encodeToString(salt, Base64.NO_WRAP)
                )
                put("role", "مدير")
            }

            createdId = db.insertOrThrow(
                "app_users",
                null,
                values
            )

            createPermissionsTable(db)

            for (module in permissionModules) {
                val permissionValues = ContentValues().apply {
                    put("user_id", createdId)
                    put("module", module)
                    put("can_view", 1)
                    put("can_add", 1)
                    put("can_edit", 1)
                    put("can_delete", 1)
                }

                db.insertOrThrow(
                    "user_permissions",
                    null,
                    permissionValues
                )
            }

            db.setTransactionSuccessful()
            true
        } catch (_: Exception) {
            false
        } finally {
            if (db.inTransaction()) {
                db.endTransaction()
            }
            if (createdId > 0) {
                activeUserId = createdId
            }
        }
    }

    private fun hasUsersInDatabase(db: SQLiteDatabase): Boolean {
        db.rawQuery(
            "SELECT COUNT(*) FROM app_users",
            null
        ).use {
            return it.moveToFirst() && it.getInt(0) > 0
        }
    }

    fun authenticate(
        username: String,
        password: String
    ): Boolean {
        activeUserId = null

        readableDatabase.rawQuery(
            """SELECT id, password_hash, password_salt
               FROM app_users
               WHERE username=?
               LIMIT 1""",
            arrayOf(username.trim().lowercase())
        ).use { cursor ->

            if (!cursor.moveToFirst()) {
                return false
            }

            return try {
                val userId = cursor.getLong(0)
                val savedHash = Base64.decode(
                    cursor.getString(1),
                    Base64.NO_WRAP
                )
                val salt = Base64.decode(
                    cursor.getString(2),
                    Base64.NO_WRAP
                )
                val enteredHash = hashPassword(password, salt)

                val valid = MessageDigest.isEqual(
                    savedHash,
                    enteredHash
                )

                if (valid) {
                    activeUserId = userId
                }

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
            """SELECT id, username, full_name, role
               FROM app_users
               WHERE id=?
               LIMIT 1""",
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

    fun setActiveUser(userId: Long): Boolean {
        readableDatabase.rawQuery(
            "SELECT id FROM app_users WHERE id=? LIMIT 1",
            arrayOf(userId.toString())
        ).use {
            if (!it.moveToFirst()) return false
        }

        activeUserId = userId
        return true
    }

    fun clearActiveUser() {
        activeUserId = null
    }

    fun hasPermission(
        module: String,
        action: String
    ): Boolean {
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
            """SELECT $column
               FROM user_permissions
               WHERE user_id=? AND module=?
               LIMIT 1""",
            arrayOf(user.id.toString(), module)
        ).use { cursor ->
            return cursor.moveToFirst() && cursor.getInt(0) == 1
        }
    }

    fun getPermissions(userId: Long): List<UserPermission> {
        if (currentUser()?.role != "مدير") return emptyList()

        val result = mutableListOf<UserPermission>()

        readableDatabase.rawQuery(
            """SELECT module, can_view, can_add, can_edit, can_delete
               FROM user_permissions
               WHERE user_id=?
               ORDER BY module""",
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

    fun setPermission(
        userId: Long,
        module: String,
        action: String,
        allowed: Boolean
    ): Boolean {
        if (currentUser()?.role != "مدير") return false
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

        val values = ContentValues().apply {
            put(column, if (allowed) 1 else 0)
        }

        return writableDatabase.update(
            "user_permissions",
            values,
            "user_id=? AND module=?",
            arrayOf(userId.toString(), module)
        ) > 0
    }

    fun listUsers(): List<AuthUser> {
        if (currentUser()?.role != "مدير") return emptyList()

        val result = mutableListOf<AuthUser>()

        readableDatabase.rawQuery(
            """SELECT id, username, full_name, role
               FROM app_users
               ORDER BY id""",
            null
        ).use { cursor ->
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

    fun createUser(
        username: String,
        fullName: String,
        password: String,
        role: String = "فني"
    ): Boolean {
        if (currentUser()?.role != "مدير") return false

        val cleanUsername = username.trim().lowercase()
        if (cleanUsername.isBlank() ||
            fullName.isBlank() ||
            password.length < 6 ||
            role.isBlank() ||
            role == "مدير"
        ) {
            return false
        }

        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        val hash = hashPassword(password, salt)

        val db = writableDatabase

        return try {
            db.beginTransaction()

            val values = ContentValues().apply {
                put("username", cleanUsername)
                put("full_name", fullName.trim())
                put(
                    "password_hash",
                    Base64.encodeToString(hash, Base64.NO_WRAP)
                )
                put(
                    "password_salt",
                    Base64.encodeToString(salt, Base64.NO_WRAP)
                )
                put("role", role.trim())
            }

            val newUserId = db.insertOrThrow(
                "app_users",
                null,
                values
            )

            for (module in permissionModules) {
                val permissionValues = ContentValues().apply {
                    put("user_id", newUserId)
                    put("module", module)
                    put("can_view", 0)
                    put("can_add", 0)
                    put("can_edit", 0)
                    put("can_delete", 0)
                }

                db.insertOrThrow(
                    "user_permissions",
                    null,
                    permissionValues
                )
            }

            db.setTransactionSuccessful()
            true
        } catch (_: Exception) {
            false
        } finally {
            if (db.inTransaction()) {
                db.endTransaction()
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
            SecretKeyFactory
                .getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .encoded
        } finally {
            spec.clearPassword()
        }
    }

        // ---------- بيانات الورشة ----------

    fun count(table: String): Int {
        require(
            table in setOf(
                "customers",
                "vehicles",
                "work_orders",
                "parts"
            )
        )

        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM $table",
            null
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

    // ---------- إضافة عميل ----------

    fun addCustomer(
        name: String,
        phone: String,
        alt: String,
        address: String
    ): Long {
        check(hasPermission("customers", "add")) {
            "ليس لديك صلاحية إضافة عميل"
        }

        val cleanPhone = normalizePhone(phone)

        require(name.trim().isNotBlank()) {
            "يرجى إدخال اسم العميل"
        }

        require(cleanPhone.isNotBlank()) {
            "يرجى إدخال رقم هاتف صحيح"
        }

        val values = ContentValues().apply {
            put("name", name.trim())
            put("phone", cleanPhone)
            put("alternate_phones", alt.trim())
            put("address", address.trim())
        }

        return writableDatabase.insertOrThrow(
            "customers",
            null,
            values
        )
    }

    // ---------- إضافة سيارة ----------

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
        check(hasPermission("vehicles", "add")) {
            "ليس لديك صلاحية إضافة سيارة"
        }

        val cleanVin = normalizeVin(vin)

        require(cleanVin.length == 17) {
            "رقم الهيكل يجب أن يتكون من 17 خانة بالضبط"
        }

        require(!cleanVin.any { it in "IOQ" }) {
            "رقم الهيكل لا يسمح بالأحرف I و O و Q"
        }

        require(customerExists(customerId)) {
            "العميل المحدد غير موجود"
        }

        val reading = odometer.toLongOrNull() ?: 0L

        require(reading >= 0) {
            "قراءة العداد غير صحيحة"
        }

        val values = ContentValues().apply {
            put("customer_id", customerId)
            put("make", make.trim())
            put("model", model.trim())
            put("model_year", year.toIntOrNull())
            put("engine", engine.trim())
            put("plate", plate.trim())
            put("vin", cleanVin)
            put("current_odometer", reading)
        }

        return writableDatabase.insertOrThrow(
            "vehicles",
            null,
            values
        )
    }

    // ---------- إضافة عميل وسيارته في عملية واحدة ----------

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
        vehicleNotes: String = ""
    ): Pair<Long, Long>? {
        if (!hasPermission("customers", "add") ||
            !hasPermission("vehicles", "add")
        ) {
            return null
        }

        val cleanName = name.trim()
        val cleanPhone = normalizePhone(phone)
        val cleanVin = normalizeVin(vin)
        val reading = odometer.toLongOrNull() ?: 0L

        if (cleanName.isBlank() ||
            cleanPhone.isBlank() ||
            cleanVin.length != 17 ||
            cleanVin.any { it in "IOQ" } ||
            reading < 0
        ) {
            return null
        }

        val db = writableDatabase
        var result: Pair<Long, Long>? = null

        try {
            db.beginTransaction()

            val customerValues = ContentValues().apply {
                put("name", cleanName)
                put("phone", cleanPhone)
                put("alternate_phones", alt.trim())
                put("address", address.trim())
                put("notes", customerNotes.trim())
            }

            val customerId = db.insertOrThrow(
                "customers",
                null,
                customerValues
            )

            val vehicleValues = ContentValues().apply {
                put("customer_id", customerId)
                put("make", make.trim())
                put("model", model.trim())
                put("model_year", year.toIntOrNull())
                put("engine", engine.trim())
                put("plate", plate.trim())
                put("vin", cleanVin)
                put("current_odometer", reading)
                put("notes", vehicleNotes.trim())
            }

            val vehicleId = db.insertOrThrow(
                "vehicles",
                null,
                vehicleValues
            )

            db.setTransactionSuccessful()
            result = customerId to vehicleId
        } catch (_: Exception) {
            result = null
        } finally {
            if (db.inTransaction()) {
                db.endTransaction()
            }
        }

        return result
    }

    private fun customerExists(customerId: Long): Boolean {
        readableDatabase.rawQuery(
            "SELECT id FROM customers WHERE id=? LIMIT 1",
            arrayOf(customerId.toString())
        ).use {
            return it.moveToFirst()
        }
    }

    // ---------- إضافة أمر إصلاح ----------

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
        check(hasPermission("work_orders", "add")) {
            "ليس لديك صلاحية إضافة أمر إصلاح"
        }

        val reading = odometer.toLongOrNull() ?: 0L

        require(reading >= 0) {
            "قراءة العداد غير صحيحة"
        }

        val labor = laborCost.trim()
            .ifBlank { "0" }
            .toDoubleOrNull()
            ?: throw IllegalArgumentException(
                "قيمة أجور العمل غير صحيحة"
            )

        val parts = partsCost.trim()
            .ifBlank { "0" }
            .toDoubleOrNull()
            ?: throw IllegalArgumentException(
                "قيمة قطع الغيار غير صحيحة"
            )

        require(labor.isFinite() && labor >= 0) {
            "أجور العمل يجب أن تكون رقماً موجباً"
        }

        require(parts.isFinite() && parts >= 0) {
            "تكلفة القطع يجب أن تكون رقماً موجباً"
        }

        val cleanStatus = status.trim().ifBlank { "مفتوح" }

        require(
            cleanStatus in setOf(
                "مفتوح",
                "قيد العمل",
                "مكتمل"
            )
        ) {
            "حالة أمر الإصلاح غير صحيحة"
        }

        val database = writableDatabase

        database.beginTransaction()
        try {
            val vehicleCustomerId = database.rawQuery(
                "SELECT customer_id FROM vehicles WHERE id=? LIMIT 1",
                arrayOf(vehicleId.toString())
            ).use { cursor ->
                if (!cursor.moveToFirst()) {
                    throw IllegalArgumentException(
                        "السيارة المحددة غير موجودة"
                    )
                }
                cursor.getLong(0)
            }

            require(customerExists(customerId)) {
                "العميل المحدد غير موجود"
            }

            require(vehicleCustomerId == customerId) {
                "السيارة غير مرتبطة بالعميل المحدد. تحقق من الملكية أولاً"
            }

            val values = ContentValues().apply {
                put("customer_id", customerId)
                put("vehicle_id", vehicleId)
                put("odometer_at_visit", reading)
                put("complaint", complaint.trim())
                put("diagnosis", diagnosis.trim())
                put("repair_notes", repairNotes.trim())
                put("status", cleanStatus)
                put("labor_cost", labor)
                put("parts_cost", parts)
            }

            database.insertOrThrow(
                "work_orders",
                null,
                values
            )

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

    // ---------- إضافة قطعة غيار ----------

    fun addPart(
        number: String,
        name: String,
        qty: String,
        unit: String,
        buy: String,
        sell: String
    ) {
        check(hasPermission("parts", "add")) {
            "ليس لديك صلاحية إضافة قطعة غيار"
        }

        val quantity = qty.toDoubleOrNull() ?: 0.0
        val purchasePrice = buy.toDoubleOrNull() ?: 0.0
        val salePrice = sell.toDoubleOrNull() ?: 0.0

        require(name.trim().isNotBlank()) {
            "يرجى إدخال اسم القطعة"
        }

        require(
            quantity.isFinite() &&
                purchasePrice.isFinite() &&
                salePrice.isFinite() &&
                quantity >= 0 &&
                purchasePrice >= 0 &&
                salePrice >= 0
        ) {
            "تحقق من الكمية والأسعار"
        }

        val values = ContentValues().apply {
            put("part_number", number.trim())
            put("name", name.trim())
            put("quantity", quantity)
            put("unit", unit.trim().ifBlank { "قطعة" })
            put("purchase_price", purchasePrice)
            put("sale_price", salePrice)
        }

        writableDatabase.insertOrThrow(
            "parts",
            null,
            values
        )
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
                    year = if (c.isNull(4)) {
                        ""
                    } else {
                        c.getInt(4).toString()
                    },
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
       // ---------- البحث في الاستقبال ----------

    fun searchReception(
        query: String
    ): List<ReceptionSearchResult> {
        val clean = query.trim()
        if (clean.isBlank()) return emptyList()

        val results = mutableListOf<ReceptionSearchResult>()

        // البحث عن العملاء برقم الهاتف
        val phoneQuery = normalizePhone(clean)

        if (phoneQuery.isNotBlank()) {
            readableDatabase.rawQuery(
                """
                SELECT id, name, phone
                FROM customers
                WHERE phone LIKE ?
                ORDER BY id DESC
                LIMIT 20
                """.trimIndent(),
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

        // البحث عن السيارات برقم الهيكل أو اللوحة
        val vinQuery = normalizeVin(clean)

        readableDatabase.rawQuery(
            """
            SELECT id, make, model, vin, plate
            FROM vehicles
            WHERE vin LIKE ?
               OR plate LIKE ? COLLATE NOCASE
            ORDER BY id DESC
            LIMIT 30
            """.trimIndent(),
            arrayOf("%$vinQuery%", "%$clean%")
        ).use { c ->
            while (c.moveToNext()) {
                val make = c.getString(1) ?: ""
                val model = c.getString(2) ?: ""
                val vin = c.getString(3) ?: ""
                val plate = c.getString(4) ?: ""

                results.add(
                    ReceptionSearchResult(
                        type = "vehicle",
                        id = c.getLong(0),
                        title = "$make $model".trim(),
                        subtitle = "سيارة — اللوحة: $plate — VIN: $vin"
                    )
                )
            }
        }

        return results
    }

    // ---------- نتيجة بحث استقبال تفصيلية ----------

    data class ReceptionMatch(
        val customer: CustomerProfile?,
        val vehicle: VehicleProfile?,
        val vehicleOwnerId: Long?,
        val vehicleOwnerName: String?,
        val vehicleOwnerPhone: String?
    ) {
        val customerExists: Boolean
            get() = customer != null

        val vehicleExists: Boolean
            get() = vehicle != null

        val ownershipMatches: Boolean
            get() = customer != null &&
                vehicle != null &&
                customer.id == vehicle.customerId
    }

    fun searchReceptionDetailed(
        query: String
    ): List<ReceptionMatch> {
        val clean = query.trim()
        if (clean.isBlank()) return emptyList()

        val matches = mutableListOf<ReceptionMatch>()
        val db = readableDatabase

        val phoneQuery = normalizePhone(clean)
        val vinQuery = normalizeVin(clean)

        val customerIds = linkedSetOf<Long>()
        val vehicleIds = linkedSetOf<Long>()

        // البحث عن العميل برقم الهاتف
        if (phoneQuery.isNotBlank()) {
            db.rawQuery(
                """
                SELECT id
                FROM customers
                WHERE phone LIKE ?
                ORDER BY id DESC
                LIMIT 20
                """.trimIndent(),
                arrayOf("%$phoneQuery%")
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    customerIds.add(cursor.getLong(0))
                }
            }
        }

        // البحث عن السيارة برقم الهيكل أو اللوحة
        db.rawQuery(
            """
            SELECT id
            FROM vehicles
            WHERE vin LIKE ?
               OR plate LIKE ? COLLATE NOCASE
            ORDER BY id DESC
            LIMIT 30
            """.trimIndent(),
            arrayOf("%$vinQuery%", "%$clean%")
        ).use { cursor ->
            while (cursor.moveToNext()) {
                vehicleIds.add(cursor.getLong(0))
            }
        }

        // إذا عثرنا على سيارة، نضيف مالكها الحالي إلى نتائج العملاء
        for (vehicleId in vehicleIds) {
            db.rawQuery(
                """
                SELECT customer_id
                FROM vehicles
                WHERE id=?
                LIMIT 1
                """.trimIndent(),
                arrayOf(vehicleId.toString())
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    customerIds.add(cursor.getLong(0))
                }
            }
        }

        // تكوين نتائج العملاء دون سيارات
        for (customerId in customerIds) {
            val customer = getCustomerProfile(customerId)
                ?: continue

            val customerVehicles = getCustomerVehicles(customerId)

            if (customerVehicles.isEmpty()) {
                matches.add(
                    ReceptionMatch(
                        customer = customer,
                        vehicle = null,
                        vehicleOwnerId = null,
                        vehicleOwnerName = null,
                        vehicleOwnerPhone = null
                    )
                )
            } else {
                for (vehicle in customerVehicles) {
                    matches.add(
                        ReceptionMatch(
                            customer = customer,
                            vehicle = vehicle,
                            vehicleOwnerId = vehicle.customerId,
                            vehicleOwnerName = vehicle.ownerName,
                            vehicleOwnerPhone = vehicle.ownerPhone
                        )
                    )
                }
            }
        }

        // إضافة السيارات التي لم يظهر مالكها ضمن نتائج العملاء
        for (vehicleId in vehicleIds) {
            val vehicle = getVehicleProfile(vehicleId)
                ?: continue

            val alreadyAdded = matches.any {
                it.vehicle?.id == vehicleId
            }

            if (!alreadyAdded) {
                matches.add(
                    ReceptionMatch(
                        customer = null,
                        vehicle = vehicle,
                        vehicleOwnerId = vehicle.customerId,
                        vehicleOwnerName = vehicle.ownerName,
                        vehicleOwnerPhone = vehicle.ownerPhone
                    )
                )
            }
        }

        return matches.distinctBy {
            Pair(it.customer?.id, it.vehicle?.id)
        }
    }

    // ---------- تفاصيل ملف السيارة ----------

    fun getVehicleProfile(
        vehicleId: Long
    ): VehicleProfile? {
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
                year = if (c.isNull(4)) {
                    ""
                } else {
                    c.getInt(4).toString()
                },
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

    // ---------- سجل ملاك السيارة ----------

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
        if (!hasPermission("vehicles", "edit")) {
            return false
        }

        if (reason.trim().isBlank()) {
            return false
        }

        val database = writableDatabase
        var success = false

        try {
            database.beginTransaction()

            val oldCustomerId = database.rawQuery(
                """
                SELECT customer_id
                FROM vehicles
                WHERE id=?
                LIMIT 1
                """.trimIndent(),
                arrayOf(vehicleId.toString())
            ).use { c ->
                if (c.moveToFirst()) c.getLong(0) else null
            }

            if (oldCustomerId == null ||
                oldCustomerId == newCustomerId
            ) {
                return false
            }

            val customerExists = database.rawQuery(
                "SELECT id FROM customers WHERE id=? LIMIT 1",
                arrayOf(newCustomerId.toString())
            ).use {
                it.moveToFirst()
            }

            if (!customerExists) {
                return false
            }

            val history = ContentValues().apply {
                put("vehicle_id", vehicleId)
                put("old_customer_id", oldCustomerId)
                put("new_customer_id", newCustomerId)
                put("reason", reason.trim())
            }

            database.insertOrThrow(
                "vehicle_owners",
                null,
                history
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

            if (changed != 1) {
                return false
            }

            database.setTransactionSuccessful()
            success = true
        } catch (_: Exception) {
            success = false
        } finally {
            if (database.inTransaction()) {
                database.endTransaction()
            }
        }

        return success
    }

    // ---------- ملف العميل ----------

    fun getCustomerProfile(
        customerId: Long
    ): CustomerProfile? {
        val sql = """
            SELECT id, name, phone, alternate_phones, address, notes
            FROM customers
            WHERE id=?
            LIMIT 1
        """.trimIndent()

        readableDatabase.rawQuery(
            sql,
            arrayOf(customerId.toString())
        ).use { c ->
            if (!c.moveToFirst()) return null

            return CustomerProfile(
                id = c.getLong(0),
                name = c.getString(1) ?: "",
                phone = c.getString(2) ?: "",
                alternatePhones = c.getString(3) ?: "",
                address = c.getString(4) ?: "",
                notes = c.getString(5) ?: ""
            )
        }
    }

    // ---------- سيارات العميل ----------

    fun getCustomerVehicles(
        customerId: Long
    ): List<VehicleProfile> {
        val out = mutableListOf<VehicleProfile>()

        val sql = """
            SELECT
                v.id, v.customer_id, v.make, v.model,
                v.model_year, v.engine, v.plate, v.vin,
                v.current_odometer, v.notes,
                c.name, c.phone
            FROM vehicles v
            LEFT JOIN customers c ON c.id = v.customer_id
            WHERE v.customer_id=?
            ORDER BY v.id DESC
        """.trimIndent()

        readableDatabase.rawQuery(
            sql,
            arrayOf(customerId.toString())
        ).use { c ->
            while (c.moveToNext()) {
                out += VehicleProfile(
                    id = c.getLong(0),
                    customerId = c.getLong(1),
                    make = c.getString(2) ?: "",
                    model = c.getString(3) ?: "",
                    year = if (c.isNull(4)) {
                        ""
                    } else {
                        c.getInt(4).toString()
                    },
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

    // ---------- سجل زيارات العميل ----------

    fun getCustomerWorkOrders(
        customerId: Long
    ): List<CustomerWorkOrderRecord> {
        val out = mutableListOf<CustomerWorkOrderRecord>()

        val sql = """
            SELECT
                w.id,
                w.vehicle_id,
                v.make || ' ' || v.model,
                w.complaint,
                w.diagnosis,
                w.repair_notes,
                w.status,
                w.labor_cost,
                w.parts_cost,
                w.created_at
            FROM work_orders w
            LEFT JOIN vehicles v ON v.id = w.vehicle_id
            WHERE w.customer_id=?
            ORDER BY w.id DESC
        """.trimIndent()

        readableDatabase.rawQuery(
            sql,
            arrayOf(customerId.toString())
        ).use { c ->
            while (c.moveToNext()) {
                out += CustomerWorkOrderRecord(
                    id = c.getLong(0),
                    vehicleId = c.getLong(1),
                    vehicleName = c.getString(2) ?: "",
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

    data class ClickableRecord(
        val id: Long,
        val title: String,
        val subtitle: String,
        val vehicleId: Long = 0L
    )

    fun recentClickableRecords(
        table: String
    ): List<ClickableRecord> {
        val sql = when (table) {
            "customers" ->
                """
                SELECT id, name, phone
                FROM customers
                ORDER BY id DESC
                LIMIT 50
                """

            "vehicles" ->
                """
                SELECT id, make || ' ' || model,
                       'VIN: ' || vin || ' — اللوحة: ' || plate
                FROM vehicles
                ORDER BY id DESC
                LIMIT 50
                """

            "work_orders" ->
                """
                SELECT id, 'أمر إصلاح #' || id,
                       complaint, vehicle_id
                FROM work_orders
                ORDER BY id DESC
                LIMIT 50
                """

            else -> return emptyList()
        }

        val records = mutableListOf<ClickableRecord>()

        readableDatabase.rawQuery(sql, null).use { c ->
            while (c.moveToNext()) {
                records += ClickableRecord(
                    id = c.getLong(0),
                    title = c.getString(1) ?: "",
                    subtitle = c.getString(2) ?: "",
                    vehicleId = if (table == "work_orders") {
                        c.getLong(3)
                    } else {
                        c.getLong(0)
                    }
                )
            }
        }

        return records
    }

    fun recentRows(
        table: String
    ): List<String> {
        val sql = when (table) {
            "customers" ->
                """
                SELECT name, phone
                FROM customers
                ORDER BY id DESC
                LIMIT 30
                """

            "vehicles" ->
                """
                SELECT make || ' ' || model, vin
                FROM vehicles
                ORDER BY id DESC
                LIMIT 30
                """

            "work_orders" ->
                """
                SELECT 'أمر #' || id, complaint
                FROM work_orders
                ORDER BY id DESC
                LIMIT 30
                """

            "parts" ->
                """
                SELECT name, 'الكمية: ' || quantity || ' ' || unit
                FROM parts
                ORDER BY id DESC
                LIMIT 30
                """

            else -> return emptyList()
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

    fun findCustomerByPhone(
        phone: String
    ): String? {
        val clean = normalizePhone(phone)
        if (clean.isBlank()) return null

        readableDatabase.rawQuery(
            "SELECT name FROM customers WHERE phone=? LIMIT 1",
            arrayOf(clean)
        ).use { c ->
            return if (c.moveToFirst()) {
                c.getString(0)
            } else {
                null
            }
        }
    }

    fun findVehicleByVin(
        vin: String
    ): String? {
        val clean = normalizeVin(vin)
        if (clean.isBlank()) return null

        readableDatabase.rawQuery(
            """
            SELECT make, model, plate
            FROM vehicles
            WHERE vin=?
            LIMIT 1
            """.trimIndent(),
            arrayOf(clean)
        ).use { c ->
            return if (c.moveToFirst()) {
                "${c.getString(0)} ${c.getString(1)} — اللوحة: ${c.getString(2)}"
            } else {
                null
            }
        }
    }

    fun findVehicleByPlate(
        plate: String
    ): String? {
        val clean = plate.trim()
        if (clean.isBlank()) return null

        readableDatabase.rawQuery(
            """
            SELECT make, model, vin
            FROM vehicles
            WHERE TRIM(plate)=? COLLATE NOCASE
            LIMIT 1
            """.trimIndent(),
            arrayOf(clean)
        ).use { c ->
            return if (c.moveToFirst()) {
                "${c.getString(0)} ${c.getString(1)} — VIN: ${c.getString(2)}"
            } else {
                null
            }
        }
    }

    fun findPartByNumber(
        number: String
    ): String? {
        val clean = number.trim()
        if (clean.isBlank()) return null

        readableDatabase.rawQuery(
            """
            SELECT name
            FROM parts
            WHERE TRIM(part_number)=? COLLATE NOCASE
            LIMIT 1
            """.trimIndent(),
            arrayOf(clean)
        ).use { c ->
            return if (c.moveToFirst()) {
                c.getString(0)
            } else {
                null
            }
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

    // ---------- أدوات تطبيع البيانات ----------

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
