package com.example.cardnote.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters

@Entity(
    tableName = "notes",
    foreignKeys = [ForeignKey(
        entity = CategoryEntity::class,
        parentColumns = ["id"],
        childColumns  = ["categoryId"],
        onDelete = ForeignKey.SET_NULL   // 分类删除后，笔记 categoryId 置 null
    )],
    indices = [Index("categoryId")]
)
@TypeConverters(Converters::class)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val url: String,
    val isDownloaded: Boolean = false,
    val remarks: String = "",
    val images: List<String> = emptyList(),
    val categoryId: Long? = null          // 所属分类，null = 未分类
)

class Converters {
    @TypeConverter
    fun fromStringList(value: List<String>): String = value.joinToString(DELIMITER)

    @TypeConverter
    fun toStringList(value: String): List<String> =
        if (value.isEmpty()) emptyList() else value.split(DELIMITER)

    companion object { private const val DELIMITER = "|||" }
}
