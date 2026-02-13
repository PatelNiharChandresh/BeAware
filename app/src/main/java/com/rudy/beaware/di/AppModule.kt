package com.rudy.beaware.di

import android.content.Context
import androidx.room.Room
import com.rudy.beaware.data.datastore.PrefsDataStore
import com.rudy.beaware.data.datastore.dataStore
import com.rudy.beaware.data.local.BeAwareDatabase
import com.rudy.beaware.data.local.dao.UsageSessionDao
import com.rudy.beaware.data.repository.AppRepository
import com.rudy.beaware.data.repository.AppRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindRepository(impl: AppRepositoryImpl): AppRepository

    companion object {

        @Provides
        @Singleton
        fun provideDatabase(@ApplicationContext context: Context): BeAwareDatabase {
            return Room.databaseBuilder(
                context,
                BeAwareDatabase::class.java,
                "beaware_db"
            ).build()
        }

        @Provides
        fun provideUsageSessionDao(database: BeAwareDatabase): UsageSessionDao {
            return database.usageSessionDao()
        }

        @Provides
        @Singleton
        fun providePrefsDataStore(@ApplicationContext context: Context): PrefsDataStore {
            return PrefsDataStore(context.dataStore)
        }
    }
}
