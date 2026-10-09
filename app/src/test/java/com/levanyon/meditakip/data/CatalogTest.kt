package com.levanyon.meditakip.data

import android.app.Application
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class CatalogTest {
    @Test fun bundledExactLookupIsOfflineAndNormalized() {
        val catalog = BundledMedicationCatalog(RuntimeEnvironment.getApplication())
        val result = catalog.lookup("8699580090635") as CatalogLookup.Found
        assertEquals("DOLARIT 300 MG 10 FILM TABLET", result.product.name)
        assertEquals("08699580090635", result.product.gtin)
        assertEquals(result, catalog.lookup("08699580090635"))
    }
    @Test fun unknownAndInvalidNeverReturnApproximateNames() {
        val catalog = BundledMedicationCatalog(RuntimeEnvironment.getApplication())
        assertEquals(CatalogLookup.Unknown, catalog.lookup("1234567890128"))
        assertEquals(CatalogLookup.Unknown, catalog.lookup("DOLARIT"))
    }
    @Test fun unavailableAndCorruptAssetsFallBackWithoutCrash() {
        assertEquals(CatalogLookup.Unavailable, BundledMedicationCatalog { throw java.io.IOException() }.lookup("8699580090635"))
        assertEquals(CatalogLookup.Unavailable, BundledMedicationCatalog { "{}".toByteArray() }.lookup("8699580090635"))
    }
}
