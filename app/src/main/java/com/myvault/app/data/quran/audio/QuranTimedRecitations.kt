package com.myvault.app.data.quran.audio

/** Provider IDs are separate namespaces; these mappings were checked against both catalogues. */
internal data class QuranTimedRecitation(
    val reciterId: Int,
    val folder: String,
    val chapterReciterId: Int? = null,
    val mp3QuranRead: Int? = null,
) {
    val cacheKey get() = chapterReciterId?.let { "chapter-$it" } ?: "mp3quran-$mp3QuranRead"
}

internal object QuranTimedRecitations {
    // Reserved application IDs do not masquerade as Quran Foundation ayah-recitation IDs.
    const val SAAD_AL_GHAMDI = 1_000_030
    const val YASSER_AL_DOSSARI = 1_000_092
    const val FARES_ABBAD = 1_000_081
    val additionalReciters = listOf(
        AudioReciterUiModel(YASSER_AL_DOSSARI, "Yasser al-Dossari"),
        AudioReciterUiModel(SAAD_AL_GHAMDI, "Saad al-Ghamdi"),
        AudioReciterUiModel(FARES_ABBAD, "Fares Abbad"),
    )
    val requestedReciters = listOf(
        AudioReciterUiModel(1, "Abdul Basit (Mujawwad)"),
        AudioReciterUiModel(2, "Abdul Basit (Murattal)"),
        AudioReciterUiModel(4, "Abu Bakr al-Shatri"),
        AudioReciterUiModel(10, "Sa'ud ash-Shuraym"),
        AudioReciterUiModel(11, "Muhammad al-Tablawi"),
    ) + additionalReciters

    fun includeRequested(catalog: List<AudioReciterUiModel>) =
        (catalog + requestedReciters).distinctBy { it.id }
    private fun chapter(id: Int, path: String) = QuranTimedRecitation(id,
        "https://download.quranicaudio.com/qdc/$path/", chapterReciterId = id)
    val sources = listOf(
        chapter(1, "abdul_baset/mujawwad"),
        QuranTimedRecitation(2, "https://server7.mp3quran.net/basit/", mp3QuranRead = 53),
        chapter(4, "abu_bakr_shatri/murattal"),
        chapter(6, "khalil_al_husary/murattal"),
        chapter(7, "mishari_al_afasy/murattal"),
        chapter(9, "siddiq_minshawi/murattal"),
        QuranTimedRecitation(10, "https://server7.mp3quran.net/shur/", mp3QuranRead = 31),
        QuranTimedRecitation(11, "https://server12.mp3quran.net/tblawi/", mp3QuranRead = 106),
        QuranTimedRecitation(SAAD_AL_GHAMDI, "https://server7.mp3quran.net/s_gmd/", mp3QuranRead = 30),
        QuranTimedRecitation(YASSER_AL_DOSSARI, "https://server11.mp3quran.net/yasser/", mp3QuranRead = 92),
        QuranTimedRecitation(FARES_ABBAD, "https://server8.mp3quran.net/frs_a/", mp3QuranRead = 81),
    ).associateBy { it.reciterId }
}
