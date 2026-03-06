package com.example.nyasaplayer.core.data.offline;

import com.example.nyasaplayer.core.data.local.dao.GenreDao;
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
public final class OfflineGenreRepository_Factory implements Factory<OfflineGenreRepository> {
  private final Provider<GenreDao> genreDaoProvider;

  public OfflineGenreRepository_Factory(Provider<GenreDao> genreDaoProvider) {
    this.genreDaoProvider = genreDaoProvider;
  }

  @Override
  public OfflineGenreRepository get() {
    return newInstance(genreDaoProvider.get());
  }

  public static OfflineGenreRepository_Factory create(Provider<GenreDao> genreDaoProvider) {
    return new OfflineGenreRepository_Factory(genreDaoProvider);
  }

  public static OfflineGenreRepository newInstance(GenreDao genreDao) {
    return new OfflineGenreRepository(genreDao);
  }
}
