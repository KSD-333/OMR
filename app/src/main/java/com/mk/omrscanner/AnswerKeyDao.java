package com.mk.omrscanner;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

/**
 * Data Access Object for answer keys.
 * Provides type-safe database operations with compile-time query validation.
 */
@Dao
public interface AnswerKeyDao {

    @Query("SELECT * FROM answer_keys")
    List<AnswerKeyEntity> getAll();

    @Query("SELECT * FROM answer_keys WHERE id = :keyId LIMIT 1")
    AnswerKeyEntity getById(String keyId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrUpdate(AnswerKeyEntity key);

    @Query("DELETE FROM answer_keys WHERE id = :keyId")
    void deleteById(String keyId);

    @Query("DELETE FROM answer_keys")
    void deleteAll();
}
