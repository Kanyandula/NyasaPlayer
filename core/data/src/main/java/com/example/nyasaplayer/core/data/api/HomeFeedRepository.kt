package com.example.nyasaplayer.core.data.api

import com.example.nyasaplayer.core.common.models.HomeFeed
import kotlinx.coroutines.flow.Flow

interface HomeFeedRepository {
    fun getHomeFeed(): Flow<HomeFeed?>
}
