package com.example.cricketscorer.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cricketscorer.data.CricketRepository
import com.example.cricketscorer.data.MatchEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(private val repository: CricketRepository) : ViewModel() {

    private val _matches = MutableStateFlow<List<MatchEntity>>(emptyList())
    val matches: StateFlow<List<MatchEntity>> = _matches.asStateFlow()

    init {
        observeMatches()
    }

    private fun observeMatches() {
        viewModelScope.launch {
            repository.observeAllMatches().collect { list ->
                _matches.value = list
            }
        }
    }
}
