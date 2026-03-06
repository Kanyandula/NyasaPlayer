package com.example.nyasaplayer.core.data.local.dao;

import android.database.Cursor;
import androidx.annotation.NonNull;
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
import com.example.nyasaplayer.core.data.local.entity.GenreEntity;
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
public final class GenreDao_Impl implements GenreDao {
  private final RoomDatabase __db;

  private final EntityUpsertionAdapter<GenreEntity> __upsertionAdapterOfGenreEntity;

  private final StringListConverter __stringListConverter = new StringListConverter();

  public GenreDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__upsertionAdapterOfGenreEntity = new EntityUpsertionAdapter<GenreEntity>(new EntityInsertionAdapter<GenreEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT INTO `genres` (`id`,`name`,`color`,`image_url`,`popularity`,`song_ids`) VALUES (?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final GenreEntity entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getName());
        statement.bindString(3, entity.getColor());
        statement.bindString(4, entity.getImageUrl());
        statement.bindLong(5, entity.getPopularity());
        final String _tmp = __stringListConverter.fromStringList(entity.getSongIds());
        statement.bindString(6, _tmp);
      }
    }, new EntityDeletionOrUpdateAdapter<GenreEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE `genres` SET `id` = ?,`name` = ?,`color` = ?,`image_url` = ?,`popularity` = ?,`song_ids` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final GenreEntity entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getName());
        statement.bindString(3, entity.getColor());
        statement.bindString(4, entity.getImageUrl());
        statement.bindLong(5, entity.getPopularity());
        final String _tmp = __stringListConverter.fromStringList(entity.getSongIds());
        statement.bindString(6, _tmp);
        statement.bindString(7, entity.getId());
      }
    });
  }

  @Override
  public Object sync(final List<GenreEntity> genres, final Continuation<? super Unit> $completion) {
    return RoomDatabaseKt.withTransaction(__db, (__cont) -> GenreDao.DefaultImpls.sync(GenreDao_Impl.this, genres, __cont), $completion);
  }

  @Override
  public Object upsertAll(final List<GenreEntity> genres,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __upsertionAdapterOfGenreEntity.upsert(genres);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<GenreEntity>> getAll() {
    final String _sql = "SELECT * FROM genres";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"genres"}, new Callable<List<GenreEntity>>() {
      @Override
      @NonNull
      public List<GenreEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfColor = CursorUtil.getColumnIndexOrThrow(_cursor, "color");
          final int _cursorIndexOfImageUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "image_url");
          final int _cursorIndexOfPopularity = CursorUtil.getColumnIndexOrThrow(_cursor, "popularity");
          final int _cursorIndexOfSongIds = CursorUtil.getColumnIndexOrThrow(_cursor, "song_ids");
          final List<GenreEntity> _result = new ArrayList<GenreEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final GenreEntity _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final String _tmpColor;
            _tmpColor = _cursor.getString(_cursorIndexOfColor);
            final String _tmpImageUrl;
            _tmpImageUrl = _cursor.getString(_cursorIndexOfImageUrl);
            final int _tmpPopularity;
            _tmpPopularity = _cursor.getInt(_cursorIndexOfPopularity);
            final List<String> _tmpSongIds;
            final String _tmp;
            _tmp = _cursor.getString(_cursorIndexOfSongIds);
            _tmpSongIds = __stringListConverter.toStringList(_tmp);
            _item = new GenreEntity(_tmpId,_tmpName,_tmpColor,_tmpImageUrl,_tmpPopularity,_tmpSongIds);
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
  public Object deleteNotIn(final List<String> ids, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final StringBuilder _stringBuilder = StringUtil.newStringBuilder();
        _stringBuilder.append("DELETE FROM genres WHERE id NOT IN (");
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
