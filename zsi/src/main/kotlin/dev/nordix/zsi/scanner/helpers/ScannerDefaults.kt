package dev.nordix.zsi.scanner.helpers

import android.content.Context
import android.util.Log
import dev.nordix.zsi.R
import org.xmlpull.v1.XmlPullParser

internal object ScannerDefaults {
    internal val scannerSdkParams = mapOf(
        765     to  0,
        687     to  4,
        588     to  2,
        8610    to  1,
        900     to  0,
        901     to  0,
        905     to  1,
    )

    fun getHashMapResource(c: Context, hashMapResId: Int): Map<String?, String?>? {
        var map: MutableMap<String?, String?>? = null
        val parser = c.resources.getXml(hashMapResId)
        var key: String? = null
        var value: String? = null
        try {
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_DOCUMENT -> Log.d("utils", "Start document")
                    XmlPullParser.START_TAG -> if (parser.name == "map") {
                        val isLinked = parser.getAttributeBooleanValue(null, "linked", false)
                        map = if (isLinked) LinkedHashMap() else HashMap()
                    } else if (parser.name == "entry") {
                        key = parser.getAttributeValue(null, "key")
                        if (null == key) {
                            parser.close()
                            return null
                        }
                    }
                    XmlPullParser.END_TAG -> if (parser.name == "entry") {
                        map!![key] = value
                        key = null
                        value = null
                    }
                    XmlPullParser.TEXT -> if (null != key) value = parser.text
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
        return map
    }

    fun getDefaultCodesParams(context: Context) : Map<Int, Int> {
        val defaultCodes = getHashMapResource(context, R.xml.scanner_defaults)?.map {
            (it.key?.toIntOrNull() ?: 0) to (it.value?.toIntOrNull() ?: 0)
        }?.toMap()
        return defaultCodes ?: emptyMap()
    }

}
