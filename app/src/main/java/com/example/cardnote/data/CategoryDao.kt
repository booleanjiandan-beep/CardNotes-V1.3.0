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

    
   
}
