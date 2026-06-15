package com.example.myapp.data.local

/** Limite pentru model offline (.task ekv1280 = cache KV 1280 tokeni). */
object OfflineLlmModelConfig {
    const val MODEL_KV_CACHE_TOKENS = 1280

    const val MIN_PAGE_WORDS = 40
    const val MIN_PAGE_CHARS = 250

    /** Chunk trimis la model per întrebare offline (mai mic = inferență mai rapidă). */
    const val CHUNK_WORDS_MIN = 40
    const val CHUNK_WORDS_MAX = 70

    /** ~[CHUNK_WORDS_MAX] cuvinte — folosit de [LocalLlmTextPreprocessor]. */
    const val MAX_PAGE_CHARS_FOR_AI = CHUNK_WORDS_MAX * 6

    /** Pool candidați: top N pagini după scor, apoi amestecate. */
    const val OFFLINE_CANDIDATE_POOL_SIZE = 30

    /** Legacy — selectTopPagesForLocalModel. */
    const val MAX_OFFLINE_PAGES = 3
}
