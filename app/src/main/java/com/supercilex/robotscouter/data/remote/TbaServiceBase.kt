package com.supercilex.robotscouter.data.remote

import com.google.android.gms.tasks.Task
import com.supercilex.robotscouter.R
import com.supercilex.robotscouter.RobotScouter
import com.supercilex.robotscouter.data.model.Team
import com.supercilex.robotscouter.util.AsyncTaskExecutor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.Calendar
import java.util.concurrent.Callable

abstract class TbaServiceBase<out T>(team: Team, clazz: Class<T>) : Callable<Team?> {
    protected val team: Team = team.copy()
    protected val api: T = TBA_RETROFIT.create(clazz)

    protected val tbaApiKey: String = RobotScouter.getString(R.string.tba_api_key)

    protected val year: Int get() = Calendar.getInstance().get(Calendar.YEAR)

    protected fun cannotContinue(response: Response<*>): Boolean = when {
        response.isSuccessful -> false
        response.code() == ERROR_404 -> true
        else -> error(response.toString())
    }

    protected companion object {
        private val TBA_RETROFIT: Retrofit = Retrofit.Builder()
                .baseUrl("https://www.thebluealliance.com/api/v3/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()

        private const val ERROR_404 = 404

        fun executeAsync(service: TbaServiceBase<*>): Task<Team?> =
                AsyncTaskExecutor.execute(service)
    }
}
