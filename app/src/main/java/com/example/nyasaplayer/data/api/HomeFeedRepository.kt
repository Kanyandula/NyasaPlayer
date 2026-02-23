package com.example.nyasaplayer.data.api

import com.example.nyasaplayer.models.HomeFeed
import kotlinx.coroutines.flow.Flow

interface HomeFeedRepository {
    fun getHomeFeed(): Flow<HomeFeed?>
}
