package com.example.nyasaplayer.core.data.offline;

import com.example.nyasaplayer.core.data.local.dao.ArtistDao;
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
public final class OfflineArtistRepository_Factory implements Factory<OfflineArtistRepository> {
  private final Provider<ArtistDao> artistDaoProvider;

  public OfflineArtistRepository_Factory(Provider<ArtistDao> artistDaoProvider) {
    this.artistDaoProvider = artistDaoProvider;
  }

  @Override
  public OfflineArtistRepository get() {
    return newInstance(artistDaoProvider.get());
  }

  public static OfflineArtistRepository_Factory create(Provider<ArtistDao> artistDaoProvider) {
    return new OfflineArtistRepository_Factory(artistDaoProvider);
  }

  public static OfflineArtistRepository newInstance(ArtistDao artistDao) {
    return new OfflineArtistRepository(artistDao);
  }
}
