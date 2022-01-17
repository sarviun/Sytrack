package com.sytrack.ui.fragments

import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.sytrack.R
import com.sytrack.ui.viewmodels.RecordListViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class RecordListFragment : Fragment(R.layout.fragment_record_list) {

    private val viewModel: RecordListViewModel by viewModels()
}