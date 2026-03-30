package com.example.cardnote.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.cardnote.data.*
import com.example.cardnote.util.ImageStorageManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// ── 筛选状态 ──
data class FilterState(
    val showDownloaded: Boolean = true,
    val showNotDownloaded: Boolean = true
) {
    val showAll: Boolean get() = showDownloaded == showNotDownloaded
}

// ── 分类树节点（UI 用） ──
data class CategoryNode(
    val entity: CategoryEntity,
    val children: List<CategoryNode> = emptyList(),
    val depth: Int = 0            // 0=根, 1=二级, 2=三级
)

data class NoteUiState(
    val filteredNotes: List<NoteEntity> = emptyList(),
    val filterState: FilterState = FilterState(),
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val isLoading: Boolean = false,
    val currentPagerIndex: Int = 0,
    // 分类
    val categoryTree: List<CategoryNode> = emptyList(),
    val selectedCategoryId: Long? = null,   // null = 全部
    val isCategoryDrawerOpen: Boolean = false,
    // sheets & dialogs
    val showAddSheet: Boolean = false,
    val noteToEdit: NoteEntity? = null,
    val noteToDelete: NoteEntity? = null,
    val snackbarMessage: String? = null
)

// private data class QueryKey(val catId: Long?, val filter: FilterState, val search: String)
// 改为携带 id 列表
private data class QueryKey(val categoryIds: List<Long>?, val filter: FilterState, val search: String)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class NoteViewModel(application: Application) : AndroidViewModel(application) {

    private val noteRepo: NoteRepository
    private val catRepo:  CategoryRepository
    private val appCtx = application.applicationContext

    private val _filterState = MutableStateFlow(FilterState())
    val filterState: StateFlow<FilterState> = _filterState.asStateFlow()

    private val _rawSearch = MutableStateFlow("")
    private val _searchQuery = _rawSearch.debounce(300).distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    private val _uiState = MutableStateFlow(NoteUiState())
    val uiState: StateFlow<NoteUiState> = _uiState.asStateFlow()

    init {
        val db = NoteDatabase.getDatabase(application)
        noteRepo = NoteRepository(db.noteDao())
        catRepo  = CategoryRepository(db.categoryDao())

        // 监听分类树
        viewModelScope.launch {
            catRepo.getAllCategories().collect { all ->
                _uiState.update { it.copy(categoryTree = buildTree(all, null, 0)) }
            }
        }

        // 监听笔记（catId + filter + search 三路合并）
        viewModelScope.launch {
            combine(
                _uiState.map { it.selectedCategoryId }.distinctUntilChanged(),
                _filterState,
                _searchQuery
            ) { catId, filter, search ->
                // 直接读 StateFlow 当前值，无需加入 combine
                val tree = _uiState.value.categoryTree
                val ids = if (catId == null) null else collectIds(tree, catId)
                QueryKey(ids, filter, search)
            }
                .flatMapLatest { key ->
                    noteRepo.queryNotes(key.categoryIds, key.filter, key.search)
                }
                .collect { notes ->
                    _uiState.update { state ->
                        state.copy(
                            filteredNotes = notes,
                            currentPagerIndex = if (state.currentPagerIndex >= notes.size) 0
                            else state.currentPagerIndex
                        )
                    }
                }
        }
        
        // viewModelScope.launch {
        //     combine(
        //         _uiState.map { it.selectedCategoryId }.distinctUntilChanged(),
        //         _uiState.map { it.categoryTree }.distinctUntilChanged(),   // ← 新增，需要 tree 才能展开 ids
        //         _filterState,
        //         _searchQuery
        //     ) { catId, filter, search -> QueryKey(catId, filter, search) }
        //         .flatMapLatest { key ->
        //             noteRepo.queryNotes(key.catId, key.filter, key.search)
        //         }
        //         .collect { notes ->
        //             _uiState.update { state ->
        //                 state.copy(filteredNotes = notes,
        //                     currentPagerIndex = if (state.currentPagerIndex >= notes.size) 0 else state.currentPagerIndex)
        //             }
        //         }
        // }

        viewModelScope.launch {
            _searchQuery.collect { q ->
                _uiState.update { it.copy(searchQuery = q, currentPagerIndex = 0) }
            }
        }
    }

    private fun buildTree(all: List<CategoryEntity>, parentId: Long?, depth: Int): List<CategoryNode> {
        if (depth >= 3) return emptyList()
        return all.filter { it.parentId == parentId }.map { cat ->
            CategoryNode(entity = cat, depth = depth,
                children = buildTree(all, cat.id, depth + 1))
        }
    }
    
    /** 收集某分类节点自身 + 所有后代的 id */
    private fun collectIds(nodes: List<CategoryNode>, targetId: Long): List<Long>? {
        for (node in nodes) {
            if (node.entity.id == targetId) {
                // 找到目标节点，收集自身 + 所有后代
                val result = mutableListOf<Long>()
                fun collect(n: CategoryNode) {
                    result.add(n.entity.id)
                    n.children.forEach { collect(it) }
                }
                collect(node)
                return result
            }
            // 递归搜索子树
            collectIds(node.children, targetId)?.let { return it }
        }
        return null
    }

    // ── 分类操作 ──
    fun selectCategory(id: Long?) {
        _uiState.update { it.copy(selectedCategoryId = id, currentPagerIndex = 0) }
    }
    fun toggleCategoryDrawer() {
        _uiState.update { it.copy(isCategoryDrawerOpen = !it.isCategoryDrawerOpen) }
    }
    fun closeCategoryDrawer() {
        _uiState.update { it.copy(isCategoryDrawerOpen = false) }
    }
    fun addCategory(name: String, parentId: Long?, colorHex: String = "#6C63FF") {
        viewModelScope.launch {
            if (parentId != null && !catRepo.canAddChild(parentId)) {
                _uiState.update { it.copy(snackbarMessage = "最多支持三级分类") }
                return@launch
            }
            catRepo.insert(CategoryEntity(name = name.trim(), parentId = parentId, colorHex = colorHex))
        }
    }
    fun renameCategory(cat: CategoryEntity, newName: String, newColor: String = cat.colorHex) {
        viewModelScope.launch { catRepo.update(cat.copy(name = newName.trim(), colorHex = newColor)) }
    }
    // fun deleteCategory(cat: CategoryEntity) {
    //     viewModelScope.launch {
    //         catRepo.delete(cat)
    //         if (_uiState.value.selectedCategoryId == cat.id)
    //             _uiState.update { it.copy(selectedCategoryId = null) }
    //     }
    // }
    
    fun deleteCategory(cat: CategoryEntity) {
        viewModelScope.launch {
            val tree = _uiState.value.categoryTree
            // 收集目标分类 + 所有子分类的 ID（利用你已有的 collectIds）
            val categoryIds = collectIds(tree, cat.id) ?: listOf(cat.id)

            // === 1. 获取该分类树下所有笔记（使用你已有的 queryNotes，不需要改 Repository）===
            val allFilter = FilterState(showDownloaded = true, showNotDownloaded = true)
            val notesToDelete = noteRepo.queryNotes(categoryIds, allFilter, "").first()

            // === 2. 删除笔记及其关联图片 ===
            notesToDelete.forEach { note ->
                ImageStorageManager.deleteImages(note.images)
                noteRepo.deleteNote(note)
            }

            // === 3. 删除分类本身（保持原有行为）===
            catRepo.delete(cat)

            // === 4. 如果当前正选中这个分类，就切回“全部笔记”===
            if (_uiState.value.selectedCategoryId == cat.id) {
                _uiState.update { it.copy(selectedCategoryId = null) }
            }

            // === 5. 提示用户 ===
            val noteCount = notesToDelete.size
            val message = if (noteCount > 0) {
                "已删除分类「${cat.name}」及其下 $noteCount 条笔记"
            } else {
                "分类「${cat.name}」已删除"
            }
            _uiState.update { it.copy(snackbarMessage = message) }
        }
    }
    // ── 搜索 ──
    fun onSearchQueryChange(query: String) { _rawSearch.value = query; _uiState.update { it.copy(searchQuery = query) } }
    fun toggleSearch() { val a = !_uiState.value.isSearchActive; _uiState.update { it.copy(isSearchActive = a) }; if (!a) clearSearch() }
    fun clearSearch() { _rawSearch.value = ""; _uiState.update { it.copy(searchQuery = "", isSearchActive = false, currentPagerIndex = 0) } }

    // ── 筛选 ──
    fun toggleDownloadedFilter()    { _filterState.update { it.copy(showDownloaded    = !it.showDownloaded) };    _uiState.update { it.copy(currentPagerIndex = 0) } }
    fun toggleNotDownloadedFilter() { _filterState.update { it.copy(showNotDownloaded = !it.showNotDownloaded) }; _uiState.update { it.copy(currentPagerIndex = 0) } }

    // ── Pager ──
    fun onPagerPageChanged(index: Int) { _uiState.update { it.copy(currentPagerIndex = index) } }

    // ── 新增 Sheet ──
    fun showAddSheet()  { _uiState.update { it.copy(showAddSheet = true) } }
    fun hideAddSheet()  { _uiState.update { it.copy(showAddSheet = false) } }

    fun addNote(name: String, url: String, isDownloaded: Boolean, remarks: String,
                imageUris: List<Uri>, categoryId: Long?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val paths = ImageStorageManager.copyAllToPrivateStorage(appCtx, imageUris)
                noteRepo.insertNote(NoteEntity(name = name.trim(), url = url.trim(),
                    isDownloaded = isDownloaded, remarks = remarks.trim(),
                    images = paths, categoryId = categoryId))
                _uiState.update { it.copy(showAddSheet = false, snackbarMessage = "笔记已添加") }
            } catch (e: Exception) {
                _uiState.update { it.copy(snackbarMessage = "添加失败：${e.message}") }
            } finally { _uiState.update { it.copy(isLoading = false) } }
        }
    }

    // ── 编辑 Sheet ──
    fun showEditSheet(note: NoteEntity) { _uiState.update { it.copy(noteToEdit = note) } }
    fun hideEditSheet()                  { _uiState.update { it.copy(noteToEdit = null) } }

    fun saveEdit(note: NoteEntity, name: String, url: String, isDownloaded: Boolean,
                 remarks: String, keptPaths: List<String>, newUris: List<Uri>, categoryId: Long?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val removed = note.images - keptPaths.toSet()
                ImageStorageManager.deleteImages(removed)
                val newPaths = ImageStorageManager.copyAllToPrivateStorage(appCtx, newUris)
                noteRepo.updateNote(note.copy(name = name.trim(), url = url.trim(),
                    isDownloaded = isDownloaded, remarks = remarks.trim(),
                    images = (keptPaths + newPaths).take(9), categoryId = categoryId))
                _uiState.update { it.copy(noteToEdit = null, snackbarMessage = "笔记已更新") }
            } catch (e: Exception) {
                _uiState.update { it.copy(snackbarMessage = "保存失败：${e.message}") }
            } finally { _uiState.update { it.copy(isLoading = false) } }
        }
    }

    // ── 删除 ──
    fun requestDelete(note: NoteEntity) { _uiState.update { it.copy(noteToDelete = note) } }
    fun cancelDelete()  { _uiState.update { it.copy(noteToDelete = null) } }
    fun confirmDelete() {
        val note = _uiState.value.noteToDelete ?: return
        viewModelScope.launch {
            ImageStorageManager.deleteImages(note.images)
            noteRepo.deleteNote(note)
            _uiState.update { it.copy(noteToDelete = null, snackbarMessage = "「${note.name}」已删除") }
        }
    }

    fun toggleDownloadStatus(note: NoteEntity) {
        viewModelScope.launch { noteRepo.updateDownloadStatus(note.id, !note.isDownloaded) }
    }

    fun removeImageFromNote(note: NoteEntity, path: String) {
        viewModelScope.launch {
            ImageStorageManager.deleteImage(path)
            noteRepo.updateNote(note.copy(images = note.images - path))
        }
    }

    fun clearSnackbar() { _uiState.update { it.copy(snackbarMessage = null) } }
}
