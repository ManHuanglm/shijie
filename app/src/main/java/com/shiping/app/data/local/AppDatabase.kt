package com.shiping.app.data.local

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.shiping.app.ShipingApp
import com.shiping.app.data.model.ApiSourceEntity
import com.shiping.app.data.model.ParseSourceEntity

@Database(entities = [ApiSourceEntity::class, ParseSourceEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun apiSourceDao(): ApiSourceDao
    abstract fun parseSourceDao(): ParseSourceDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    ShipingApp.context,
                    AppDatabase::class.java,
                    "shiping.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
