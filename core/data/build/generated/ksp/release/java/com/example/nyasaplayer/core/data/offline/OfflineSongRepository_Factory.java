package com.example.nyasaplayer.core.data.offline;

import com.example.nyasaplayer.core.data.local.dao.SongDao;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast",
    "deprecation"
})
public final class OfflineSongRepository_Factory implements Factory<OfflineSongRepository> {
  private final Provider<SongDao> songDaoProvider;

  public OfflineSongRepository_Factory(Provider<SongDao> songDaoProvider) {
    this.songDaoProvider = songDaoProvider;
  }

  @Override
  public OfflineSongRepository get() {
    return newInstance(songDaoProvider.get());
  }

  public static OfflineSongRepository_Factory create(Provider<SongDao> songDaoProvider) {
    return new OfflineSongRepository_Factory(songDaoProvider);
  }

  public static OfflineSongRepository newInstance(SongDao songDao) {
    return new OfflineSongRepository(songDao);
  }
}
