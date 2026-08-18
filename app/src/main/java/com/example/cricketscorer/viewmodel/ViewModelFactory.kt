package com.example.cricketscorer.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.cricketscorer.data.CricketRepository

class ViewModelFactory(private val repository: CricketRepository) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(MatchSetupViewModel::class.java) ->
                MatchSetupViewModel(repository) as T
            modelClass.isAssignableFrom(ScoringViewModel::class.java) ->
                ScoringViewModel(repository) as T
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
