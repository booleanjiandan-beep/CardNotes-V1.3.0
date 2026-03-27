package com.example.cardnote

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.cardnote.data.CategoryEntity
import com.example.cardnote.data.NoteEntity
import com.example.cardnote.ui.CategoryNode
import com.example.cardnote.ui.FilterState
import com.example.cardnote.ui.NoteViewModel
import com.example.cardnote.ui.theme.CardNoteTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { CardNoteTheme { CardNoteApp() } }
    }
}

// ═══════════════════════════════════════
// Root
// ═══════════════════════════════════════
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CardNoteApp(vm: NoteViewModel = viewModel()) {
    val uiState     by vm.uiState.collectAsStateWithLifecycle()
    val filterState by vm.filterState.collectAsStateWithLifecycle()
    val notes  = uiState.filteredNotes
    val snack  = remember { SnackbarHostState() }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { snack.showSnackbar(it, duration = SnackbarDuration.Short); vm.clearSnackbar() }
    }

    val pager = rememberPagerState(initialPage = 0, pageCount = { notes.size.coerceAtLeast(1) })
    LaunchedEffect(notes.size) { if (pager.currentPage >= notes.size && notes.isNotEmpty()) pager.animateScrollToPage(0) }
    LaunchedEffect(pager.currentPage) { vm.onPagerPageChanged(pager.currentPage) }

    // 抽屉关闭时 back 键
    BackHandler(uiState.isCategoryDrawerOpen) { vm.closeCategoryDrawer() }

    // 选中分类名称 + 颜色
    val selectedCatName = remember(uiState.selectedCategoryId, uiState.categoryTree) {
        @Composable
        fun findName(nodes: List<CategoryNode>, id: Long?): String? {
            if (id == null) return null
            for (n in nodes) { if (n.entity.id == id) return n.entity.name; findName(n.children, id)?.let { return it } }
            return null
        }
        findName(uiState.categoryTree, uiState.selectedCategoryId)
    }
    val selectedCatColor = remember(uiState.selectedCategoryId, uiState.categoryTree) {
        @Composable
        fun findColor(nodes: List<CategoryNode>, id: Long?): String? {
            if (id == null) return null
            for (n in nodes) { if (n.entity.id == id) return n.entity.colorHex; findColor(n.children, id)?.let { return it } }
            return null
        }
        parseColor(findColor(uiState.categoryTree, uiState.selectedCategoryId) ?: "#6C63FF")
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color(0xFF0F0F14),
        snackbarHost = {
            SnackbarHost(snack) { d ->
                Snackbar(snackbarData = d, containerColor = Color(0xFF2A2A3E),
                    contentColor = Color.White, shape = RoundedCornerShape(12.dp))
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { vm.showAddSheet() },
                containerColor = Color(0xFF6C63FF), contentColor = Color.White,
                shape = RoundedCornerShape(16.dp), elevation = FloatingActionButtonDefaults.elevation(8.dp)
            ) { Icon(Icons.Default.Add, "新增笔记", modifier = Modifier.size(24.dp)) }
        }
    ) { pad ->
        Row(modifier = Modifier.fillMaxSize().padding(pad)) {
            // ── 左侧分类抽屉（可收起） ──
            AnimatedVisibility(visible = uiState.isCategoryDrawerOpen,
                enter = slideInHorizontally { -it } + fadeIn(),
                exit  = slideOutHorizontally { -it } + fadeOut()
            ) {
                CategoryDrawer(
                    tree = uiState.categoryTree,
                    selectedId = uiState.selectedCategoryId,
                    onSelect   = { id -> vm.selectCategory(id); vm.closeCategoryDrawer() },
                    onAddRoot  = { name, color -> vm.addCategory(name, null, color) },
                    onAddChild = { name, pid, color -> vm.addCategory(name, pid, color) },
                    onRename   = { cat, name, color -> vm.renameCategory(cat, name, color) },
                    onDelete   = { cat -> vm.deleteCategory(cat) },
                    onClose    = { vm.closeCategoryDrawer() }
                )
            }

            Column(modifier = Modifier.fillMaxSize()) {
                FilterHeader(
                    filterState = filterState, totalCount = notes.size,
                    searchQuery = uiState.searchQuery, isSearchActive = uiState.isSearchActive,
                    selectedCatName = selectedCatName,
                    selectedCatColor = selectedCatColor,
                    onOpenDrawer           = { vm.toggleCategoryDrawer() },
                    onToggleDownloaded     = { vm.toggleDownloadedFilter() },
                    onToggleNotDownloaded  = { vm.toggleNotDownloadedFilter() },
                    onToggleSearch         = { vm.toggleSearch() },
                    onSearchQueryChange    = { vm.onSearchQueryChange(it) },
                    onClearSearch          = { vm.clearSearch() }
                )
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (notes.isEmpty()) EmptyStateView(filterState, uiState.searchQuery)
                    else {
                        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 32.dp), pageSpacing = 16.dp
                        ) { idx ->
                            val note = notes[idx]
                            val offset = (pager.currentPage - idx) + pager.currentPageOffsetFraction
                            NoteCard(
                                note = note, pageOffset = offset, searchQuery = uiState.searchQuery,
                                categoryTree = uiState.categoryTree,
                                onToggleDownload = { vm.toggleDownloadStatus(note) },
                                onEditRequest    = { vm.showEditSheet(note) },
                                onDeleteRequest  = { vm.requestDelete(note) },
                                onRemoveImage    = { path -> vm.removeImageFromNote(note, path) }
                            )
                        }
                        PageIndicator(currentPage = pager.currentPage, pageCount = notes.size,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp))
                    }
                }
            }
        }
    }

    // sheets / dialogs
    if (uiState.showAddSheet) {
        NoteBottomSheet(isLoading = uiState.isLoading, existingNote = null,
            categoryTree = uiState.categoryTree, initialCategoryId = uiState.selectedCategoryId,
            onDismiss = { vm.hideAddSheet() },
            onConfirm = { name, url, dl, rem, kept, uris, catId -> vm.addNote(name, url, dl, rem, uris, catId) })
    }
    uiState.noteToEdit?.let { note ->
        NoteBottomSheet(isLoading = uiState.isLoading, existingNote = note,
            categoryTree = uiState.categoryTree, initialCategoryId = note.categoryId,
            onDismiss = { vm.hideEditSheet() },
            onConfirm = { name, url, dl, rem, kept, uris, catId -> vm.saveEdit(note, name, url, dl, rem, kept, uris, catId) })
    }
    uiState.noteToDelete?.let { note ->
        DeleteConfirmDialog(noteName = note.name, imageCount = note.images.size,
            onConfirm = { vm.confirmDelete() }, onDismiss = { vm.cancelDelete() })
    }
}

// ═══════════════════════════════════════
// 分类抽屉
// ═══════════════════════════════════════
@Composable
fun CategoryDrawer(
    tree: List<CategoryNode>, selectedId: Long?,
    onSelect: (Long?) -> Unit,
    onAddRoot: (name: String, color: String) -> Unit,
    onAddChild: (name: String, parentId: Long, color: String) -> Unit,
    onRename: (cat: CategoryEntity, newName: String, newColor: String) -> Unit,
    onDelete: (CategoryEntity) -> Unit,
    onClose: () -> Unit
) {
    var showAddRoot by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxHeight().width(280.dp), color = Color(0xFF13131E),
        shadowElevation = 16.dp) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("分类", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = { showAddRoot = true }) {
                        Icon(Icons.Default.CreateNewFolder, "新建分类", tint = Color(0xFF6C63FF), modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, "关闭", tint = Color(0xFF8888AA), modifier = Modifier.size(20.dp))
                    }
                }
            }
            Divider(color = Color(0xFF2A2A38))
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
                CategoryItem(label = "全部笔记", icon = Icons.Default.LibraryBooks, accentColor = Color(0xFF6C63FF),
                    selected = selectedId == null, depth = 0, isFolder = false,
                    onSelect = { onSelect(null) }, canAddChild = false,
                    onAddChild = {}, onRename = {}, onDelete = {})
                Divider(color = Color(0xFF1E1E2E), modifier = Modifier.padding(vertical = 4.dp))
                tree.forEach { node ->
                    CategoryNodeItem(node, selectedId, onSelect, onAddChild, onRename, onDelete)
                }
            }
        }
    }

    if (showAddRoot) {
        CategoryNameDialog(title = "新建顶级分类",
            onConfirm = { name, color -> onAddRoot(name, color); showAddRoot = false },
            onDismiss = { showAddRoot = false })
    }
}

@Composable
fun CategoryNodeItem(
    node: CategoryNode, selectedId: Long?,
    onSelect: (Long?) -> Unit,
    onAddChild: (name: String, parentId: Long, color: String) -> Unit,
    onRename: (cat: CategoryEntity, newName: String, newColor: String) -> Unit,
    onDelete: (CategoryEntity) -> Unit
) {
    var showAddChild by remember { mutableStateOf(false) }
    var showRename   by remember { mutableStateOf(false) }
    var expanded     by remember { mutableStateOf(true) }
    val canAddChild  = node.depth < 2
    val accentColor  = parseColor(node.entity.colorHex)

    CategoryItem(
        label = node.entity.name,
        icon = if (node.children.isEmpty()) Icons.Default.Label else Icons.Default.Folder,
        accentColor = accentColor,
        selected = selectedId == node.entity.id, depth = node.depth,
        isFolder = node.children.isNotEmpty(),
        expanded = expanded, onToggleExpand = { expanded = !expanded },
        onSelect = { onSelect(node.entity.id) },
        canAddChild = canAddChild,
        onAddChild = { showAddChild = true },
        onRename   = { showRename = true },
        onDelete   = { onDelete(node.entity) }
    )

    AnimatedVisibility(visible = expanded) {
        Column {
            node.children.forEach { child ->
                CategoryNodeItem(child, selectedId, onSelect, onAddChild, onRename, onDelete)
            }
        }
    }

    if (showAddChild) {
        CategoryNameDialog(title = "在「${node.entity.name}」下新建分类",
            onConfirm = { name, color -> onAddChild(name, node.entity.id, color); showAddChild = false },
            onDismiss = { showAddChild = false })
    }
    if (showRename) {
        CategoryNameDialog(title = "重命名「${node.entity.name}」", initial = node.entity.name,
            initialColor = node.entity.colorHex,
            onConfirm = { name, color -> onRename(node.entity, name, color); showRename = false },
            onDismiss = { showRename = false })
    }
}

@Composable
fun CategoryItem(
    label: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color = Color(0xFF6C63FF),
    selected: Boolean, depth: Int, isFolder: Boolean,
    expanded: Boolean = false, onToggleExpand: () -> Unit = {},
    onSelect: () -> Unit, canAddChild: Boolean,
    onAddChild: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val indentDp = (depth * 20).dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp + indentDp, end = 4.dp, top = 2.dp, bottom = 2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) accentColor.copy(alpha = 0.18f) else Color.Transparent)
            .clickable { onSelect() }
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (isFolder) {
            Icon(if (expanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight, null,
                tint = Color(0xFF8888AA), modifier = Modifier.size(16.dp).clickable { onToggleExpand() })
        } else {
            Spacer(modifier = Modifier.size(16.dp))
        }
        // 彩色圆点标记
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(accentColor))
        Icon(icon, null, tint = if (selected) accentColor else Color(0xFF8888AA), modifier = Modifier.size(16.dp))
        Text(label, fontSize = 14.sp, color = if (selected) Color.White else Color(0xFFCCCCDD),
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)

        if (onRename != {}) {
            Box {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.MoreVert, null, tint = Color(0xFF555566), modifier = Modifier.size(16.dp))
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(Color(0xFF252535))) {
                    if (canAddChild) DropdownMenuItem(
                        text = { Text("新建子分类", color = Color.White, fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.CreateNewFolder, null, tint = accentColor, modifier = Modifier.size(16.dp)) },
                        onClick = { showMenu = false; onAddChild() })
                    DropdownMenuItem(
                        text = { Text("重命名", color = Color.White, fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, null, tint = Color(0xFF8888AA), modifier = Modifier.size(16.dp)) },
                        onClick = { showMenu = false; onRename() })
                    DropdownMenuItem(
                        text = { Text("删除", color = Color(0xFFFF5555), fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color(0xFFFF5555), modifier = Modifier.size(16.dp)) },
                        onClick = { showMenu = false; onDelete() })
                }
            }
        }
    }
}

// 颜色解析工具（#RRGGBB → Color）
@Composable
fun parseColor(hex: String): Color {
    return try {
        val c = hex.trimStart('#')
        val r = c.substring(0, 2).toInt(16)
        val g = c.substring(2, 4).toInt(16)
        val b = c.substring(4, 6).toInt(16)
        Color(r, g, b)
    } catch (_: Exception) { Color(0xFF6C63FF) }
}

// 预设颜色盘
private val PRESET_COLORS = listOf(
    "#6C63FF", "#FF6B6B", "#34D399", "#FB923C", "#60A5FA",
    "#F472B6", "#FBBF24", "#A78BFA", "#2DD4BF", "#F87171"
)

@Composable
fun CategoryNameDialog(
    title: String, initial: String = "", initialColor: String = "#6C63FF",
    onConfirm: (name: String, color: String) -> Unit, onDismiss: () -> Unit
) {
    var text  by remember { mutableStateOf(initial) }
    var color by remember { mutableStateOf(initialColor) }
    AlertDialog(onDismissRequest = onDismiss, containerColor = Color(0xFF1E1E2E), shape = RoundedCornerShape(16.dp),
        title = { Text(title, color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = text, onValueChange = { text = it },
                    placeholder = { Text("分类名称", color = Color(0xFF555566)) },
                    singleLine = true, shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = parseColor(color),
                        unfocusedBorderColor = Color(0xFF3A3A55), focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White, cursorColor = parseColor(color),
                        focusedContainerColor = Color(0xFF252535), unfocusedContainerColor = Color(0xFF252535)))
                // 颜色选择
                Text("标签颜色", fontSize = 12.sp, color = Color(0xFF8888AA))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    PRESET_COLORS.forEach { hex ->
                        val c = parseColor(hex)
                        Box(modifier = Modifier
                            .size(28.dp).clip(CircleShape).background(c)
                            .border(if (color == hex) 2.dp else 0.dp, Color.White, CircleShape)
                            .clickable { color = hex },
                            contentAlignment = Alignment.Center) {
                            if (color == hex) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { if (text.isNotBlank()) onConfirm(text.trim(), color) },
                colors = ButtonDefaults.buttonColors(containerColor = parseColor(color)),
                shape = RoundedCornerShape(10.dp)) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = Color(0xFF8888AA)) } }
    )
}

// ═══════════════════════════════════════
// 顶部筛选 / 搜索栏
// ═══════════════════════════════════════
@Composable
fun FilterHeader(
    filterState: FilterState, totalCount: Int, searchQuery: String, isSearchActive: Boolean,
    selectedCatName: String?,
    selectedCatColor: Color = Color(0xFF6C63FF),
    onOpenDrawer: () -> Unit,
    onToggleDownloaded: () -> Unit, onToggleNotDownloaded: () -> Unit,
    onToggleSearch: () -> Unit, onSearchQueryChange: (String) -> Unit, onClearSearch: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val focusReq = remember { FocusRequester() }
    LaunchedEffect(isSearchActive) { if (isSearchActive) { delay(100); runCatching { focusReq.requestFocus() } } }

    Surface(color = Color(0xFF1A1A24), shadowElevation = 8.dp) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {

                // 左侧：汉堡菜单按钮（打开分类抽屉）
                IconButton(onClick = onOpenDrawer,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp))
                        .background(if (selectedCatName != null) selectedCatColor.copy(0.2f) else Color(0xFF2A2A38))) {
                    Icon(Icons.Default.Menu, "分类",
                        tint = if (selectedCatName != null) selectedCatColor else Color(0xFF8888AA))
                }

                // 标题 / 搜索框区域 — 固定高度 40dp，防止搜索框撑高整行
                Box(modifier = Modifier.weight(1f).height(40.dp)) {
                    // 标题（非搜索状态）
                    AnimatedVisibility(!isSearchActive, modifier = Modifier.fillMaxSize(),
                        enter = fadeIn(), exit = fadeOut()) {
                        Column(modifier = Modifier.fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                            Text("卡片笔记", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                            if (selectedCatName != null)
                                Text(selectedCatName, fontSize = 10.sp, color = Color(0xFF6C63FF), maxLines = 1)
                            else
                                Text("共 $totalCount 条", fontSize = 10.sp, color = Color(0xFF8888AA), maxLines = 1)
                        }
                    }
                    // 搜索框（搜索状态）— 强制固定高度，不随内容撑大
                    AnimatedVisibility(isSearchActive, modifier = Modifier.fillMaxSize(),
                        enter = fadeIn(), exit = fadeOut()) {
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            modifier = Modifier.fillMaxSize().focusRequester(focusReq),
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = Color.White, fontSize = 13.sp,
                                lineHeight = 13.sp
                            ),
                            decorationBox = { innerTextField ->
                                Row(
                                    modifier = Modifier.fillMaxSize()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF252535))
                                        .border(1.dp, Color(0xFF6C63FF), RoundedCornerShape(10.dp))
                                        .padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.Search, null, tint = Color(0xFF6C63FF), modifier = Modifier.size(14.dp))
                                    Box(modifier = Modifier.weight(1f)) {
                                        if (searchQuery.isEmpty()) Text("搜索…", color = Color(0xFF5555AA), fontSize = 13.sp, maxLines = 1)
                                        innerTextField()
                                    }
                                    if (searchQuery.isNotEmpty()) {
                                        Icon(Icons.Default.Close, null, tint = Color(0xFF8888AA),
                                            modifier = Modifier.size(14.dp).clickable { onSearchQueryChange("") })
                                    }
                                }
                            }
                        )
                    }
                }

                // 搜索图标
                IconButton(onClick = onToggleSearch,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp))
                        .background(if (isSearchActive || searchQuery.isNotEmpty()) Color(0xFF6C63FF).copy(0.2f) else Color(0xFF2A2A38))) {
                    Icon(if (isSearchActive) Icons.Default.SearchOff else Icons.Default.Search, "搜索",
                        tint = if (isSearchActive || searchQuery.isNotEmpty()) Color(0xFF6C63FF) else Color(0xFF8888AA))
                }

                // 右侧：三个点菜单（筛选）
                Box {
                    IconButton(onClick = { menuExpanded = !menuExpanded },
                        modifier = Modifier.clip(RoundedCornerShape(10.dp))
                            .background(if (!filterState.showAll) Color(0xFF6C63FF).copy(0.2f) else Color(0xFF2A2A38))) {
                        Icon(Icons.Default.MoreVert, "筛选",
                            tint = if (!filterState.showAll) Color(0xFF6C63FF) else Color(0xFF8888AA))
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false },
                        modifier = Modifier.background(Color(0xFF2A2A38)).width(180.dp)) {
                        Text("筛选条件", modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            fontSize = 11.sp, color = Color(0xFF8888AA), fontWeight = FontWeight.Medium)
                        FilterCheckboxItem("已下载",  filterState.showDownloaded,    Color(0xFF4CAF50)) { onToggleDownloaded() }
                        FilterCheckboxItem("未下载",  filterState.showNotDownloaded, Color(0xFFFF7043)) { onToggleNotDownloaded() }
                    }
                }
            }

            // 状态指示条
            val showBanner = searchQuery.isNotEmpty() || !filterState.showAll || selectedCatName != null
            AnimatedVisibility(visible = showBanner) {
                Row(modifier = Modifier.fillMaxWidth().background(selectedCatColor.copy(0.07f)).padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    selectedCatName?.let {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(selectedCatColor))
                            Text(it, fontSize = 11.sp, color = selectedCatColor, fontWeight = FontWeight.Medium)
                        }
                    }
                    if (searchQuery.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Icon(Icons.Default.Search, null, tint = Color(0xFF8888CC), modifier = Modifier.size(12.dp))
                        Text("\"$searchQuery\"", fontSize = 11.sp, color = Color(0xFF8888CC))
                    }
                    if (!filterState.showAll) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Icon(Icons.Default.FilterList, null, tint = Color(0xFF8888CC), modifier = Modifier.size(12.dp))
                        Text(if (filterState.showDownloaded) "已下载" else "未下载", fontSize = 11.sp, color = Color(0xFF8888CC))
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Text("$totalCount 条", fontSize = 11.sp, color = Color(0xFF44446A))
                }
            }
        }
    }
}

@Composable
fun FilterCheckboxItem(label: String, checked: Boolean, color: Color, onCheckedChange: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable { onCheckedChange() }.padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = { onCheckedChange() },
            colors = CheckboxDefaults.colors(checkedColor = color, uncheckedColor = Color(0xFF8888AA)))
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, color = Color.White, fontSize = 13.sp)
        Spacer(modifier = Modifier.weight(1f))
        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(color))
    }
}

// ═══════════════════════════════════════
// 笔记卡片
// ═══════════════════════════════════════
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteCard(
    note: NoteEntity, pageOffset: Float, searchQuery: String = "",
    categoryTree: List<CategoryNode> = emptyList(),
    onToggleDownload: () -> Unit,
    onEditRequest: () -> Unit,
    onDeleteRequest: () -> Unit,
    onRemoveImage: (String) -> Unit = {}
) {
    val scale by animateFloatAsState(
        targetValue = 1f - (abs(pageOffset) * 0.08f).coerceIn(0f, 0.08f),
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "scale")
    val context = LocalContext.current

    // ── 图片状态 ──
    val imgPager = rememberPagerState(initialPage = 0, pageCount = { note.images.size.coerceAtLeast(1) })
    var isImageEditMode  by remember(note.id) { mutableStateOf(false) }
    var imageToDelete    by remember { mutableStateOf<String?>(null) }
    // 全屏预览
    var fullscreenIndex  by remember { mutableStateOf<Int?>(null) }
    val coroutineScope   = rememberCoroutineScope()

    LaunchedEffect(note.images.size) {
        if (note.images.isEmpty()) isImageEditMode = false
        else if (imgPager.currentPage >= note.images.size) imgPager.scrollToPage(note.images.size - 1)
    }

    // 分类名称 + 颜色
    val catEntry = remember(note.categoryId, categoryTree) {
        @Composable
        fun find(nodes: List<CategoryNode>, id: Long?): CategoryNode? {
            if (id == null) return null
            for (n in nodes) { if (n.entity.id == id) return n; find(n.children, id)?.let { return it } }
            return null
        }
        find(categoryTree, note.categoryId)
    }
    val catName  = catEntry?.entity?.name
    val catColor = catEntry?.let { parseColor(it.entity.colorHex) } ?: Color(0xFF6C63FF)

    Card(
        modifier = Modifier.fillMaxWidth().fillMaxHeight(0.82f)
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = 1f - (abs(pageOffset) * 0.3f).coerceIn(0f, 0.3f) }
            .shadow(24.dp, RoundedCornerShape(24.dp),
                ambientColor = Color(0xFF6C63FF).copy(0.3f), spotColor = Color(0xFF6C63FF).copy(0.4f)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── 图片区域 ──
            Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.5f)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(Color(0xFF252535))
                .pointerInput(note.id) {
                    detectTapGestures(
                        onLongPress = { if (note.images.isNotEmpty()) isImageEditMode = true },
                        onDoubleTap = { if (note.images.isNotEmpty()) fullscreenIndex = imgPager.currentPage }
                    )
                }
            ) {
                if (note.images.isNotEmpty()) {
                    // ── 图片堆叠效果（只在非编辑模式下显示后续图层） ──
                    if (!isImageEditMode && note.images.size > 1) {
                        // 下方堆叠层（最多显示 2 层假堆叠）
                        val stackCount = minOf(note.images.size - 1, 2)
                        for (i in stackCount downTo 1) {
                            val offsetX = (i * 4).dp
                            val offsetY = (i * 3).dp
                            val rot = i * 2.5f
                            Box(modifier = Modifier.fillMaxSize()
                                .graphicsLayer { translationX = offsetX.toPx(); translationY = -offsetY.toPx(); rotationZ = rot }
                                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                                .background(Color(0xFF2A2A40))
                            )
                        }
                    }

                    // ── 主图片（顶层，非编辑模式只加载当前页，节省内存）──
                    if (isImageEditMode) {
                        // 编辑模式：完整 Pager
                        HorizontalPager(state = imgPager, modifier = Modifier.fillMaxSize(), userScrollEnabled = false) { i ->
                            val path = note.images[i]
                            AsyncImage(model = ImageRequest.Builder(context)
                                .data(if (path.startsWith("/")) File(path) else Uri.parse(path)).crossfade(true).build(),
                                contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        }
                    } else {
                        // 普通模式：HorizontalPager 正常滑动，只渲染可见帧（Compose Pager 默认 beyondBoundsPageCount=0）
                        HorizontalPager(state = imgPager, modifier = Modifier.fillMaxSize(),
                            beyondViewportPageCount = 0) { i ->
                            val path = note.images[i]
                            AsyncImage(model = ImageRequest.Builder(context)
                                .data(if (path.startsWith("/")) File(path) else Uri.parse(path)).crossfade(true).build(),
                                contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        }
                    }

                    // 页码点（非编辑模式 + 多图）
                    if (!isImageEditMode && note.images.size > 1) {
                        Row(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            note.images.forEachIndexed { i, _ ->
                                Box(modifier = Modifier
                                    .size(width = if (i == imgPager.currentPage) 20.dp else 6.dp, height = 6.dp)
                                    .clip(CircleShape)
                                    .background(if (i == imgPager.currentPage) Color.White else Color.White.copy(0.4f))
                                    .animateContentSize())
                            }
                        }
                    }

                    // 双击提示（仅普通模式，短暂显示）
                    if (!isImageEditMode) {
                        Box(modifier = Modifier.align(Alignment.TopEnd).padding(10.dp)
                            .clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(0.45f))
                            .padding(horizontal = 6.dp, vertical = 3.dp)) {
                            Text("双击全屏", fontSize = 9.sp, color = Color.White.copy(0.7f))
                        }
                    }

                    // ── 编辑模式：底部缩略图条 ──
                    if (isImageEditMode) {
                        Surface(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                            color = Color.Black.copy(0.78f)) {
                            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                note.images.forEachIndexed { i, path ->
                                    Box(modifier = Modifier.size(52.dp).clip(RoundedCornerShape(8.dp))
                                        .border(if (i == imgPager.currentPage) 2.dp else 0.dp, Color(0xFF6C63FF), RoundedCornerShape(8.dp))
                                        .clickable { coroutineScope.launch { imgPager.animateScrollToPage(i) } }) {
                                        AsyncImage(model = ImageRequest.Builder(context)
                                            .data(if (path.startsWith("/")) File(path) else Uri.parse(path)).crossfade(false).build(),
                                            contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                        Box(modifier = Modifier.align(Alignment.TopEnd).padding(2.dp)
                                            .size(18.dp).clip(CircleShape).background(Color(0xFFFF4444))
                                            .clickable { imageToDelete = path }, contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.Close, "删除", tint = Color.White, modifier = Modifier.size(11.dp))
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.weight(1f))
                                IconButton(onClick = { isImageEditMode = false },
                                    modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(0.15f))) {
                                    Icon(Icons.Default.Check, "完成", tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                        Surface(modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
                            shape = RoundedCornerShape(8.dp), color = Color(0xFF6C63FF).copy(0.88f)) {
                            Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.Edit, null, tint = Color.White, modifier = Modifier.size(11.dp))
                                Text("图片编辑模式", fontSize = 10.sp, color = Color.White)
                            }
                        }
                    }
                } else {
                    // 无图占位
                    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(Icons.Default.ImageNotSupported, null, tint = Color(0xFF3A3A55), modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("暂无图片", color = Color(0xFF3A3A55), fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("编辑笔记可添加图片", color = Color(0xFF2A2A45), fontSize = 11.sp)
                    }
                }

                // 下载徽章
                if (!isImageEditMode) {
                    Box(modifier = Modifier.align(Alignment.TopStart).padding(12.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (note.isDownloaded) Color(0xFF4CAF50).copy(0.9f) else Color(0xFFFF7043).copy(0.9f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(if (note.isDownloaded) Icons.Default.CloudDone else Icons.Default.CloudOff,
                                null, tint = Color.White, modifier = Modifier.size(12.dp))
                            Text(if (note.isDownloaded) "已下载" else "未下载",
                                color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            } // end 图片 Box

            // ── 内容区域 ──
            Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
                // 分类标签（彩色）
                catName?.let {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(bottom = 6.dp)) {
                        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(catColor))
                        Text(it, fontSize = 11.sp, color = catColor)
                    }
                }
                HighlightText(note.name, searchQuery, Color.White, 20.sp, 2, FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                // URL + 复制
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.Link, null, tint = Color(0xFF6C63FF), modifier = Modifier.size(14.dp))
                    Box(modifier = Modifier.weight(1f)) { HighlightText(note.url, searchQuery, Color(0xFF6C63FF), 12.sp, 1) }
                    if (note.url.isNotBlank()) {
                        IconButton(onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("url", note.url))
                        }, modifier = Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF6C63FF).copy(0.15f))) {
                            Icon(Icons.Default.ContentCopy, "复制", tint = Color(0xFF6C63FF), modifier = Modifier.size(14.dp))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                if (note.remarks.isNotBlank()) {
                    Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFF252535)) {
                        HighlightText(note.remarks, searchQuery, Color(0xFFCCCCDD), 13.sp, 3, modifier = Modifier.padding(12.dp))
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onEditRequest, shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF6C63FF)),
                        border = BorderStroke(1.dp, Color(0xFF6C63FF).copy(0.5f)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                        Icon(Icons.Default.Edit, null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("编辑", fontSize = 12.sp)
                    }
                    OutlinedButton(onClick = onToggleDownload, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = if (note.isDownloaded) Color(0xFFFF7043) else Color(0xFF4CAF50)),
                        border = BorderStroke(1.dp, if (note.isDownloaded) Color(0xFFFF7043).copy(0.5f) else Color(0xFF4CAF50).copy(0.5f)),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)) {
                        Icon(if (note.isDownloaded) Icons.Default.CloudOff else Icons.Default.CloudDownload, null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (note.isDownloaded) "标为未下载" else "标为已下载", fontSize = 12.sp)
                    }
                    IconButton(onClick = onDeleteRequest,
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFFFF4444).copy(0.1f))) {
                        Icon(Icons.Default.Delete, "删除", tint = Color(0xFFFF4444))
                    }
                }
            }
        }
    }

    // ── 全屏图片弹窗 ──
    fullscreenIndex?.let { startIdx ->
        FullscreenImageDialog(images = note.images, startIndex = startIdx, onDismiss = { fullscreenIndex = null })
    }

    // 单张图片删除确认
    imageToDelete?.let { path ->
        AlertDialog(onDismissRequest = { imageToDelete = null }, containerColor = Color(0xFF1E1E2E), shape = RoundedCornerShape(16.dp),
            icon = { Icon(Icons.Default.HideImage, null, tint = Color(0xFFFF7043), modifier = Modifier.size(32.dp)) },
            title = { Text("删除这张图片？", color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
            text  = { Text("图片将从应用存储永久删除。", color = Color(0xFF8888AA), fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {
                Button(onClick = { onRemoveImage(path); imageToDelete = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF7043)), shape = RoundedCornerShape(10.dp)) {
                    Text("删除", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { imageToDelete = null }, shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFF3A3A55)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF8888AA))) { Text("取消") }
            })
    }
}

// ═══════════════════════════════════════
// 全屏图片弹窗（双击触发）
// ═══════════════════════════════════════
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FullscreenImageDialog(images: List<String>, startIndex: Int, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val pager = rememberPagerState(initialPage = startIndex, pageCount = { images.size })

    Dialog(onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true, dismissOnClickOutside = true)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)
            .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) }) {

            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { i ->
                val path = images[i]
                AsyncImage(model = ImageRequest.Builder(context)
                    .data(if (path.startsWith("/")) File(path) else Uri.parse(path)).crossfade(true).build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures() }, // 消费点击防止穿透关闭
                    contentScale = ContentScale.Fit)
            }

            // 关闭按钮
            IconButton(onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                    .size(36.dp).clip(CircleShape).background(Color.Black.copy(0.5f))) {
                Icon(Icons.Default.Close, "关闭", tint = Color.White)
            }

            // 页码
            if (images.size > 1) {
                Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp)
                    .clip(RoundedCornerShape(16.dp)).background(Color.Black.copy(0.5f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Text("${pager.currentPage + 1} / ${images.size}", color = Color.White, fontSize = 13.sp)
                }
            }

            // 提示
            Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp)) {
                Text("点击任意处关闭", color = Color.White.copy(0.45f), fontSize = 11.sp)
            }
        }
    }
}

// ═══════════════════════════════════════
// 新增 / 编辑 BottomSheet
// ═══════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteBottomSheet(
    isLoading: Boolean, existingNote: NoteEntity?,
    categoryTree: List<CategoryNode>, initialCategoryId: Long?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, url: String, isDownloaded: Boolean, remarks: String,
                keptPaths: List<String>, newImageUris: List<Uri>, categoryId: Long?) -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isEdit = existingNote != null

    var name         by remember { mutableStateOf(existingNote?.name    ?: "") }
    var url          by remember { mutableStateOf(existingNote?.url     ?: "") }
    var isDownloaded by remember { mutableStateOf(existingNote?.isDownloaded ?: false) }
    var remarks      by remember { mutableStateOf(existingNote?.remarks ?: "") }
    var nameError    by remember { mutableStateOf(false) }
    var showImgSrc   by remember { mutableStateOf(false) }
    var keptPaths    by remember { mutableStateOf(existingNote?.images ?: emptyList()) }
    var newUris      by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var selectedCat  by remember { mutableStateOf(initialCategoryId) }
    var showCatPicker by remember { mutableStateOf(false) }

    // Bug fix: 改为普通函数，避免局部变量使用 get()
    fun totalImageCount() = keptPaths.size + newUris.size

    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        val canAdd = 9 - totalImageCount()
        newUris = (newUris + uris).distinct().take(newUris.size + canAdd.coerceAtLeast(0))
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) cameraUri?.let { if (totalImageCount() < 9) newUris = newUris + it }
    }
    val cameraPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { val f = File(context.cacheDir, "photo_${System.currentTimeMillis()}.jpg")
            val u = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
            cameraUri = u; cameraLauncher.launch(u) }
    }
    fun launchCamera() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            val f = File(context.cacheDir, "photo_${System.currentTimeMillis()}.jpg")
            val u = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
            cameraUri = u; cameraLauncher.launch(u)
        } else cameraPermLauncher.launch(Manifest.permission.CAMERA)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Color(0xFF1A1A2E),
        dragHandle = {
            Box(modifier = Modifier.padding(vertical = 12.dp).size(width = 40.dp, height = 4.dp).clip(CircleShape).background(Color(0xFF3A3A55)))
        }) {
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (isEdit) "编辑笔记" else "新增笔记", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "关闭", tint = Color(0xFF8888AA)) }
            }

            NoteTextField(value = name, onValueChange = { name = it; nameError = false },
                label = "笔记名称 *", placeholder = "请输入笔记标题",
                isError = nameError, errorMessage = "名称不能为空", leadingIcon = Icons.Default.Title)
            NoteTextField(value = url, onValueChange = { url = it }, label = "URL", placeholder = "https://example.com", leadingIcon = Icons.Default.Link)
            NoteTextField(value = remarks, onValueChange = { remarks = it }, label = "备注", placeholder = "添加备注内容…", singleLine = false, minLines = 3, leadingIcon = Icons.Default.Notes)

            // 分类选择
            Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFF252540),
                modifier = Modifier.fillMaxWidth().clickable { showCatPicker = true }) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.Folder, null, tint = Color(0xFF6C63FF), modifier = Modifier.size(18.dp))
                    Text(text = run {
                        fun find(nodes: List<CategoryNode>, id: Long?): String? {
                            if (id == null) return null
                            for (n in nodes) { if (n.entity.id == id) return n.entity.name; find(n.children, id)?.let { return it } }
                            return null
                        }
                        find(categoryTree, selectedCat) ?: "未分类（点击选择）"
                    }, color = if (selectedCat != null) Color.White else Color(0xFF666688), fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Icon(Icons.Default.ChevronRight, null, tint = Color(0xFF555566), modifier = Modifier.size(16.dp))
                }
            }

            // 下载开关
            Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFF252540)) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(if (isDownloaded) Icons.Default.CloudDone else Icons.Default.CloudOff, null,
                            tint = if (isDownloaded) Color(0xFF4CAF50) else Color(0xFF8888AA), modifier = Modifier.size(20.dp))
                        Text(if (isDownloaded) "已下载" else "未下载", color = Color.White, fontSize = 14.sp)
                    }
                    Switch(checked = isDownloaded, onCheckedChange = { isDownloaded = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF4CAF50),
                            uncheckedThumbColor = Color.White, uncheckedTrackColor = Color(0xFF3A3A55)))
                }
            }

            // 图片
            Text("图片", fontSize = 14.sp, color = Color(0xFF8888AA), fontWeight = FontWeight.Medium)
            MixedImageGrid(keptPaths = keptPaths, newUris = newUris, onAddClick = { showImgSrc = true },
                onRemoveKept = { keptPaths = keptPaths - it }, onRemoveNew = { newUris = newUris - it })
            Text("图片将复制到应用私有存储（最多9张）", fontSize = 11.sp, color = Color(0xFF55556A))

            Spacer(modifier = Modifier.height(4.dp))
            Button(onClick = {
                if (name.isBlank()) nameError = true
                else onConfirm(name, url, isDownloaded, remarks, keptPaths, newUris, selectedCat)
            }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C63FF)), enabled = !isLoading) {
                if (isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                else {
                    Icon(if (isEdit) Icons.Default.Save else Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isEdit) "保存修改" else "保存笔记", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    if (showImgSrc) {
        ImageSourceDialog(onGallery = { showImgSrc = false; galleryLauncher.launch("image/*") },
            onCamera  = { showImgSrc = false; launchCamera() }, onDismiss = { showImgSrc = false })
    }
    if (showCatPicker) {
        CategoryPickerDialog(tree = categoryTree, selectedId = selectedCat,
            onSelect = { selectedCat = it; showCatPicker = false }, onDismiss = { showCatPicker = false })
    }
}

// ═══════════════════════════════════════
// 分类选择弹窗（新增/编辑笔记时使用）
// ═══════════════════════════════════════
@Composable
fun CategoryPickerDialog(tree: List<CategoryNode>, selectedId: Long?, onSelect: (Long?) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, containerColor = Color(0xFF1E1E2E), shape = RoundedCornerShape(16.dp),
        title = { Text("选择分类", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                // 未分类选项
                Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    .background(if (selectedId == null) Color(0xFF6C63FF).copy(0.18f) else Color.Transparent)
                    .clickable { onSelect(null) }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.FolderOff, null, tint = Color(0xFF8888AA), modifier = Modifier.size(16.dp))
                    Text("未分类", color = if (selectedId == null) Color.White else Color(0xFFBBBBCC), fontSize = 14.sp)
                    if (selectedId == null) { Spacer(modifier = Modifier.weight(1f)); Icon(Icons.Default.Check, null, tint = Color(0xFF6C63FF), modifier = Modifier.size(16.dp)) }
                }
                Divider(color = Color(0xFF2A2A38), modifier = Modifier.padding(vertical = 4.dp))
                @Composable
                fun renderNodes(nodes: List<CategoryNode>) {
                    nodes.forEach { node ->
                        Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .background(if (selectedId == node.entity.id) Color(0xFF6C63FF).copy(0.18f) else Color.Transparent)
                            .clickable { onSelect(node.entity.id) }
                            .padding(start = (12 + node.depth * 18).dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(if (node.children.isEmpty()) Icons.Default.Label else Icons.Default.Folder, null,
                                tint = if (selectedId == node.entity.id) Color(0xFF6C63FF) else Color(0xFF8888AA), modifier = Modifier.size(15.dp))
                            Text(node.entity.name, color = if (selectedId == node.entity.id) Color.White else Color(0xFFBBBBCC), fontSize = 14.sp, modifier = Modifier.weight(1f))
                            if (selectedId == node.entity.id) Icon(Icons.Default.Check, null, tint = Color(0xFF6C63FF), modifier = Modifier.size(15.dp))
                        }
                        renderNodes(node.children)
                    }
                }
                renderNodes(tree)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = Color(0xFF8888AA)) } }
    )
}

// ═══════════════════════════════════════
// 图片网格（旧路径 + 新URI 混合）
// ═══════════════════════════════════════
@Composable
fun MixedImageGrid(keptPaths: List<String>, newUris: List<Uri>,
                   onAddClick: () -> Unit, onRemoveKept: (String) -> Unit, onRemoveNew: (Uri) -> Unit) {
    data class ImgItem(val data: Any, val isKept: Boolean, val key: Any)
    val items: List<ImgItem?> = buildList {
        keptPaths.forEach { add(ImgItem(File(it), true, it)) }
        newUris.forEach   { add(ImgItem(it, false, it)) }
        if (keptPaths.size + newUris.size < 9) add(null)
    }
    val cols = 3; val rows = (items.size + cols - 1) / cols
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(rows) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(cols) { col ->
                    val idx = row * cols + col
                    Box(modifier = Modifier.weight(1f)) {
                        when {
                            idx >= items.size -> Spacer(modifier = Modifier.aspectRatio(1f))
                            items[idx] == null -> AddImagePlaceholder(onClick = onAddClick)
                            else -> {
                                val item = items[idx]!!
                                Box(modifier = Modifier.aspectRatio(1f)) {
                                    AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(item.data).crossfade(true).build(),
                                        contentDescription = null, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                                    IconButton(onClick = { if (item.isKept) onRemoveKept(item.key as String) else onRemoveNew(item.key as Uri) },
                                        modifier = Modifier.align(Alignment.TopEnd).padding(2.dp).size(24.dp).clip(CircleShape).background(Color.Black.copy(0.6f))) {
                                        Icon(Icons.Default.Close, "移除", tint = Color.White, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ImageSourceDialog(onGallery: () -> Unit, onCamera: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, containerColor = Color(0xFF252535),
        title = { Text("添加图片", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(onClick = onGallery, shape = RoundedCornerShape(12.dp), color = Color(0xFF1E1E2E)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.PhotoLibrary, null, tint = Color(0xFF6C63FF), modifier = Modifier.size(24.dp))
                        Text("从相册选择", color = Color.White, fontSize = 15.sp)
                    }
                }
                Surface(onClick = onCamera, shape = RoundedCornerShape(12.dp), color = Color(0xFF1E1E2E)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.CameraAlt, null, tint = Color(0xFF6C63FF), modifier = Modifier.size(24.dp))
                        Text("拍照", color = Color.White, fontSize = 15.sp)
                    }
                }
            }
        },
        confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = Color(0xFF8888AA)) } })
}

@Composable
fun AddImagePlaceholder(onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.aspectRatio(1f), shape = RoundedCornerShape(10.dp),
        color = Color(0xFF252540), border = BorderStroke(1.dp, Color(0xFF3A3A55))) {
        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.Default.AddPhotoAlternate, "添加图片", tint = Color(0xFF6C63FF), modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text("添加图片", fontSize = 11.sp, color = Color(0xFF6655CC))
        }
    }
}

@Composable
fun NoteTextField(value: String, onValueChange: (String) -> Unit, label: String, placeholder: String,
                  isError: Boolean = false, errorMessage: String = "", singleLine: Boolean = true, minLines: Int = 1,
                  leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    Column {
        OutlinedTextField(value = value, onValueChange = onValueChange, modifier = Modifier.fillMaxWidth(),
            label = { Text(label, fontSize = 13.sp) }, placeholder = { Text(placeholder, color = Color(0xFF555566), fontSize = 13.sp) },
            singleLine = singleLine, minLines = minLines, isError = isError, shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF6C63FF), unfocusedBorderColor = Color(0xFF3A3A55),
                errorBorderColor = Color(0xFFFF5252), focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                cursorColor = Color(0xFF6C63FF), focusedContainerColor = Color(0xFF1E1E30), unfocusedContainerColor = Color(0xFF1A1A28),
                focusedLabelColor = Color(0xFF6C63FF), unfocusedLabelColor = Color(0xFF8888AA)),
            leadingIcon = leadingIcon?.let { { Icon(it, null, tint = Color(0xFF6655CC), modifier = Modifier.size(18.dp)) } })
        if (isError && errorMessage.isNotEmpty())
            Text(errorMessage, color = Color(0xFFFF5252), fontSize = 11.sp, modifier = Modifier.padding(start = 16.dp, top = 4.dp))
    }
}

// ═══════════════════════════════════════
// 删除确认
// ═══════════════════════════════════════
@Composable
fun DeleteConfirmDialog(noteName: String, imageCount: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, containerColor = Color(0xFF1E1E2E), shape = RoundedCornerShape(20.dp),
        icon = { Box(modifier = Modifier.size(52.dp).clip(CircleShape).background(Color(0xFFFF4444).copy(0.15f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.DeleteForever, null, tint = Color(0xFFFF4444), modifier = Modifier.size(28.dp)) } },
        title = { Text("确认删除", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
        text = { Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Text("「$noteName」", color = Color.White, fontWeight = FontWeight.Medium, fontSize = 15.sp, textAlign = TextAlign.Center)
            Text("将被永久删除，此操作无法撤销。", color = Color(0xFF8888AA), fontSize = 13.sp, textAlign = TextAlign.Center)
            if (imageCount > 0) Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFFF4444).copy(0.08f)) {
                Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Image, null, tint = Color(0xFFFF7777), modifier = Modifier.size(14.dp))
                    Text("同时删除 $imageCount 张关联图片", color = Color(0xFFFF7777), fontSize = 12.sp)
                }
            }
        }},
        confirmButton = { Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4444)), shape = RoundedCornerShape(10.dp)) { Text("删除", color = Color.White, fontWeight = FontWeight.SemiBold) } },
        dismissButton = { OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF8888AA)), border = BorderStroke(1.dp, Color(0xFF3A3A55))) { Text("取消") } })
}

// ═══════════════════════════════════════
// 高亮 Text
// ═══════════════════════════════════════
@Composable
fun HighlightText(text: String, query: String, baseColor: Color,
                  fontSize: androidx.compose.ui.unit.TextUnit, maxLines: Int = Int.MAX_VALUE,
                  fontWeight: FontWeight = FontWeight.Normal, modifier: Modifier = Modifier) {
    if (query.isBlank()) {
        Text(text, color = baseColor, fontSize = fontSize, fontWeight = fontWeight, maxLines = maxLines,
            overflow = TextOverflow.Ellipsis, modifier = modifier, lineHeight = (fontSize.value * 1.5f).sp); return
    }
    val annotated = buildAnnotatedString {
        val lower = text.lowercase(); val lq = query.lowercase(); var c = 0
        while (c < text.length) {
            val i = lower.indexOf(lq, c)
            if (i == -1) { withStyle(androidx.compose.ui.text.SpanStyle(color = baseColor, fontWeight = fontWeight)) { append(text.substring(c)) }; break }
            if (i > c) withStyle(androidx.compose.ui.text.SpanStyle(color = baseColor, fontWeight = fontWeight)) { append(text.substring(c, i)) }
            withStyle(androidx.compose.ui.text.SpanStyle(color = Color(0xFFFFD54F), background = Color(0xFFFFD54F).copy(0.2f), fontWeight = FontWeight.SemiBold)) { append(text.substring(i, i + lq.length)) }
            c = i + lq.length
        }
    }
    Text(annotated, fontSize = fontSize, maxLines = maxLines, overflow = TextOverflow.Ellipsis, modifier = modifier, lineHeight = (fontSize.value * 1.5f).sp)
}

// ═══════════════════════════════════════
// 页码指示器
// ═══════════════════════════════════════
@Composable
fun PageIndicator(currentPage: Int, pageCount: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(pageCount.coerceAtMost(8)) { i ->
            Box(modifier = Modifier.animateContentSize(spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                .size(width = if (i == currentPage) 24.dp else 8.dp, height = 8.dp).clip(CircleShape)
                .background(if (i == currentPage) Color(0xFF6C63FF) else Color.White.copy(0.3f)))
        }
        if (pageCount > 8) Text("${currentPage + 1}/$pageCount", fontSize = 11.sp, color = Color.White.copy(0.5f))
    }
}

// ═══════════════════════════════════════
// 空状态
// ═══════════════════════════════════════
@Composable
fun EmptyStateView(filterState: FilterState, searchQuery: String = "") {
    val isSearch = searchQuery.isNotBlank()
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.padding(32.dp)) {
        Box(modifier = Modifier.size(120.dp).clip(CircleShape)
            .background(Brush.radialGradient(listOf(Color(0xFF6C63FF).copy(0.2f), Color.Transparent), radius = 200f)),
            contentAlignment = Alignment.Center) {
            Icon(if (isSearch) Icons.Default.ManageSearch else Icons.Default.SearchOff, null,
                tint = Color(0xFF6C63FF).copy(0.6f), modifier = Modifier.size(48.dp))
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(if (isSearch) "未找到匹配结果" else "没有符合条件的笔记", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Color(0xFF8888AA))
        Spacer(modifier = Modifier.height(8.dp))
        Text(when { isSearch -> "「$searchQuery」无匹配，换个关键词试试"; !filterState.showAll -> "尝试调整顶部筛选条件"; else -> "点击右下角 + 按钮添加新笔记" },
            fontSize = 14.sp, color = Color(0xFF555566), textAlign = TextAlign.Center)
    }
}
