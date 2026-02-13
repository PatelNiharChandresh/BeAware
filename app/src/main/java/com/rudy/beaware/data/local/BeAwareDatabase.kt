package com.rudy.beaware.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.rudy.beaware.data.local.dao.UsageSessionDao
import com.rudy.beaware.data.local.entity.UsageSessionEntity

@Database(entities = [UsageSessionEntity::class], version = 1)
abstract class BeAwareDatabase : RoomDatabase() {
    abstract fun usageSessionDao(): UsageSessionDao
}
