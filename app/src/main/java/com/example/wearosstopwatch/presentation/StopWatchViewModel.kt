@file:OptIn(ExperimentalCoroutinesApi::class)

package com.example.wearosstopwatch.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class StopWatchViewModel: ViewModel() {

    private val _elapsedTime = MutableStateFlow(0L) // Saves our elapsed time (how much time has passed for current run)
    private val _timerState = MutableStateFlow(TimerState.RESET) // Set the default timer state to RESET
    val timerState = _timerState.asStateFlow() // Expose the immutable timerState

    // Format text for the stop watch
    private val formatter = DateTimeFormatter.ofPattern("HH:mm:ss:SSS")

    // Whenever the elapsedTime state flow changes it causes an emission of the stopWatchText
    // and updates the UI with our new text
    val stopWatchText = _elapsedTime
        .map { millis ->
            LocalTime.ofNanoOfDay(millis * 1_000_000).format(formatter)
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            "00:00:00:000"
        )

    init {
        // This flow is dependant on _timerState
        // Ex. if our state changes from PAUSE to RUNNING then getTimerFlow will run as intended
        // But if our app is Paused then we will return an empty Flow because we will not enter the while loop
        _timerState
            .flatMapLatest { timerState ->
                getTimerFlow(
                    isRunnnig = timerState == TimerState.RUNNING
                )
            } // From this point we are getting emissions (timeDiff) from getTimerFlow of type Flow<Long>
            .onEach { timeDiff ->
                // _elapsedTime triggers by calling update which automatically triggers an emission of our stopWatchText
                _elapsedTime.update { it + timeDiff } // Every 10 ms add our previous 10 ms and keep total
            }
            .launchIn(viewModelScope) // Run this code in ViewModelScope
    }

    // The purpose of this function is to keep time accurately in case the delay function is off at all
    private fun getTimerFlow(isRunnnig: Boolean): Flow<Long> {
        //Keeps a loop active as long as the timer is running and consistently emit the time differences
        return flow {
            var startMillis = System.currentTimeMillis() // Save the current time in milliseconds
            while (isRunnnig) {
                val currentMillis = System.currentTimeMillis()
                val timeDiff = if (currentMillis > startMillis) {
                    // Calculate the time difference between the current time and the started time
                    currentMillis - startMillis
                } else 0L
                emit(timeDiff)
                startMillis = System.currentTimeMillis() // Assign current time so calculating can be repeated
                delay(10L)
            }
        }
    }

}