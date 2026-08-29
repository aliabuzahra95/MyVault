package com.myvault.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myvault.app.data.local.entity.CourseConceptCardEntity
import com.myvault.app.data.local.entity.CourseEntity
import com.myvault.app.data.repository.CourseRepository
import com.myvault.app.data.repository.FolderRepository
import com.myvault.app.data.repository.FolderStickyNoteRepository
import com.myvault.app.data.repository.NoteRepository
import com.myvault.app.data.repository.courseFolderMode
import com.myvault.app.data.preferences.VaultPreferences
import com.myvault.app.ui.components.VaultTreeItemType
import com.myvault.app.ui.components.VaultTreeItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CoursesUiState(
    val courses: List<CourseEntity> = emptyList(),
    val activeCourse: CourseEntity? = null,
    val folderState: FolderUiState = FolderUiState(),
    val concepts: List<CourseConceptCardEntity> = emptyList(),
    val noteCountsByCourse: Map<String, Int> = emptyMap(),
    val conceptCountsByCourse: Map<String, Int> = emptyMap(),
    val treesByCourse: Map<String, List<VaultTreeItem>> = emptyMap(),
    val notePreviewLines: Int = 0,
    val showFullNoteTitles: Boolean = false,
) {
    val continueNoteId: String? get() = activeCourse?.lastOpenedNoteId
    val continueNoteTitle: String?
        get() = continueNoteId?.let { id -> folderState.contents.findNote(id)?.name }
}

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class CoursesViewModel @Inject constructor(
    private val courseRepository: CourseRepository,
    private val folderRepository: FolderRepository,
    private val stickyNoteRepository: FolderStickyNoteRepository,
    private val noteRepository: NoteRepository,
    private val vaultPreferences: VaultPreferences,
) : ViewModel() {
    private val selectedCourseId = MutableStateFlow<String?>(null)
    private val rootFolderId = MutableStateFlow<String?>(null)

    private data class CourseCatalog(
        val courses: List<CourseEntity>,
        val concepts: List<CourseConceptCardEntity>,
        val noteCountsByCourse: Map<String, Int>,
        val conceptCountsByCourse: Map<String, Int>,
        val treesByCourse: Map<String, List<VaultTreeItem>>,
    )

    private val courseTrees = courseRepository.courses.flatMapLatest { courses ->
        if (courses.isEmpty()) {
            flowOf(emptyMap())
        } else {
            combine(
                courses.map { course ->
                    folderRepository.observeWorkspaceTree(courseFolderMode(course.id)).map { tree ->
                        course.id to tree
                    }
                },
            ) { trees -> trees.toMap() }
        }
    }

    private val courseCatalog = combine(
        courseRepository.courses,
        courseRepository.concepts,
        courseTrees,
    ) { courses, concepts, trees ->
        CourseCatalog(
            courses = courses,
            concepts = concepts,
            noteCountsByCourse = courses.associate { course ->
                course.id to trees[course.id].orEmpty().sumOf { it.noteCount() }
            },
            conceptCountsByCourse = concepts.groupingBy { it.courseId }.eachCount(),
            treesByCourse = trees,
        )
    }

    private val folderState = rootFolderId.flatMapLatest { id ->
        if (id == null) {
            flowOf(FolderUiState())
        } else {
            combine(
                folderRepository.observeFolder(id),
                stickyNoteRepository.observeForFolder(id),
                folderRepository.observeFolderContents(id),
                folderRepository.observeWorkspaceTreeForFolder(id),
                vaultPreferences.userPreferences.map { it.expandedFolderIds },
            ) { folder, stickyNotes, contents, workspace, expanded ->
                FolderUiState(folder, stickyNotes, contents, workspace, expanded)
            }
        }
    }

    val uiState: StateFlow<CoursesUiState> = combine(
        courseCatalog,
        selectedCourseId,
        folderState,
        vaultPreferences.userPreferences,
    ) { catalog, requestedId, folder, preferences ->
        val active = catalog.courses.firstOrNull { it.id == requestedId } ?: catalog.courses.firstOrNull()
        CoursesUiState(
            courses = catalog.courses,
            activeCourse = active,
            folderState = folder,
            concepts = catalog.concepts.filter { it.courseId == active?.id },
            noteCountsByCourse = catalog.noteCountsByCourse,
            conceptCountsByCourse = catalog.conceptCountsByCourse,
            treesByCourse = catalog.treesByCourse,
            notePreviewLines = when (preferences.notePreview) {
                "two_lines" -> 2
                "three_lines" -> 3
                else -> 0
            },
            showFullNoteTitles = preferences.showFullNoteTitles,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CoursesUiState())

    init {
        viewModelScope.launch {
            courseRepository.courses.collect { courses ->
                val active = courses.firstOrNull { it.id == selectedCourseId.value } ?: courses.firstOrNull()
                if (active == null) {
                    rootFolderId.value = null
                } else {
                    selectedCourseId.value = active.id
                    rootFolderId.value = courseRepository.ensureWorkspace(active)
                }
            }
        }
    }

    fun selectCourse(id: String) {
        viewModelScope.launch {
            selectedCourseId.value = id
            courseRepository.courses.first().firstOrNull { it.id == id }?.let {
                rootFolderId.value = courseRepository.ensureWorkspace(it)
            }
        }
    }

    fun revealFolderPath(courseId: String, folderIds: List<String>) {
        viewModelScope.launch {
            val course = courseRepository.courses.first().firstOrNull { it.id == courseId } ?: return@launch
            selectedCourseId.value = courseId
            val courseRoot = courseRepository.ensureWorkspace(course)
            rootFolderId.value = courseRoot
            if (folderIds.isNotEmpty()) {
                val expandedIds = vaultPreferences.userPreferences.first().expandedFolderIds.toMutableSet()
                folderIds.forEach { folderId -> expandedIds += "folder:$courseRoot:$folderId" }
                vaultPreferences.setExpandedFolderIds(expandedIds)
            }
        }
    }

    fun createCourse(title: String) = viewModelScope.launch { selectCourse(courseRepository.createCourse(title)) }
    fun renameCourse(id: String, title: String) = viewModelScope.launch { courseRepository.renameCourse(id, title) }
    fun deleteCourse(id: String) = viewModelScope.launch { courseRepository.deleteCourse(id) }

    fun createNote(onCreated: (String) -> Unit) = viewModelScope.launch {
        rootFolderId.value?.let {
            val noteId = noteRepository.createNote(it)
            uiState.value.activeCourse?.id?.let { courseId -> courseRepository.markNoteOpened(courseId, noteId) }
            onCreated(noteId)
        }
    }

    fun createNoteForCourse(courseId: String, onCreated: (String) -> Unit) = viewModelScope.launch {
        val course = courseRepository.courses.first().firstOrNull { it.id == courseId } ?: return@launch
        selectedCourseId.value = courseId
        val courseRoot = courseRepository.ensureWorkspace(course)
        rootFolderId.value = courseRoot
        val noteId = noteRepository.createNote(courseRoot)
        courseRepository.markNoteOpened(courseId, noteId)
        onCreated(noteId)
    }

    fun createNoteInFolder(folderId: String, onCreated: (String) -> Unit) = viewModelScope.launch {
        val noteId = noteRepository.createNote(folderId)
        uiState.value.activeCourse?.id?.let { courseId -> courseRepository.markNoteOpened(courseId, noteId) }
        onCreated(noteId)
    }

    fun createNoteInFolderForCourse(courseId: String, folderId: String, onCreated: (String) -> Unit) = viewModelScope.launch {
        selectedCourseId.value = courseId
        val noteId = noteRepository.createNote(folderId)
        courseRepository.markNoteOpened(courseId, noteId)
        onCreated(noteId)
    }

    fun createSubfolder(name: String, description: String?) = viewModelScope.launch {
        rootFolderId.value?.let { folderRepository.createFolder(it, name, description = description) }
    }

    fun createSubfolderInFolder(parentId: String, name: String, description: String?) = viewModelScope.launch {
        folderRepository.createFolder(parentId, name, description = description)
    }

    fun createSubfolderForCourse(
        courseId: String,
        parentId: String?,
        name: String,
        description: String? = null,
    ) = viewModelScope.launch {
        val course = courseRepository.courses.first().firstOrNull { it.id == courseId } ?: return@launch
        selectedCourseId.value = courseId
        val courseRoot = courseRepository.ensureWorkspace(course)
        rootFolderId.value = courseRoot
        folderRepository.createFolder(parentId ?: courseRoot, name, description = description)
    }

    fun updateChildFolder(id: String, name: String, description: String?) = viewModelScope.launch {
        folderRepository.updateFolderDetails(id, name, description)
    }

    fun moveChildFolder(id: String, parentId: String?) = viewModelScope.launch {
        folderRepository.moveFolder(id, parentId)
    }

    fun moveChildFolderForCourse(courseId: String, id: String, parentId: String?) = viewModelScope.launch {
        val course = courseRepository.courses.first().firstOrNull { it.id == courseId } ?: return@launch
        val courseRoot = courseRepository.ensureWorkspace(course)
        folderRepository.moveFolder(id, parentId ?: courseRoot)
    }

    fun deleteChildFolder(id: String) = viewModelScope.launch {
        folderRepository.deleteFolderTree(id)
    }

    fun setFolderExpanded(id: String, expanded: Boolean) = viewModelScope.launch {
        val expandedIds = vaultPreferences.userPreferences.first().expandedFolderIds.toMutableSet()
        val key = "folder:${rootFolderId.value}:$id"
        if (expanded) expandedIds += key else expandedIds -= key
        vaultPreferences.setExpandedFolderIds(expandedIds)
    }

    fun moveItemInOrder(id: String, type: VaultTreeItemType, direction: Int) = viewModelScope.launch {
        folderRepository.moveTreeItemWithinSiblings(id, type, direction)
    }

    fun renameNote(id: String, title: String) = viewModelScope.launch { noteRepository.updateTitle(id, title) }
    fun moveNote(id: String, folderId: String?) = viewModelScope.launch { noteRepository.moveNote(id, folderId) }
    fun moveNoteForCourse(courseId: String, id: String, folderId: String?) = viewModelScope.launch {
        val course = courseRepository.courses.first().firstOrNull { it.id == courseId } ?: return@launch
        val courseRoot = courseRepository.ensureWorkspace(course)
        noteRepository.moveNote(id, folderId ?: courseRoot)
    }
    fun moveNoteToMode(id: String, mode: String) = viewModelScope.launch { noteRepository.moveNoteToMode(id, mode) }
    fun deleteNote(id: String) = viewModelScope.launch { noteRepository.deleteNote(id) }
    fun setNotePinned(id: String, pinned: Boolean) = viewModelScope.launch { noteRepository.setPinned(id, pinned) }
    fun setNoteFolderPinned(id: String, pinned: Boolean) = viewModelScope.launch { noteRepository.setFolderPinned(id, pinned) }
    fun setNoteFavourite(id: String, favourite: Boolean) = viewModelScope.launch { noteRepository.setFavourite(id, favourite) }

    fun createSubNote(parentId: String, onCreated: (String) -> Unit) = viewModelScope.launch {
        val parent = noteRepository.getNote(parentId) ?: return@launch
        onCreated(noteRepository.createNote(parent.folderId, "Untitled sub-note", parent.id))
    }

    fun openNote(id: String) = viewModelScope.launch {
        uiState.value.activeCourse?.id?.let { courseRepository.markNoteOpened(it, id) }
    }

    fun createSticky(text: String) = viewModelScope.launch { rootFolderId.value?.let { stickyNoteRepository.create(it, text) } }
    fun updateSticky(id: String, text: String) = viewModelScope.launch { stickyNoteRepository.update(id, text) }
    fun deleteSticky(id: String) = viewModelScope.launch { stickyNoteRepository.delete(id) }

    fun createConcept(term: String, arabic: String?, definition: String, details: String?) = viewModelScope.launch {
        uiState.value.activeCourse?.id?.let { courseRepository.createConcept(it, term, arabic, definition, details) }
    }

    fun saveConcept(id: String, term: String, arabic: String?, definition: String, details: String?) =
        viewModelScope.launch { courseRepository.saveConcept(id, term, arabic, definition, details) }

    fun deleteConcept(id: String) = viewModelScope.launch { courseRepository.deleteConcept(id) }
}

private fun List<com.myvault.app.ui.components.VaultTreeItem>.findNote(id: String): com.myvault.app.ui.components.VaultTreeItem? {
    forEach { item ->
        if (item.id == id && item.type == VaultTreeItemType.Note) return item
        item.children.findNote(id)?.let { return it }
    }
    return null
}

private fun com.myvault.app.ui.components.VaultTreeItem.noteCount(): Int =
    (if (type == VaultTreeItemType.Note) 1 else 0) + children.sumOf { it.noteCount() }
