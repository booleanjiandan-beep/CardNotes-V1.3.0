package com.example.cardnote.data

import android.content.Context
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [NoteEntity::class, CategoryEntity::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class NoteDatabase : RoomDatabase() {

    abstract fun noteDao(): NoteDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        @Volatile private var INSTANCE: NoteDatabase? = null

        fun getDatabase(context: Context): NoteDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context.applicationContext, NoteDatabase::class.java, "note_database")
                    .addMigrations(MIGRATION_1_2)
                    .addCallback(DatabaseCallback())
                    .build()
                    .also { INSTANCE = it }
            }
        }

        // 从 v1（无分类）迁移到 v2（含分类）
        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS categories (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        parentId INTEGER,
                        sortOrder INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(parentId) REFERENCES categories(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_categories_parentId ON categories(parentId)")
                db.execSQL("ALTER TABLE notes ADD COLUMN categoryId INTEGER REFERENCES categories(id) ON DELETE SET NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_notes_categoryId ON notes(categoryId)")
            }
        }
    }

    private class DatabaseCallback : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                CoroutineScope(Dispatchers.IO).launch {
                    // 预建分类
                    val catDao  = database.categoryDao()
                    val noteDao = database.noteDao()
                    val techId = catDao.insertCategory(CategoryEntity(name = "技术笔记"))
                    val designId = catDao.insertCategory(CategoryEntity(name = "设计资源"))
                    catDao.insertCategory(CategoryEntity(name = "Kotlin",  parentId = techId))
                    catDao.insertCategory(CategoryEntity(name = "Android", parentId = techId))
                    catDao.insertCategory(CategoryEntity(name = "Material Design", parentId = designId))

                    // 预建示例笔记
                    noteDao.insertNote(NoteEntity(name = "Kotlin 协程笔记",
                        url = "https://kotlinlang.org/docs/coroutines-overview.html",
                        isDownloaded = true, remarks = "深入理解 suspend 函数和 Flow", categoryId = techId))
                    noteDao.insertNote(NoteEntity(name = "Jetpack Compose 入门",
                        url = "https://developer.android.com/jetpack/compose",
                        isDownloaded = false, remarks = "需要下载离线文档", categoryId = techId))
                    noteDao.insertNote(NoteEntity(name = "Room 数据库指南",
                        url = "https://developer.android.com/training/data-storage/room",
                        isDownloaded = true, remarks = "含 DAO、Entity、Migration 示例", categoryId = techId))
                    noteDao.insertNote(NoteEntity(name = "Material3 设计规范",
                        url = "https://m3.material.io/",
                        isDownloaded = false, remarks = "颜色系统和组件规范", categoryId = designId))
                    noteDao.insertNote(NoteEntity(name = "Android 架构组件",
                        url = "https://developer.android.com/topic/architecture",
                        isDownloaded = true, remarks = "MVVM + Repository 模式", categoryId = techId))
                }
            }
        }
    }
}
