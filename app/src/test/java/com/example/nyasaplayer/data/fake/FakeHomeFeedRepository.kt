package com.example.nyasaplayer.data.fake

import com.example.nyasaplayer.data.api.HomeFeedRepository
import com.example.nyasaplayer.models.HomeFeed
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeHomeFeedRepository : HomeFeedRepository {

    val homeFeed = MutableStateFlow<HomeFeed?>(null)

    override fun getHomeFeed(): Flow<HomeFeed?> = homeFeed
}
