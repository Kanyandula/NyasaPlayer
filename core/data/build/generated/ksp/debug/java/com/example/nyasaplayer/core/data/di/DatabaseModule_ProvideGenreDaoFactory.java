package com.example.nyasaplayer.core.data.di;

import com.example.nyasaplayer.core.data.local.NyasaDatabase;
import com.example.nyasaplayer.core.data.local.dao.GenreDao;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
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
public final class DatabaseModule_ProvideGenreDaoFactory implements Factory<GenreDao> {
  private final Provider<NyasaDatabase> databaseProvider;

  public DatabaseModule_ProvideGenreDaoFactory(Provider<NyasaDatabase> databaseProvider) {
    this.databaseProvider = databaseProvider;
  }

  @Override
  public GenreDao get() {
    return provideGenreDao(databaseProvider.get());
  }

  public static DatabaseModule_ProvideGenreDaoFactory create(
      Provider<NyasaDatabase> databaseProvider) {
    return new DatabaseModule_ProvideGenreDaoFactory(databaseProvider);
  }

  public static GenreDao provideGenreDao(NyasaDatabase database) {
    return Preconditions.checkNotNullFromProvides(DatabaseModule.INSTANCE.provideGenreDao(database));
  }
}
