package com.example.cardnote.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 分类实体，支持最多三级层级。
 * parentId == null  → 一级分类（根）
 * parentId != null  → 子分类（最多嵌套到三级）
 */
@Entity(
    tableName = "categories",
    foreignKeys = [ForeignKey(
        entity = CategoryEntity::class,
        parentColumns = ["id"],
        childColumns  = ["parentId"],
        onDelete = ForeignKey.CASCADE   // 父分类删除时，子分类一并删除
    )],
    indices = [Index("parentId")]
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val parentId: Long? = null,   // null = 顶级分类
    val sortOrder: Int = 0，
    val colorHex: String = "#6C63FF"   // ← 新增这一行
)
