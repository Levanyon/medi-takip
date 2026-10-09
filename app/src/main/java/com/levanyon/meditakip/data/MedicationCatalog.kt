package com.levanyon.meditakip.data

import android.content.Context
import com.levanyon.meditakip.domain.Gtin
import org.json.JSONObject
import java.security.MessageDigest

data class CatalogProduct(val gtin: String, val name: String)
sealed interface CatalogLookup {
    data class Found(val product: CatalogProduct) : CatalogLookup
    data object Unknown : CatalogLookup
    data object Unavailable : CatalogLookup
}
fun interface MedicationCatalog { fun lookup(gtin: String): CatalogLookup }

/** Bundled community reference, never an authority for clinical instructions. No runtime network. */
class BundledMedicationCatalog(private val read: (String) -> ByteArray) : MedicationCatalog {
    constructor(context: Context) : this({ path -> context.assets.open("catalog/$path").use { it.readBytes() } })
    private val products: Map<String, CatalogProduct>? by lazy {
        runCatching {
            val bytes = read("medications.json")
            val manifest = JSONObject(read("manifest.json").toString(Charsets.UTF_8))
            val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            require(hash == manifest.getString("snapshotSha256"))
            val root = JSONObject(bytes.toString(Charsets.UTF_8))
            require(root.getString("version") == VERSION)
            val rows = root.getJSONArray("products")
            buildMap {
                for (i in 0 until rows.length()) {
                    val row = rows.getJSONObject(i)
                    val gtin = row.getString("gtin"); val name = row.getString("name")
                    require(Gtin.normalize(gtin) == gtin && name.isNotBlank() && gtin !in this)
                    put(gtin, CatalogProduct(gtin, name))
                }
                require(size == manifest.getInt("products"))
            }
        }.getOrNull()
    }
    override fun lookup(gtin: String): CatalogLookup {
        val code = Gtin.normalize(gtin) ?: return CatalogLookup.Unknown
        val index = products ?: return CatalogLookup.Unavailable
        return index[code]?.let(CatalogLookup::Found) ?: CatalogLookup.Unknown
    }
    companion object { const val VERSION = "6211e27aeb1c48f77a7d2c3c0858ecec60a8505b" }
}
