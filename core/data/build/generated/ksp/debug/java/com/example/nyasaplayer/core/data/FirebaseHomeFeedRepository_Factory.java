package com.example.nyasaplayer.core.data;

import com.google.firebase.firestore.FirebaseFirestore;
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
public final class FirebaseHomeFeedRepository_Factory implements Factory<FirebaseHomeFeedRepository> {
  private final Provider<FirebaseFirestore> firestoreProvider;

  public FirebaseHomeFeedRepository_Factory(Provider<FirebaseFirestore> firestoreProvider) {
    this.firestoreProvider = firestoreProvider;
  }

  @Override
  public FirebaseHomeFeedRepository get() {
    return newInstance(firestoreProvider.get());
  }

  public static FirebaseHomeFeedRepository_Factory create(
      Provider<FirebaseFirestore> firestoreProvider) {
    return new FirebaseHomeFeedRepository_Factory(firestoreProvider);
  }

  public static FirebaseHomeFeedRepository newInstance(FirebaseFirestore firestore) {
    return new FirebaseHomeFeedRepository(firestore);
  }
}
