package com.sytrack.di

import android.content.Context
import androidx.room.Room
import com.sytrack.db.RecordDatabase
import com.sytrack.utils.Constants.DATABASE_NAME
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext app: Context) =
        Room.databaseBuilder(
            app,
            RecordDatabase::class.java,
            DATABASE_NAME
        ).build()

    @Provides
    fun provideDao(database: RecordDatabase) = database.getRecordDAO()

}