package org.avmedia.gshockGoogleSync.data.steps

import android.content.Context
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.avmedia.gshockapi.model.StepCounterData
import javax.inject.Inject
import javax.inject.Singleton

private data class StoredStepCounter(
    val capturedAtEpochMillis: Long,
    val dayOfWeek: Int,
    val month: Int,
    val dayOfMonth: Int,
    val hourlySteps: List<Int?>,
    val dailyHistory: List<Int?>,
    val currentDaySteps: Int?,
) {
    fun toApiModel() = StepCounterData(
        dayOfWeek,
        month,
        dayOfMonth,
        hourlySteps,
        dailyHistory,
        currentDaySteps,
    )
}

/** Persists the latest watch step snapshot, including background synchronizations. */
@Singleton
class StepCounterStore @Inject constructor(
    @param:ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences("step_counter", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val _steps = MutableStateFlow(load())

    val steps: StateFlow<StepCounterData> = _steps.asStateFlow()

    fun save(data: StepCounterData) {
        val stored = StoredStepCounter(
            capturedAtEpochMillis = System.currentTimeMillis(),
            dayOfWeek = data.dayOfWeek,
            month = data.month,
            dayOfMonth = data.dayOfMonth,
            hourlySteps = data.hourlySteps,
            dailyHistory = data.dailyHistory,
            currentDaySteps = data.currentDaySteps,
        )
        check(preferences.edit().putString("latest_v1", gson.toJson(stored)).commit()) {
            "Unable to persist step-counter data"
        }
        _steps.value = data
    }

    private fun load(): StepCounterData {
        val json = preferences.getString("latest_v1", null) ?: return StepCounterData.unavailable()
        return runCatching { gson.fromJson(json, StoredStepCounter::class.java).toApiModel() }
            .getOrElse { StepCounterData.unavailable() }
    }
}
