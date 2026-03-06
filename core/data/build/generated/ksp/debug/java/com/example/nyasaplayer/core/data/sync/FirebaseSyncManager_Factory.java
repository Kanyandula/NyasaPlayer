package com.example.nyasaplayer.core.data.sync;

import com.example.nyasaplayer.core.data.local.dao.ArtistDao;
import com.example.nyasaplayer.core.data.local.dao.GenreDao;
import com.example.nyasaplayer.core.data.local.dao.SongDao;
import com.google.firebase.firestore.FirebaseFirestore;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
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
public final class FirebaseSyncManager_Factory implements Factory<FirebaseSyncManager> {
  private final Provider<FirebaseFirestore> firestoreProvider;

  private final Provider<SongDao> songDaoProvider;

  private final Provider<ArtistDao> artistDaoProvider;

  private final Provider<GenreDao> genreDaoProvider;

  public FirebaseSyncManager_Factory(Provider<FirebaseFirestore> firestoreProvider,
      Provider<SongDao> songDaoProvider, Provider<ArtistDao> artistDaoProvider,
      Provider<GenreDao> genreDaoProvider) {
    this.firestoreProvider = firestoreProvider;
    this.songDaoProvider = songDaoProvider;
    this.artistDaoProvider = artistDaoProvider;
    this.genreDaoProvider = genreDaoProvider;
  }

  @Override
  public FirebaseSyncManager get() {
    return newInstance(firestoreProvider.get(), songDaoProvider.get(), artistDaoProvider.get(), genreDaoProvider.get());
  }

  public static FirebaseSyncManager_Factory create(Provider<FirebaseFirestore> firestoreProvider,
      Provider<SongDao> songDaoProvider, Provider<ArtistDao> artistDaoProvider,
      Provider<GenreDao> genreDaoProvider) {
    return new FirebaseSyncManager_Factory(firestoreProvider, songDaoProvider, artistDaoProvider, genreDaoProvider);
  }

  public static FirebaseSyncManager newInstance(FirebaseFirestore firestore, SongDao songDao,
      ArtistDao artistDao, GenreDao genreDao) {
    return new FirebaseSyncManager(firestore, songDao, artistDao, genreDao);
  }
}
