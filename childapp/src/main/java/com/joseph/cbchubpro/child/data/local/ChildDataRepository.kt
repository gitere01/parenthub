package com.joseph.cbchubpro.child.data.local

import android.content.Context
import com.joseph.cbchubpro.child.data.model.Child

class ChildDataRepository(context: Context) {

    private val sharedPreferences = context.getSharedPreferences("child_data", Context.MODE_PRIVATE)

    fun saveChildData(child: Child) {
        with(sharedPreferences.edit()) {
            putString("child_id", child.id)
            putString("child_name", child.name)
            putBoolean("is_paired", true)
            apply()
        }
    }

    fun getChildName(): String? {
        return sharedPreferences.getString("child_name", null)
    }

    fun isPaired(): Boolean {
        return sharedPreferences.getBoolean("is_paired", false)
    }

    fun setParentPassword(password: String) {
        sharedPreferences.edit().putString("parent_password", password).apply()
    }

    fun getParentPassword(): String? {
        return sharedPreferences.getString("parent_password", null)
    }

    fun clearData() {
        with(sharedPreferences.edit()) {
            clear()
            apply()
        }
    }
}
