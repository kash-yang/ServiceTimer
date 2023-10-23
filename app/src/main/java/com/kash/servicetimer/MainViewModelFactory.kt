package com.kash.servicetimer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.kash.servicetimer.service.ITimerServiceWrapper

@Suppress("UNCHECKED_CAST")
class MainViewModelFactory(private val serviceWrapper: ITimerServiceWrapper<*>) :
    ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>) =
        with(modelClass) {
            when {
                isAssignableFrom(MainViewModel::class.java) ->
                    MainViewModel(serviceWrapper)

                else ->
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
        } as T
}