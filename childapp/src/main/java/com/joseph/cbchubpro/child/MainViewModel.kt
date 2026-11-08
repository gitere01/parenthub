package com.joseph.cbchubpro.child

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.joseph.cbchubpro.child.data.local.ChildDataRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val childDataRepository = ChildDataRepository(application)
    
    private val _isPaired = MutableStateFlow(childDataRepository.isPaired())
    val isPaired = _isPaired.asStateFlow()

    fun updatePairingStatus() {
        _isPaired.value = childDataRepository.isPaired()
    }

    fun isPairedSync(): Boolean {
        return childDataRepository.isPaired()
    }

    fun getChildName(): String? {
        return childDataRepository.getChildName()
    }
}
