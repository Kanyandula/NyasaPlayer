package com.example.nyasaplayer.core.data.local.dao;

import android.database.Cursor;
import android.os.CancellationSignal;
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
import com.example.nyasaplayer.core.data.local.entity.SongEntity;
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
public final class SongDao_Impl implements SongDao {
  private final RoomDatabase __db;

  private final EntityUpsertionAdapter<SongEntity> __upsertionAdapterOfSongEntity;

  private final StringListConverter __stringListConverter = new StringListConverter();

  public SongDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__upsertionAdapterOfSongEntity = new EntityUpsertionAdapter<SongEntity>(new EntityInsertionAdapter<SongEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT INTO `songs` (`media_id`,`title`,`subtitle`,`image_url`,`song_url`,`artist_id`,`artist_name`,`album_id`,`album_name`,`duration_ms`,`genre_ids`,`cover_url`,`audio_url`,`popularity`,`is_explicit`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final SongEntity entity) {
        statement.bindString(1, entity.getMediaId());
        statement.bindString(2, entity.getTitle());
        statement.bindString(3, entity.getSubtitle());
        statement.bindString(4, entity.getImageUrl());
        statement.bindString(5, entity.getSongUrl());
        statement.bindString(6, entity.getArtistId());
        statement.bindString(7, entity.getArtistName());
        statement.bindString(8, entity.getAlbumId());
        statement.bindString(9, entity.getAlbumName());
        statement.bindLong(10, entity.getDurationMs());
        final String _tmp = __stringListConverter.fromStringList(entity.getGenreIds());
        statement.bindString(11, _tmp);
        statement.bindString(12, entity.getCoverUrl());
        statement.bindString(13, entity.getAudioUrl());
        statement.bindLong(14, entity.getPopularity());
        final int _tmp_1 = entity.isExplicit() ? 1 : 0;
        statement.bindLong(15, _tmp_1);
      }
    }, new EntityDeletionOrUpdateAdapter<SongEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE `songs` SET `media_id` = ?,`title` = ?,`subtitle` = ?,`image_url` = ?,`song_url` = ?,`artist_id` = ?,`artist_name` = ?,`album_id` = ?,`album_name` = ?,`duration_ms` = ?,`genre_ids` = ?,`cover_url` = ?,`audio_url` = ?,`popularity` = ?,`is_explicit` = ? WHERE `media_id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final SongEntity entity) {
        statement.bindString(1, entity.getMediaId());
        statement.bindString(2, entity.getTitle());
        statement.bindString(3, entity.getSubtitle());
        statement.bindString(4, entity.getImageUrl());
        statement.bindString(5, entity.getSongUrl());
        statement.bindString(6, entity.getArtistId());
        statement.bindString(7, entity.getArtistName());
        statement.bindString(8, entity.getAlbumId());
        statement.bindString(9, entity.getAlbumName());
        statement.bindLong(10, entity.getDurationMs());
        final String _tmp = __stringListConverter.fromStringList(entity.getGenreIds());
        statement.bindString(11, _tmp);
        statement.bindString(12, entity.getCoverUrl());
        statement.bindString(13, entity.getAudioUrl());
        statement.bindLong(14, entity.getPopularity());
        final int _tmp_1 = entity.isExplicit() ? 1 : 0;
        statement.bindLong(15, _tmp_1);
        statement.bindString(16, entity.getMediaId());
      }
    });
  }

  @Override
  public Object sync(final List<SongEntity> songs, final Continuation<? super Unit> $completion) {
    return RoomDatabaseKt.withTransaction(__db, (__cont) -> SongDao.DefaultImpls.sync(SongDao_Impl.this, songs, __cont), $completion);
  }

  @Override
  public Object upsertAll(final List<SongEntity> songs,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __upsertionAdapterOfSongEntity.upsert(songs);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<SongEntity>> getAll() {
    final String _sql = "SELECT * FROM songs";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"songs"}, new Callable<List<SongEntity>>() {
      @Override
      @NonNull
      public List<SongEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfMediaId = CursorUtil.getColumnIndexOrThrow(_cursor, "media_id");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfSubtitle = CursorUtil.getColumnIndexOrThrow(_cursor, "subtitle");
          final int _cursorIndexOfImageUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "image_url");
          final int _cursorIndexOfSongUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "song_url");
          final int _cursorIndexOfArtistId = CursorUtil.getColumnIndexOrThrow(_cursor, "artist_id");
          final int _cursorIndexOfArtistName = CursorUtil.getColumnIndexOrThrow(_cursor, "artist_name");
          final int _cursorIndexOfAlbumId = CursorUtil.getColumnIndexOrThrow(_cursor, "album_id");
          final int _cursorIndexOfAlbumName = CursorUtil.getColumnIndexOrThrow(_cursor, "album_name");
          final int _cursorIndexOfDurationMs = CursorUtil.getColumnIndexOrThrow(_cursor, "duration_ms");
          final int _cursorIndexOfGenreIds = CursorUtil.getColumnIndexOrThrow(_cursor, "genre_ids");
          final int _cursorIndexOfCoverUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "cover_url");
          final int _cursorIndexOfAudioUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "audio_url");
          final int _cursorIndexOfPopularity = CursorUtil.getColumnIndexOrThrow(_cursor, "popularity");
          final int _cursorIndexOfIsExplicit = CursorUtil.getColumnIndexOrThrow(_cursor, "is_explicit");
          final List<SongEntity> _result = new ArrayList<SongEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final SongEntity _item;
            final String _tmpMediaId;
            _tmpMediaId = _cursor.getString(_cursorIndexOfMediaId);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final String _tmpSubtitle;
            _tmpSubtitle = _cursor.getString(_cursorIndexOfSubtitle);
            final String _tmpImageUrl;
            _tmpImageUrl = _cursor.getString(_cursorIndexOfImageUrl);
            final String _tmpSongUrl;
            _tmpSongUrl = _cursor.getString(_cursorIndexOfSongUrl);
            final String _tmpArtistId;
            _tmpArtistId = _cursor.getString(_cursorIndexOfArtistId);
            final String _tmpArtistName;
            _tmpArtistName = _cursor.getString(_cursorIndexOfArtistName);
            final String _tmpAlbumId;
            _tmpAlbumId = _cursor.getString(_cursorIndexOfAlbumId);
            final String _tmpAlbumName;
            _tmpAlbumName = _cursor.getString(_cursorIndexOfAlbumName);
            final long _tmpDurationMs;
            _tmpDurationMs = _cursor.getLong(_cursorIndexOfDurationMs);
            final List<String> _tmpGenreIds;
            final String _tmp;
            _tmp = _cursor.getString(_cursorIndexOfGenreIds);
            _tmpGenreIds = __stringListConverter.toStringList(_tmp);
            final String _tmpCoverUrl;
            _tmpCoverUrl = _cursor.getString(_cursorIndexOfCoverUrl);
            final String _tmpAudioUrl;
            _tmpAudioUrl = _cursor.getString(_cursorIndexOfAudioUrl);
            final int _tmpPopularity;
            _tmpPopularity = _cursor.getInt(_cursorIndexOfPopularity);
            final boolean _tmpIsExplicit;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsExplicit);
            _tmpIsExplicit = _tmp_1 != 0;
            _item = new SongEntity(_tmpMediaId,_tmpTitle,_tmpSubtitle,_tmpImageUrl,_tmpSongUrl,_tmpArtistId,_tmpArtistName,_tmpAlbumId,_tmpAlbumName,_tmpDurationMs,_tmpGenreIds,_tmpCoverUrl,_tmpAudioUrl,_tmpPopularity,_tmpIsExplicit);
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
  public Object getByMediaIds(final List<String> mediaIds,
      final Continuation<? super List<SongEntity>> $completion) {
    final StringBuilder _stringBuilder = StringUtil.newStringBuilder();
    _stringBuilder.append("SELECT * FROM songs WHERE media_id IN (");
    final int _inputSize = mediaIds.size();
    StringUtil.appendPlaceholders(_stringBuilder, _inputSize);
    _stringBuilder.append(")");
    final String _sql = _stringBuilder.toString();
    final int _argCount = 0 + _inputSize;
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, _argCount);
    int _argIndex = 1;
    for (String _item : mediaIds) {
      _statement.bindString(_argIndex, _item);
      _argIndex++;
    }
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<SongEntity>>() {
      @Override
      @NonNull
      public List<SongEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfMediaId = CursorUtil.getColumnIndexOrThrow(_cursor, "media_id");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfSubtitle = CursorUtil.getColumnIndexOrThrow(_cursor, "subtitle");
          final int _cursorIndexOfImageUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "image_url");
          final int _cursorIndexOfSongUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "song_url");
          final int _cursorIndexOfArtistId = CursorUtil.getColumnIndexOrThrow(_cursor, "artist_id");
          final int _cursorIndexOfArtistName = CursorUtil.getColumnIndexOrThrow(_cursor, "artist_name");
          final int _cursorIndexOfAlbumId = CursorUtil.getColumnIndexOrThrow(_cursor, "album_id");
          final int _cursorIndexOfAlbumName = CursorUtil.getColumnIndexOrThrow(_cursor, "album_name");
          final int _cursorIndexOfDurationMs = CursorUtil.getColumnIndexOrThrow(_cursor, "duration_ms");
          final int _cursorIndexOfGenreIds = CursorUtil.getColumnIndexOrThrow(_cursor, "genre_ids");
          final int _cursorIndexOfCoverUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "cover_url");
          final int _cursorIndexOfAudioUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "audio_url");
          final int _cursorIndexOfPopularity = CursorUtil.getColumnIndexOrThrow(_cursor, "popularity");
          final int _cursorIndexOfIsExplicit = CursorUtil.getColumnIndexOrThrow(_cursor, "is_explicit");
          final List<SongEntity> _result = new ArrayList<SongEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final SongEntity _item_1;
            final String _tmpMediaId;
            _tmpMediaId = _cursor.getString(_cursorIndexOfMediaId);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final String _tmpSubtitle;
            _tmpSubtitle = _cursor.getString(_cursorIndexOfSubtitle);
            final String _tmpImageUrl;
            _tmpImageUrl = _cursor.getString(_cursorIndexOfImageUrl);
            final String _tmpSongUrl;
            _tmpSongUrl = _cursor.getString(_cursorIndexOfSongUrl);
            final String _tmpArtistId;
            _tmpArtistId = _cursor.getString(_cursorIndexOfArtistId);
            final String _tmpArtistName;
            _tmpArtistName = _cursor.getString(_cursorIndexOfArtistName);
            final String _tmpAlbumId;
            _tmpAlbumId = _cursor.getString(_cursorIndexOfAlbumId);
            final String _tmpAlbumName;
            _tmpAlbumName = _cursor.getString(_cursorIndexOfAlbumName);
            final long _tmpDurationMs;
            _tmpDurationMs = _cursor.getLong(_cursorIndexOfDurationMs);
            final List<String> _tmpGenreIds;
            final String _tmp;
            _tmp = _cursor.getString(_cursorIndexOfGenreIds);
            _tmpGenreIds = __stringListConverter.toStringList(_tmp);
            final String _tmpCoverUrl;
            _tmpCoverUrl = _cursor.getString(_cursorIndexOfCoverUrl);
            final String _tmpAudioUrl;
            _tmpAudioUrl = _cursor.getString(_cursorIndexOfAudioUrl);
            final int _tmpPopularity;
            _tmpPopularity = _cursor.getInt(_cursorIndexOfPopularity);
            final boolean _tmpIsExplicit;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsExplicit);
            _tmpIsExplicit = _tmp_1 != 0;
            _item_1 = new SongEntity(_tmpMediaId,_tmpTitle,_tmpSubtitle,_tmpImageUrl,_tmpSongUrl,_tmpArtistId,_tmpArtistName,_tmpAlbumId,_tmpAlbumName,_tmpDurationMs,_tmpGenreIds,_tmpCoverUrl,_tmpAudioUrl,_tmpPopularity,_tmpIsExplicit);
            _result.add(_item_1);
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
  public Flow<List<SongEntity>> getByArtistId(final String artistId) {
    final String _sql = "SELECT * FROM songs WHERE artist_id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, artistId);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"songs"}, new Callable<List<SongEntity>>() {
      @Override
      @NonNull
      public List<SongEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfMediaId = CursorUtil.getColumnIndexOrThrow(_cursor, "media_id");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfSubtitle = CursorUtil.getColumnIndexOrThrow(_cursor, "subtitle");
          final int _cursorIndexOfImageUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "image_url");
          final int _cursorIndexOfSongUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "song_url");
          final int _cursorIndexOfArtistId = CursorUtil.getColumnIndexOrThrow(_cursor, "artist_id");
          final int _cursorIndexOfArtistName = CursorUtil.getColumnIndexOrThrow(_cursor, "artist_name");
          final int _cursorIndexOfAlbumId = CursorUtil.getColumnIndexOrThrow(_cursor, "album_id");
          final int _cursorIndexOfAlbumName = CursorUtil.getColumnIndexOrThrow(_cursor, "album_name");
          final int _cursorIndexOfDurationMs = CursorUtil.getColumnIndexOrThrow(_cursor, "duration_ms");
          final int _cursorIndexOfGenreIds = CursorUtil.getColumnIndexOrThrow(_cursor, "genre_ids");
          final int _cursorIndexOfCoverUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "cover_url");
          final int _cursorIndexOfAudioUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "audio_url");
          final int _cursorIndexOfPopularity = CursorUtil.getColumnIndexOrThrow(_cursor, "popularity");
          final int _cursorIndexOfIsExplicit = CursorUtil.getColumnIndexOrThrow(_cursor, "is_explicit");
          final List<SongEntity> _result = new ArrayList<SongEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final SongEntity _item;
            final String _tmpMediaId;
            _tmpMediaId = _cursor.getString(_cursorIndexOfMediaId);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final String _tmpSubtitle;
            _tmpSubtitle = _cursor.getString(_cursorIndexOfSubtitle);
            final String _tmpImageUrl;
            _tmpImageUrl = _cursor.getString(_cursorIndexOfImageUrl);
            final String _tmpSongUrl;
            _tmpSongUrl = _cursor.getString(_cursorIndexOfSongUrl);
            final String _tmpArtistId;
            _tmpArtistId = _cursor.getString(_cursorIndexOfArtistId);
            final String _tmpArtistName;
            _tmpArtistName = _cursor.getString(_cursorIndexOfArtistName);
            final String _tmpAlbumId;
            _tmpAlbumId = _cursor.getString(_cursorIndexOfAlbumId);
            final String _tmpAlbumName;
            _tmpAlbumName = _cursor.getString(_cursorIndexOfAlbumName);
            final long _tmpDurationMs;
            _tmpDurationMs = _cursor.getLong(_cursorIndexOfDurationMs);
            final List<String> _tmpGenreIds;
            final String _tmp;
            _tmp = _cursor.getString(_cursorIndexOfGenreIds);
            _tmpGenreIds = __stringListConverter.toStringList(_tmp);
            final String _tmpCoverUrl;
            _tmpCoverUrl = _cursor.getString(_cursorIndexOfCoverUrl);
            final String _tmpAudioUrl;
            _tmpAudioUrl = _cursor.getString(_cursorIndexOfAudioUrl);
            final int _tmpPopularity;
            _tmpPopularity = _cursor.getInt(_cursorIndexOfPopularity);
            final boolean _tmpIsExplicit;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsExplicit);
            _tmpIsExplicit = _tmp_1 != 0;
            _item = new SongEntity(_tmpMediaId,_tmpTitle,_tmpSubtitle,_tmpImageUrl,_tmpSongUrl,_tmpArtistId,_tmpArtistName,_tmpAlbumId,_tmpAlbumName,_tmpDurationMs,_tmpGenreIds,_tmpCoverUrl,_tmpAudioUrl,_tmpPopularity,_tmpIsExplicit);
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
  public Flow<List<SongEntity>> getByGenreId(final String genreId) {
    final String _sql = "SELECT * FROM songs WHERE genre_ids LIKE '%\"' || ? || '\"%'";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, genreId);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"songs"}, new Callable<List<SongEntity>>() {
      @Override
      @NonNull
      public List<SongEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfMediaId = CursorUtil.getColumnIndexOrThrow(_cursor, "media_id");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfSubtitle = CursorUtil.getColumnIndexOrThrow(_cursor, "subtitle");
          final int _cursorIndexOfImageUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "image_url");
          final int _cursorIndexOfSongUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "song_url");
          final int _cursorIndexOfArtistId = CursorUtil.getColumnIndexOrThrow(_cursor, "artist_id");
          final int _cursorIndexOfArtistName = CursorUtil.getColumnIndexOrThrow(_cursor, "artist_name");
          final int _cursorIndexOfAlbumId = CursorUtil.getColumnIndexOrThrow(_cursor, "album_id");
          final int _cursorIndexOfAlbumName = CursorUtil.getColumnIndexOrThrow(_cursor, "album_name");
          final int _cursorIndexOfDurationMs = CursorUtil.getColumnIndexOrThrow(_cursor, "duration_ms");
          final int _cursorIndexOfGenreIds = CursorUtil.getColumnIndexOrThrow(_cursor, "genre_ids");
          final int _cursorIndexOfCoverUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "cover_url");
          final int _cursorIndexOfAudioUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "audio_url");
          final int _cursorIndexOfPopularity = CursorUtil.getColumnIndexOrThrow(_cursor, "popularity");
          final int _cursorIndexOfIsExplicit = CursorUtil.getColumnIndexOrThrow(_cursor, "is_explicit");
          final List<SongEntity> _result = new ArrayList<SongEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final SongEntity _item;
            final String _tmpMediaId;
            _tmpMediaId = _cursor.getString(_cursorIndexOfMediaId);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final String _tmpSubtitle;
            _tmpSubtitle = _cursor.getString(_cursorIndexOfSubtitle);
            final String _tmpImageUrl;
            _tmpImageUrl = _cursor.getString(_cursorIndexOfImageUrl);
            final String _tmpSongUrl;
            _tmpSongUrl = _cursor.getString(_cursorIndexOfSongUrl);
            final String _tmpArtistId;
            _tmpArtistId = _cursor.getString(_cursorIndexOfArtistId);
            final String _tmpArtistName;
            _tmpArtistName = _cursor.getString(_cursorIndexOfArtistName);
            final String _tmpAlbumId;
            _tmpAlbumId = _cursor.getString(_cursorIndexOfAlbumId);
            final String _tmpAlbumName;
            _tmpAlbumName = _cursor.getString(_cursorIndexOfAlbumName);
            final long _tmpDurationMs;
            _tmpDurationMs = _cursor.getLong(_cursorIndexOfDurationMs);
            final List<String> _tmpGenreIds;
            final String _tmp;
            _tmp = _cursor.getString(_cursorIndexOfGenreIds);
            _tmpGenreIds = __stringListConverter.toStringList(_tmp);
            final String _tmpCoverUrl;
            _tmpCoverUrl = _cursor.getString(_cursorIndexOfCoverUrl);
            final String _tmpAudioUrl;
            _tmpAudioUrl = _cursor.getString(_cursorIndexOfAudioUrl);
            final int _tmpPopularity;
            _tmpPopularity = _cursor.getInt(_cursorIndexOfPopularity);
            final boolean _tmpIsExplicit;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsExplicit);
            _tmpIsExplicit = _tmp_1 != 0;
            _item = new SongEntity(_tmpMediaId,_tmpTitle,_tmpSubtitle,_tmpImageUrl,_tmpSongUrl,_tmpArtistId,_tmpArtistName,_tmpAlbumId,_tmpAlbumName,_tmpDurationMs,_tmpGenreIds,_tmpCoverUrl,_tmpAudioUrl,_tmpPopularity,_tmpIsExplicit);
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
        _stringBuilder.append("DELETE FROM songs WHERE media_id NOT IN (");
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
