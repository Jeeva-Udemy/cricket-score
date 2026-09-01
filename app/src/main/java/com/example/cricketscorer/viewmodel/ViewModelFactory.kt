package com.example.cricketscorer.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.cricketscorer.data.CricketRepository

class ViewModelFactory(
    private val repository: CricketRepository,
    private val appContext: Context
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(HomeViewModel::class.java) ->
                HomeViewModel(repository, appContext) as T
            modelClass.isAssignableFrom(MatchSetupViewModel::class.java) ->
                MatchSetupViewModel(repository, appContext) as T
            modelClass.isAssignableFrom(ScoringViewModel::class.java) ->
                ScoringViewModel(repository, appContext) as T
            modelClass.isAssignableFrom(SquadViewModel::class.java) ->
                SquadViewModel(repository) as T
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
