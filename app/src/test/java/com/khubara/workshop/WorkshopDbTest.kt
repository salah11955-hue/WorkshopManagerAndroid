package com.khubara.workshop

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkshopDbTest {
    @Test
    fun phoneNormalizerSupportsArabicDigits() {
        assertEquals("0096650123456", WorkshopDb.normalizePhone("00966٥٠١٢٣٤٥٦"))
    }

    @Test
    fun vinNormalizerRemovesSeparatorsAndUppercases() {
        assertEquals("ABC123DEF456GHI789", WorkshopDb.normalizeVin("abc123-def456 ghi789"))
    }

    @Test
    fun plateNormalizerRemovesSeparatorsAndArabicDigits() {
        assertEquals("ABC1", WorkshopDb.normalizePlate("a-b c ١"))
    }

    @Test
    fun workOrderTotalsCannotBecomeNegative() {
        val order = WorkOrderRecord(
            id = 1,
            customerId = 1,
            customerName = "عميل",
            customerPhone = "0500000000",
            vehicleId = 1,
            vehicleName = "سيارة",
            vehiclePlate = "ABC1",
            odometer = 100,
            complaint = "",
            diagnosis = "",
            repairNotes = "",
            status = "مفتوح",
            priority = "عادية",
            technicianName = "",
            expectedDelivery = "",
            laborCost = 100.0,
            partsCost = 50.0,
            discount = 200.0,
            downPayment = 20.0,
            additionalPaid = 10.0,
            createdAt = ""
        )
        assertEquals(0.0, order.totalCost, 0.001)
        assertEquals(30.0, order.paidAmount, 0.001)
        assertEquals(0.0, order.remainingAmount, 0.001)
        assertEquals(100, order.paymentPercent)
    }
}
