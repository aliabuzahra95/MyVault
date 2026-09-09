package com.myvault.app.ui.screens

import com.myvault.app.ui.model.*
import com.myvault.app.ui.viewmodel.*
import org.junit.Assert.*
import org.junit.Test

class LibraryOrderingTest {
    private fun file(id:String,order:Int,created:Long=0,opened:Long=0) = LibraryFileItem(id,id,"PDF","1 KB","","application/pdf","",orderIndex=order,createdAt=created,lastOpenedAt=opened)
    @Test fun mixedManualAndAutomaticViewsPreserveStoredOrdering() {
        val folders=listOf(LibraryFolderItem("folder","Folder",2,orderIndex=1,createdAt=10,
            files=listOf(file("nested",0)),children=listOf(LibraryFolderItem("child","Child",0,orderIndex=1))))
        val files=listOf(file("z",0,30,40),file("a",2,20,50))
        fun tree(mode:StudySortMode)=libraryOrderTree(folders,files,mode,emptyMap())
        assertEquals(listOf("z","folder","a"),tree(StudySortMode.Manual).map { it.id })
        assertEquals(listOf("a","folder","z"),tree(StudySortMode.Alphabetical).map { it.id })
        assertEquals(listOf("z","a","folder"),tree(StudySortMode.Created).map { it.id })
        assertEquals(listOf("a","z","folder"),tree(StudySortMode.Opened).map { it.id })
        assertEquals(listOf("z","folder","a"),tree(StudySortMode.Manual).map { it.id })
        assertEquals(listOf("nested","child"),tree(StudySortMode.Manual)[1].children.map { it.id })
        assertFalse(librarySortModes.contains(StudySortMode.Modified))
        assertEquals(tree(StudySortMode.Manual),tree(StudySortMode.Manual).moveStudySibling("nested","a"))
    }
}
