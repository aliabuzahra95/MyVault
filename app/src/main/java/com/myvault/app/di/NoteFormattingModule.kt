package com.myvault.app.di

import com.myvault.app.data.formatting.DefaultNoteFormattingProviderGateway
import com.myvault.app.data.formatting.DefaultNoteFormattingTrace
import com.myvault.app.data.formatting.NativeNoteFormattingGenerator
import com.myvault.app.data.formatting.NoteFormattingGenerator
import com.myvault.app.data.formatting.NoteFormattingProviderGateway
import com.myvault.app.data.formatting.NoteFormattingTrace
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NoteFormattingModule {
    @Binds
    @Singleton
    internal abstract fun bindNoteFormattingGenerator(
        implementation: NativeNoteFormattingGenerator,
    ): NoteFormattingGenerator

    @Binds
    @Singleton
    internal abstract fun bindNoteFormattingProviderGateway(
        implementation: DefaultNoteFormattingProviderGateway,
    ): NoteFormattingProviderGateway

    @Binds
    @Singleton
    internal abstract fun bindNoteFormattingTrace(
        implementation: DefaultNoteFormattingTrace,
    ): NoteFormattingTrace
}
