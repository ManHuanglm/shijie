package com.shiping.app.data.local

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.shiping.app.BuildConfig
import com.shiping.app.ShipingApp
import com.shiping.app.data.model.ApiSourceEntity
import com.shiping.app.data.model.DownloadEntity
import com.shiping.app.data.model.FavoriteEntity
import com.shiping.app.data.model.ParseSourceEntity
import com.shiping.app.data.model.PlayHistoryEntity
import com.shiping.app.data.model.TvSourceEntity

@Database(
    entities = [ApiSourceEntity::class, ParseSourceEntity::class, DownloadEntity::class, PlayHistoryEntity::class, TvSourceEntity::class, FavoriteEntity::class],
    version = 8,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun apiSourceDao(): ApiSourceDao
    abstract fun parseSourceDao(): ParseSourceDao
    abstract fun downloadDao(): DownloadDao
    abstract fun playHistoryDao(): PlayHistoryDao
    abstract fun tvSourceDao(): TvSourceDao
    abstract fun favoriteDao(): FavoriteDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        /** v6→v7：播放历史改为 (vodId, sourceUrl) 复合主键，旧记录来源置空串保留 */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `play_history_new` (" +
                        "`vodId` INTEGER NOT NULL, " +
                        "`sourceUrl` TEXT NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`vodPic` TEXT NOT NULL, " +
                        "`lastEpisodeName` TEXT NOT NULL, " +
                        "`lastPositionMs` INTEGER NOT NULL, " +
                        "`durationMs` INTEGER NOT NULL, " +
                        "`watchedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`vodId`, `sourceUrl`))",
                )
                db.execSQL(
                    "INSERT INTO `play_history_new` " +
                        "(`vodId`, `sourceUrl`, `title`, `vodPic`, `lastEpisodeName`, `lastPositionMs`, `durationMs`, `watchedAt`) " +
                        "SELECT `vodId`, '', `title`, `vodPic`, `lastEpisodeName`, `lastPositionMs`, `durationMs`, `watchedAt` FROM `play_history`",
                )
                db.execSQL("DROP TABLE `play_history`")
                db.execSQL("ALTER TABLE `play_history_new` RENAME TO `play_history`")
            }
        }

        /** v7→v8：新增收藏表 */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `favorites` (" +
                        "`vodId` INTEGER NOT NULL, " +
                        "`sourceUrl` TEXT NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`vodPic` TEXT NOT NULL, " +
                        "`addedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`vodId`, `sourceUrl`))",
                )
            }
        }

        fun getInstance(): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: buildDatabase().also { instance = it }
            }
        }

        private fun buildDatabase(): AppDatabase {
            val builder = Room.databaseBuilder(
                ShipingApp.context,
                AppDatabase::class.java,
                "shiping.db",
            )
            // 仅在 Debug 构建中允许破坏性迁移，避免线上用户数据丢失
            if (BuildConfig.DEBUG) {
                builder.fallbackToDestructiveMigration()
            }
            builder.addMigrations(MIGRATION_6_7, MIGRATION_7_8)
            return builder.build()
        }
    }
}
