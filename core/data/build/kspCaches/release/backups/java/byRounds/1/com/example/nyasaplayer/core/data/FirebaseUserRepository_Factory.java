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
public final class FirebaseUserRepository_Factory implements Factory<FirebaseUserRepository> {
  private final Provider<FirebaseFirestore> firestoreProvider;

  public FirebaseUserRepository_Factory(Provider<FirebaseFirestore> firestoreProvider) {
    this.firestoreProvider = firestoreProvider;
  }

  @Override
  public FirebaseUserRepository get() {
    return newInstance(firestoreProvider.get());
  }

  public static FirebaseUserRepository_Factory create(
      Provider<FirebaseFirestore> firestoreProvider) {
    return new FirebaseUserRepository_Factory(firestoreProvider);
  }

  public static FirebaseUserRepository newInstance(FirebaseFirestore firestore) {
    return new FirebaseUserRepository(firestore);
  }
}
