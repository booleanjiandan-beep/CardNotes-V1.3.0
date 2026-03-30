package com.example.cardnote.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    // ── 基础查询 ──

    @Query("SELECT * FROM notes ORDER BY id DESC")
    fun getAllNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE isDownloaded = 1 ORDER BY id DESC")
    fun getDownloadedNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE isDownloaded = 0 ORDER BY id DESC")
    fun getNotDownloadedNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE isDownloaded = :isDownloaded ORDER BY id DESC")
    fun getNotesByDownloadStatus(isDownloaded: Boolean): Flow<List<NoteEntity>>

    // ── 按分类查询 ──

    /** 指定分类（不含子分类）的全部笔记 */
    @Query("SELECT * FROM notes WHERE categoryId = :categoryId ORDER BY id DESC")
    fun getNotesByCategory(categoryId: Long): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE categoryId = :categoryId AND isDownloaded = 1 ORDER BY id DESC")
    fun getDownloadedByCategory(categoryId: Long): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE categoryId = :categoryId AND isDownloaded = 0 ORDER BY id DESC")
    fun getNotDownloadedByCategory(categoryId: Long): Flow<List<NoteEntity>>

    /** 未分类笔记 */
    @Query("SELECT * FROM notes WHERE categoryId IS NULL ORDER BY id DESC")
    fun getUncategorizedNotes(): Flow<List<NoteEntity>>
 
    /** 指定分类集合（自身 + 所有后代）的全部笔记 */
    @Query("SELECT * FROM notes WHERE categoryId IN (:categoryIds) ORDER BY id DESC")
    fun getNotesByCategoryIds(categoryIds: List<Long>): Flow<List<NoteEntity>>
    
    @Query("SELECT * FROM notes WHERE categoryId IN (:categoryIds) AND isDownloaded = 1 ORDER BY id DESC")
    fun getDownloadedByCategoryIds(categoryIds: List<Long>): Flow<List<NoteEntity>>
    
    @Query("SELECT * FROM notes WHERE categoryId IN (:categoryIds) AND isDownloaded = 0 ORDER BY id DESC")
    fun getNotDownloadedByCategoryIds(categoryIds: List<Long>): Flow<List<NoteEntity>>
    
    @Query("""SELECT * FROM notes WHERE categoryId IN (:categoryIds) AND
        (name LIKE '%' || :q || '%' OR url LIKE '%' || :q || '%' OR remarks LIKE '%' || :q || '%')
        ORDER BY id DESC""")
    fun searchByCategoryIds(q: String, categoryIds: List<Long>): Flow<List<NoteEntity>>
    
    @Query("""SELECT * FROM notes WHERE categoryId IN (:categoryIds) AND isDownloaded = :dl AND
        (name LIKE '%' || :q || '%' OR url LIKE '%' || :q || '%' OR remarks LIKE '%' || :q || '%')
        ORDER BY id DESC""")
    fun searchByCategoryIdsAndDownload(q: String, categoryIds: List<Long>, dl: Boolean): Flow<List<NoteEntity>>
    // ── 搜索 ──

    @Query("""SELECT * FROM notes WHERE
        (name LIKE '%' || :q || '%' OR url LIKE '%' || :q || '%' OR remarks LIKE '%' || :q || '%')
        ORDER BY id DESC""")
    fun searchAllNotes(q: String): Flow<List<NoteEntity>>

    @Query("""SELECT * FROM notes WHERE isDownloaded = 1 AND
        (name LIKE '%' || :q || '%' OR url LIKE '%' || :q || '%' OR remarks LIKE '%' || :q || '%')
        ORDER BY id DESC""")
    fun searchDownloadedNotes(q: String): Flow<List<NoteEntity>>

    @Query("""SELECT * FROM notes WHERE isDownloaded = 0 AND
        (name LIKE '%' || :q || '%' OR url LIKE '%' || :q || '%' OR remarks LIKE '%' || :q || '%')
        ORDER BY id DESC""")
    fun searchNotDownloadedNotes(q: String): Flow<List<NoteEntity>>

    @Query("""SELECT * FROM notes WHERE categoryId = :catId AND
        (name LIKE '%' || :q || '%' OR url LIKE '%' || :q || '%' OR remarks LIKE '%' || :q || '%')
        ORDER BY id DESC""")
    fun searchByCategory(q: String, catId: Long): Flow<List<NoteEntity>>

    @Query("""SELECT * FROM notes WHERE categoryId = :catId AND isDownloaded = :dl AND
        (name LIKE '%' || :q || '%' OR url LIKE '%' || :q || '%' OR remarks LIKE '%' || :q || '%')
        ORDER BY id DESC""")
    fun searchByCategoryAndDownload(q: String, catId: Long, dl: Boolean): Flow<List<NoteEntity>>

    // ── CRUD ──

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getNoteById(id: Long): NoteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity): Long

    @Update
    suspend fun updateNote(note: NoteEntity)

    @Delete
    suspend fun deleteNote(note: NoteEntity)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteNoteById(id: Long)

    @Query("UPDATE notes SET isDownloaded = :isDownloaded WHERE id = :id")
    suspend fun updateDownloadStatus(id: Long, isDownloaded: Boolean)

    @Query("SELECT COUNT(*) FROM notes WHERE isDownloaded = :isDownloaded")
    suspend fun countByDownloadStatus(isDownloaded: Boolean): Int
}
