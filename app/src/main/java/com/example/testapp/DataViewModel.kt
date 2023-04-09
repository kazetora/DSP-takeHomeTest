package com.example.testapp

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class DataViewModel : ViewModel() {
    private val _extractedText = MutableLiveData<String>()
    val extractedText: LiveData<String> = _extractedText

    private val _distance = MutableLiveData<Float>()
    val distance: LiveData<Float> = _distance

    private val _estimatedTime = MutableLiveData<Int>()
    val estimatedTime: LiveData<Int> = _estimatedTime

    fun updateExtractedText(text: String) {
        _extractedText.value = text
    }

    fun updateDistance(distance: Float) {
        _distance.value = distance
    }

    fun updateEstimatedTime(time: Int) {
        _estimatedTime.value = time
    }
}