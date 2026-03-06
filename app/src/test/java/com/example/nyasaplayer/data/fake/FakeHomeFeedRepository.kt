package com.example.nyasaplayer.data.fake

import com.example.nyasaplayer.core.common.models.HomeFeed
import com.example.nyasaplayer.core.data.api.HomeFeedRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeHomeFeedRepository : HomeFeedRepository {

    val homeFeed = MutableStateFlow<HomeFeed?>(null)

    override fun getHomeFeed(): Flow<HomeFeed?> = homeFeed
}
