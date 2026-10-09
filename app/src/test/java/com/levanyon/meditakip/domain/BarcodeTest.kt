package com.levanyon.meditakip.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class BarcodeTest {
    private val gtin = "08699580090635"
    private val now = LocalDate.of(2026, 10, 9)
    private fun parse(raw: String) = MedicineBarcode.parse(raw, now)
    @Test fun eanAndLeadingZeroGtinAreSameProduct() {
        assertEquals(gtin, Gtin.normalize("8699580090635")); assertEquals(gtin, Gtin.normalize(gtin))
        assertEquals(ScannedProduct(gtin), parse("8699580090635"))
    }
    @Test fun nonzeroPackagingIndicatorIsNotStripped() {
        assertEquals("18699580090632", Gtin.normalize("18699580090632"))
        assertNotEquals(gtin, Gtin.normalize("18699580090632"))
    }
    @Test fun badChecksumsLengthsAndCharactersFail() {
        listOf("", "8699580090634", "00000000000000", "869958009063", "86995800906350", "８699580090635", " 8699580090635").forEach { assertNull(it, Gtin.normalize(it)) }
    }
    @Test fun turkishOrderUsesFnc1AfterSerial() {
        val scan = parse("]d201${gtin}21ABC123\u001d1727123110LOT42")!!
        assertEquals("ABC123", scan.serial); assertEquals("LOT42", scan.batch)
        assertEquals("2027-12-31", scan.expiry.toString())
    }
    @Test fun leadingSeparatorAndAlternativeOrderWork() {
        val scan = parse("\u001d01${gtin}1727123110LOT\u001d21SERIAL")!!
        assertEquals("LOT", scan.batch); assertEquals("SERIAL", scan.serial)
    }
    @Test fun humanReadableManualFormSupportsAnyOrder() {
        assertEquals(parse("01${gtin}1727123121SER\u001d10LOT"), parse("(21)SER(01)$gtin(10)LOT(17)271231"))
    }
    @Test fun variableFieldsNeverSplitOnEmbeddedAiDigits() {
        val scan = parse("01${gtin}21ABC1727123110XYZ")!!
        assertEquals("ABC1727123110XYZ", scan.serial); assertNull(scan.expiry); assertNull(scan.batch)
        assertNull(parse("01${gtin}21ABCDEFGHIJKLMNOPQRSTUVWXYZ17271231"))
    }
    @Test fun optionalFieldsCanBeAbsent() {
        assertEquals(ScannedProduct(gtin), parse("01$gtin"))
        assertEquals("SER", parse("01${gtin}21SER")!!.serial)
    }
    @Test fun duplicateUnknownAndTruncatedAisFail() {
        listOf("01${gtin}01$gtin", "01${gtin}99UNKNOWN", "01${gtin}17", "01${gtin}21", "01${gtin}21S\u001d", "21SER", "(01)$gtin(17)271231X").forEach { assertNull(it, parse(it)) }
    }
    @Test fun nonProductPayloadsFail() {
        listOf("https://example.com", "BEGIN:VCARD", "hello", "]d1$gtin", "12345").forEach { assertNull(parse(it)) }
    }
    @Test fun monthOnlyExpiryRetainsPrecisionAndMonthEndComparison() {
        val expiry = parse("01${gtin}17280200")!!.expiry!!
        assertNull(expiry.day); assertEquals("2028-02", expiry.toString())
        assertFalse(expiry.expired(LocalDate.of(2028, 2, 29))); assertTrue(expiry.expired(LocalDate.of(2028, 3, 1)))
        assertEquals(expiry, PackageExpiry.parseStored(expiry.toString()))
    }
    @Test fun invalidDatesNeverNormalize() {
        listOf("271332", "270229", "270431", "270000", "ABCDEF").forEach { assertNull(parse("01${gtin}17$it")) }
        assertNotNull(parse("01${gtin}17280229")); assertNull(PackageExpiry.parseStored("2027-02-29"))
    }
    @Test fun centuryWindowIsExplicit() {
        assertEquals("1999-12-31", PackageExpiry.parseGs1("991231", now).toString())
        assertEquals("2070-01-01", PackageExpiry.parseGs1("700101", now).toString())
    }
    @Test fun repeatedFramesAcceptedOnce() {
        val gate = ScanGate(); assertTrue(gate.accept()); repeat(100) { assertFalse(gate.accept()) }
        assertTrue(ScanGate().accept())
    }
    @Test fun cameraPermissionStatesAreIndependentOfOtherPermissions() {
        assertEquals(CameraAccess.UNAVAILABLE, CameraPolicy.state(false, false, false, false))
        assertEquals(CameraAccess.RATIONALE, CameraPolicy.state(true, false, false, false))
        assertEquals(CameraAccess.DENIED, CameraPolicy.state(true, false, true, true))
        assertEquals(CameraAccess.SETTINGS, CameraPolicy.state(true, false, true, false))
        assertEquals(CameraAccess.READY, CameraPolicy.state(true, true, true, false))
    }
}
