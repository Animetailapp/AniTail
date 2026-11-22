package com.anitail.music.viewmodels

data class ArtistAboutSeed(
    val artistId: String,
    val artistName: String,
    val thumbnailUrl: String?,
    val bannerUrl: String?,
    val description: String?,
    val subscriberCountText: String?,
    val monthlyListenerCount: String?,
    val viewCount: String?,
    val catalogTitles: List<String>,
)

object ArtistAboutNavCache {
    private const val MAX_ENTRIES = 24
    private val seeds = LinkedHashMap<String, ArtistAboutSeed>()

    @Synchronized
    fun put(seed: ArtistAboutSeed) {
        seeds.remove(seed.artistId)
        seeds[seed.artistId] = seed
        while (seeds.size > MAX_ENTRIES) {
            val firstKey = seeds.keys.firstOrNull() ?: break
            seeds.remove(firstKey)
        }
    }

    @Synchronized
    fun get(artistId: String): ArtistAboutSeed? = seeds[artistId]
}

