package com.sytrack.ui.viewmodels

import androidx.lifecycle.ViewModel
import com.sytrack.repositories.MainRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class RecordViewModel @Inject constructor(val repository: MainRepository) : ViewModel() {

}