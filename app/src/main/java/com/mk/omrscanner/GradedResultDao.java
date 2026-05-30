package com.mk.omrscanner;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

/**
 * Data Access Object for graded results.
 * Provides type-safe database operations with compile-time query validation.
 */
@Dao
public interface GradedResultDao {

    @Query("SELECT * FROM graded_results ORDER BY timestamp DESC")
    List<GradedResultEntity> getAll();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(GradedResultEntity result);

    @Query("DELETE FROM graded_results WHERE id = :resultId")
    void deleteById(String resultId);

    @Query("DELETE FROM graded_results")
    void deleteAll();

    @Query("SELECT COUNT(*) FROM graded_results")
    int count();
}
