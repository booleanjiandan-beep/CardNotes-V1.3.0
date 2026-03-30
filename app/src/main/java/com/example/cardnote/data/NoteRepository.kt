package com.example.cardnote.data

import com.example.cardnote.ui.FilterState
import kotlinx.coroutines.flow.Flow

class NoteRepository(private val dao: NoteDao) {

    fun getAllNotes()          = dao.getAllNotes()
    fun getDownloadedNotes()   = dao.getDownloadedNotes()
    fun getNotDownloadedNotes()= dao.getNotDownloadedNotes()

    /** 根据分类 + 下载筛选 + 搜索词，返回对应 Flow */
    fun queryNotes(
        categoryIds: List<Long>?,   // ← 改为列表
        filter: FilterState,
        search: String
    ): Flow<List<NoteEntity>> {
        val q = search.trim()
        return when {
            q.isNotBlank() && categoryIds != null -> when {
                filter.showAll        -> dao.searchByCategoryIds(q, categoryIds)
                filter.showDownloaded -> dao.searchByCategoryIdsAndDownload(q, categoryIds, true)
                else                  -> dao.searchByCategoryIdsAndDownload(q, categoryIds, false)
            }
            q.isNotBlank() -> when {
                filter.showAll        -> dao.searchAllNotes(q)
                filter.showDownloaded -> dao.searchDownloadedNotes(q)
                else                  -> dao.searchNotDownloadedNotes(q)
            }
            categoryIds != null -> when {
                filter.showAll        -> dao.getNotesByCategoryIds(categoryIds)
                filter.showDownloaded -> dao.getDownloadedByCategoryIds(categoryIds)
                else                  -> dao.getNotDownloadedByCategoryIds(categoryIds)
            }
            else -> when {
                filter.showAll        -> dao.getAllNotes()
                filter.showDownloaded -> dao.getDownloadedNotes()
                else                  -> dao.getNotDownloadedNotes()
            }
        }
    }

    
    // fun queryNotes(
    //     categoryId: Long?,        // null = 全部分类
    //     filter: FilterState,
    //     search: String
    // ): Flow<List<NoteEntity>> {
    //     val q = search.trim()
    //     return when {
    //         // ── 有搜索词 ──
    //         q.isNotBlank() && categoryId != null -> when {
    //             filter.showAll        -> dao.searchByCategory(q, categoryId)
    //             filter.showDownloaded -> dao.searchByCategoryAndDownload(q, categoryId, true)
    //             else                  -> dao.searchByCategoryAndDownload(q, categoryId, false)
    //         }
    //         q.isNotBlank() -> when {
    //             filter.showAll        -> dao.searchAllNotes(q)
    //             filter.showDownloaded -> dao.searchDownloadedNotes(q)
    //             else                  -> dao.searchNotDownloadedNotes(q)
    //         }
    //         // ── 无搜索词，按分类 ──
    //         categoryId != null -> when {
    //             filter.showAll        -> dao.getNotesByCategory(categoryId)
    //             filter.showDownloaded -> dao.getDownloadedByCategory(categoryId)
    //             else                  -> dao.getNotDownloadedByCategory(categoryId)
    //         }
    //         // ── 无搜索词，无分类筛选 ──
    //         else -> when {
    //             filter.showAll        -> dao.getAllNotes()
    //             filter.showDownloaded -> dao.getDownloadedNotes()
    //             else                  -> dao.getNotDownloadedNotes()
    //         }
    //     }
    // }

    suspend fun insertNote(note: NoteEntity)  = dao.insertNote(note)
    suspend fun updateNote(note: NoteEntity)  = dao.updateNote(note)
    suspend fun deleteNote(note: NoteEntity)  = dao.deleteNote(note)
    suspend fun updateDownloadStatus(id: Long, dl: Boolean) = dao.updateDownloadStatus(id, dl)
}

class CategoryRepository(private val dao: CategoryDao) {
    fun getAllCategories()    = dao.getAllCategories()
    fun getRootCategories()  = dao.getRootCategories()
    fun getChildren(pid: Long) = dao.getChildren(pid)
    suspend fun getDepth(id: Long): Int {
        var depth = 0
        var cur: Long? = id
        while (cur != null) { cur = dao.getParentId(cur); depth++ }
        return depth
    }
    suspend fun canAddChild(parentId: Long): Boolean {
        val parentDepth = getDepth(parentId)
        return parentDepth < 4   // 最多四级：depth 1/2/3/4
    }
    suspend fun insert(cat: CategoryEntity) = dao.insertCategory(cat)
    suspend fun update(cat: CategoryEntity) = dao.updateCategory(cat)
    suspend fun delete(cat: CategoryEntity) = dao.deleteCategory(cat)
}
