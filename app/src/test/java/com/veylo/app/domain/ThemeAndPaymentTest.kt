package com.veylo.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThemeAndPaymentTest {

    @Test fun detectsTripStyleFromDestination() {
        assertEquals(TripTheme.HILLS, TripTheme.detect("Darjeeling, West Bengal", "Trip", false))
        assertEquals(TripTheme.HILLS, TripTheme.detect("Manali", "", false))
        assertEquals(TripTheme.BEACH, TripTheme.detect("Goa", "Friends trip", false))
        assertEquals(TripTheme.BEACH, TripTheme.detect("Havelock Island, Andaman", "", false))
        assertEquals(TripTheme.BUSINESS, TripTheme.detect("Bangalore", "Client meeting", false))
        assertEquals(TripTheme.BUSINESS, TripTheme.detect("Goa", "Sales conference", false)) // business wins
        assertEquals(TripTheme.VACATION, TripTheme.detect("Paris", "Honeymoon", false))
        assertEquals(TripTheme.CAFE, TripTheme.detect("Blue Tokai", "Weekend", true))
        // "sea" must not match inside other words
        assertEquals(TripTheme.VACATION, TripTheme.detect("Seattle", "", false))
    }

    @Test fun parsesUpiAppResponse() {
        val ok = PaymentParser.parseUpiResponse("txnId=AXI9f8e&responseCode=00&Status=SUCCESS&txnRef=VEYLO1&ApprovalRefNo=412345678901")!!
        assertEquals(PayStatus.SUCCESS, ok.status)
        assertEquals("412345678901", ok.txnId)
        assertEquals(PayStatus.PENDING, PaymentParser.parseUpiResponse("status=SUBMITTED&txnId=X1")!!.status)
        assertEquals(PayStatus.FAILED, PaymentParser.parseUpiResponse("Status=FAILURE&responseCode=ZD")!!.status)
        assertNull(PaymentParser.parseUpiResponse(null))
        assertNull(PaymentParser.parseUpiResponse(""))
    }

    @Test fun readsGooglePayStyleScreenshot() {
        val text = """
            ₹500
            Paid to Rahul Sharma
            Completed
            4 Nov 2026, 9:41 pm
            UPI transaction ID
            431298765432
            To: rahul@okaxis
            Google transaction ID
            CICAgKCb7pXuMQ
        """.trimIndent()
        val p = PaymentParser.parseScreenshotText(text)
        assertEquals(PayStatus.SUCCESS, p.status)
        assertEquals("431298765432", p.txnId)
        assertEquals(50000L, p.amount)
    }

    @Test fun readsPhonePeStyleScreenshot() {
        val text = "Transaction Successful\n₹ 1,250.50\nPaid to PRIYA\nUTR: 412233445566\nTransaction ID T2611041234567890"
        val p = PaymentParser.parseScreenshotText(text)
        assertEquals(PayStatus.SUCCESS, p.status)
        assertEquals("412233445566", p.txnId)
        assertEquals(125050L, p.amount)
    }

    @Test fun readsAmountWhenRupeeSignIsMisread() {
        val text = "%100\nPaid to Rahul\nCompleted\n6 Nov 2026, 10:20 am\nUPI transaction ID\n431298765432"
        val p = PaymentParser.parseScreenshotText(text)
        assertEquals(10000L, p.amount)
        assertEquals("431298765432", p.txnId)
        // A bare amount line without any symbol also works; the 12-digit id is never taken as the amount.
        assertEquals(25000L, PaymentParser.parseScreenshotText("250\nPaid\nUTR 412233445566").amount)
        assertNull(PaymentParser.parseScreenshotText("Paid\n412233445566").amount)
    }

    @Test fun detectsFailedAndPending() {
        assertEquals(PayStatus.FAILED, PaymentParser.parseScreenshotText("Payment failed\n₹300\nRef no 123456789012").status)
        assertEquals(PayStatus.PENDING, PaymentParser.parseScreenshotText("Payment pending\nRs. 300").status)
        assertEquals(PayStatus.UNKNOWN, PaymentParser.parseScreenshotText("hello world").status)
    }
}
