package com.example.cardnote.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    /** 所有分类（实时流，用于构建树） */
    @Query("SELECT * FROM categories ORDER BY parentId ASC, sortOrder ASC, id ASC")
    fun getAllCategories(): Flow<List<CategoryEntity>>

    /** 顶级分类 */
    @Query("SELECT * FROM categories WHERE parentId IS NULL ORDER BY sortOrder ASC, id ASC")
    fun getRootCategories(): Flow<List<CategoryEntity>>

    /** 某个父分类的直接子分类 */
    @Query("SELECT * FROM categories WHERE parentId = :parentId ORDER BY sortOrder ASC, id ASC")
    fun getChildren(parentId: Long): Flow<List<CategoryEntity>>

    /** 统计某分类的直接子数量（用于限制三级深度） */
    @Query("SELECT COUNT(*) FROM categories WHERE parentId = :parentId")
    suspend fun countChildren(parentId: Long): Int

    /** 查询某分类的父分类 id（用于计算当前深度） */
    @Query("SELECT parentId FROM categories WHERE id = :id")
    suspend fun getParentId(id: Long): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: CategoryEntity): Long

    @Update
    suspend fun updateCategory(category: CategoryEntity)

    @Delete
    suspend fun deleteCategory(category: CategoryEntity)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteCategoryById(id: Long)

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
}
