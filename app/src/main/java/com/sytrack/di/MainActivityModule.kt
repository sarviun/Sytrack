package com.sytrack.di

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.qualifiers.ActivityContext

@InstallIn(ActivityComponent::class)
@Module
object MainActivityModule {

    @Provides
    fun provideSharedPreferences(@ActivityContext context: Context): SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
}