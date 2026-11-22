
package com.smsguard.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class SmsHistoryItem(
    val id: Long,
    val sender: String,
    val message: String,
    val label: String,
    val timestamp: Long
)

object SmsHistoryRepository {

    private const val PREFS_NAME = "sms_guard_history"
    private const val HISTORY_KEY = "items"
    private const val MAX_ITEMS = 200

    @Synchronized
    fun save(context: Context, item: SmsHistoryItem) {

        val items = load(context).toMutableList()

        items.add(0, item)

        val limitedItems = items.take(MAX_ITEMS)
        val array = JSONArray()

        limitedItems.forEach { entry ->

            val json = JSONObject()

            json.put("id", entry.id)
            json.put("sender", entry.sender)
            json.put("message", entry.message)
            json.put("label", entry.label)
            json.put("timestamp", entry.timestamp)

            array.put(json)
        }

        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(HISTORY_KEY, array.toString())
            .apply()
    }

    @Synchronized
    fun load(context: Context): List<SmsHistoryItem> {

        val preferences = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val content = preferences.getString(HISTORY_KEY, "[]")
            ?: "[]"

        return try {

            val array = JSONArray(content)
            val items = mutableListOf<SmsHistoryItem>()

            for (i in 0 until array.length()) {

                val json = array.getJSONObject(i)

                items.add(
                    SmsHistoryItem(
                        id = json.getLong("id"),
                        sender = json.getString("sender"),
                        message = json.getString("message"),
                        label = json.getString("label"),
                        timestamp = json.getLong("timestamp")
                    )
                )
            }

            items

        } catch (e: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun clear(context: Context) {

        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(HISTORY_KEY)
            .apply()
    }
}
