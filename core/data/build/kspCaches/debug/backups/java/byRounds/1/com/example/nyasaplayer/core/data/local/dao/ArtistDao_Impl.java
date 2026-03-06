package com.example.nyasaplayer.core.data.local.dao;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.EntityUpsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomDatabaseKt;
import androidx.room.RoomSQLiteQuery;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.room.util.StringUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.example.nyasaplayer.core.data.local.StringListConverter;
import com.example.nyasaplayer.core.data.local.entity.ArtistEntity;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.StringBuilder;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class ArtistDao_Impl implements ArtistDao {
  private final RoomDatabase __db;

  private final EntityUpsertionAdapter<ArtistEntity> __upsertionAdapterOfArtistEntity;

  private final StringListConverter __stringListConverter = new StringListConverter();

  public ArtistDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__upsertionAdapterOfArtistEntity = new EntityUpsertionAdapter<ArtistEntity>(new EntityInsertionAdapter<ArtistEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT INTO `artists` (`id`,`name`,`image_url`,`genres`,`popularity`,`song_count`) VALUES (?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final ArtistEntity entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getName());
        statement.bindString(3, entity.getImageUrl());
        final String _tmp = __stringListConverter.fromStringList(entity.getGenres());
        statement.bindString(4, _tmp);
        statement.bindLong(5, entity.getPopularity());
        statement.bindLong(6, entity.getSongCount());
      }
    }, new EntityDeletionOrUpdateAdapter<ArtistEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE `artists` SET `id` = ?,`name` = ?,`image_url` = ?,`genres` = ?,`popularity` = ?,`song_count` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final ArtistEntity entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getName());
        statement.bindString(3, entity.getImageUrl());
        final String _tmp = __stringListConverter.fromStringList(entity.getGenres());
        statement.bindString(4, _tmp);
        statement.bindLong(5, entity.getPopularity());
        statement.bindLong(6, entity.getSongCount());
        statement.bindString(7, entity.getId());
      }
    });
  }

  @Override
  public Object sync(final List<ArtistEntity> artists,
      final Continuation<? super Unit> $completion) {
    return RoomDatabaseKt.withTransaction(__db, (__cont) -> ArtistDao.DefaultImpls.sync(ArtistDao_Impl.this, artists, __cont), $completion);
  }

  @Override
  public Object upsertAll(final List<ArtistEntity> artists,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __upsertionAdapterOfArtistEntity.upsert(artists);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<ArtistEntity>> getAll() {
    final String _sql = "SELECT * FROM artists";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"artists"}, new Callable<List<ArtistEntity>>() {
      @Override
      @NonNull
      public List<ArtistEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfImageUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "image_url");
          final int _cursorIndexOfGenres = CursorUtil.getColumnIndexOrThrow(_cursor, "genres");
          final int _cursorIndexOfPopularity = CursorUtil.getColumnIndexOrThrow(_cursor, "popularity");
          final int _cursorIndexOfSongCount = CursorUtil.getColumnIndexOrThrow(_cursor, "song_count");
          final List<ArtistEntity> _result = new ArrayList<ArtistEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ArtistEntity _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final String _tmpImageUrl;
            _tmpImageUrl = _cursor.getString(_cursorIndexOfImageUrl);
            final List<String> _tmpGenres;
            final String _tmp;
            _tmp = _cursor.getString(_cursorIndexOfGenres);
            _tmpGenres = __stringListConverter.toStringList(_tmp);
            final int _tmpPopularity;
            _tmpPopularity = _cursor.getInt(_cursorIndexOfPopularity);
            final int _tmpSongCount;
            _tmpSongCount = _cursor.getInt(_cursorIndexOfSongCount);
            _item = new ArtistEntity(_tmpId,_tmpName,_tmpImageUrl,_tmpGenres,_tmpPopularity,_tmpSongCount);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object getById(final String artistId,
      final Continuation<? super ArtistEntity> $completion) {
    final String _sql = "SELECT * FROM artists WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, artistId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<ArtistEntity>() {
      @Override
      @Nullable
      public ArtistEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfImageUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "image_url");
          final int _cursorIndexOfGenres = CursorUtil.getColumnIndexOrThrow(_cursor, "genres");
          final int _cursorIndexOfPopularity = CursorUtil.getColumnIndexOrThrow(_cursor, "popularity");
          final int _cursorIndexOfSongCount = CursorUtil.getColumnIndexOrThrow(_cursor, "song_count");
          final ArtistEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final String _tmpImageUrl;
            _tmpImageUrl = _cursor.getString(_cursorIndexOfImageUrl);
            final List<String> _tmpGenres;
            final String _tmp;
            _tmp = _cursor.getString(_cursorIndexOfGenres);
            _tmpGenres = __stringListConverter.toStringList(_tmp);
            final int _tmpPopularity;
            _tmpPopularity = _cursor.getInt(_cursorIndexOfPopularity);
            final int _tmpSongCount;
            _tmpSongCount = _cursor.getInt(_cursorIndexOfSongCount);
            _result = new ArtistEntity(_tmpId,_tmpName,_tmpImageUrl,_tmpGenres,_tmpPopularity,_tmpSongCount);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteNotIn(final List<String> ids, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final StringBuilder _stringBuilder = StringUtil.newStringBuilder();
        _stringBuilder.append("DELETE FROM artists WHERE id NOT IN (");
        final int _inputSize = ids.size();
        StringUtil.appendPlaceholders(_stringBuilder, _inputSize);
        _stringBuilder.append(")");
        final String _sql = _stringBuilder.toString();
        final SupportSQLiteStatement _stmt = __db.compileStatement(_sql);
        int _argIndex = 1;
        for (String _item : ids) {
          _stmt.bindString(_argIndex, _item);
          _argIndex++;
        }
        __db.beginTransaction();
        try {
          _stmt.executeUpdateDelete();
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
