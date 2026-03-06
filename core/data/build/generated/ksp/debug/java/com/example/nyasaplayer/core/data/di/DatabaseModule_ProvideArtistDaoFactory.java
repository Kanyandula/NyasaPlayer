package com.example.nyasaplayer.core.data.di;

import com.example.nyasaplayer.core.data.local.NyasaDatabase;
import com.example.nyasaplayer.core.data.local.dao.ArtistDao;
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
public final class DatabaseModule_ProvideArtistDaoFactory implements Factory<ArtistDao> {
  private final Provider<NyasaDatabase> databaseProvider;

  public DatabaseModule_ProvideArtistDaoFactory(Provider<NyasaDatabase> databaseProvider) {
    this.databaseProvider = databaseProvider;
  }

  @Override
  public ArtistDao get() {
    return provideArtistDao(databaseProvider.get());
  }

  public static DatabaseModule_ProvideArtistDaoFactory create(
      Provider<NyasaDatabase> databaseProvider) {
    return new DatabaseModule_ProvideArtistDaoFactory(databaseProvider);
  }

  public static ArtistDao provideArtistDao(NyasaDatabase database) {
    return Preconditions.checkNotNullFromProvides(DatabaseModule.INSTANCE.provideArtistDao(database));
  }
}
