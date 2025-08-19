package com.grloepr.pushtrack.data

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context

/**
 * Room database for PushTrack application
 * Stores pose detection data, calibration sessions, and optimized thresholds
 */
@Database(
    entities = [
        PoseDataEntity::class,
        CalibrationSession::class,
        ExerciseThresholds::class
    ],
    version = 1,
    exportSchema = false
)
abstract class PushTrackDatabase : RoomDatabase() {
    
    abstract fun poseDataDao(): PoseDataDao
    abstract fun calibrationSessionDao(): CalibrationSessionDao
    abstract fun exerciseThresholdsDao(): ExerciseThresholdsDao
    
    companion object {
        @Volatile
        private var INSTANCE: PushTrackDatabase? = null
        
        fun getDatabase(context: Context): PushTrackDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PushTrackDatabase::class.java,
                    "pushtrack_database"
                )
                .addCallback(DatabaseCallback)
                .build()
                
                INSTANCE = instance
                instance
            }
        }
        
        /**
         * Database callback to initialize default data
         */
        private object DatabaseCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                // Initialize default thresholds
                initializeDefaultThresholds(db)
            }
            
            private fun initializeDefaultThresholds(db: SupportSQLiteDatabase) {
                val currentTime = System.currentTimeMillis()
                
                // Default push-up thresholds
                db.execSQL("""
                    INSERT INTO exercise_thresholds (
                        exerciseType, upThreshold, downThreshold, transitionBuffer,
                        minRepDuration, maxRepDuration, speedSensitivity,
                        minConfidence, minLandmarkVisibility,
                        calibrationSessionId, createdAt, lastUpdated, version
                    ) VALUES (
                        'PUSH_UP', 160.0, 90.0, 10.0,
                        1000, 10000, 1.0,
                        0.5, 0.5,
                        NULL, $currentTime, $currentTime, 1
                    )
                """)
                
                // Default squat thresholds
                db.execSQL("""
                    INSERT INTO exercise_thresholds (
                        exerciseType, upThreshold, downThreshold, transitionBuffer,
                        minRepDuration, maxRepDuration, speedSensitivity,
                        minConfidence, minLandmarkVisibility,
                        calibrationSessionId, createdAt, lastUpdated, version
                    ) VALUES (
                        'SQUAT', 160.0, 90.0, 10.0,
                        1500, 15000, 1.0,
                        0.5, 0.5,
                        NULL, $currentTime, $currentTime, 1
                    )
                """)
                
                // Default pull-up thresholds
                db.execSQL("""
                    INSERT INTO exercise_thresholds (
                        exerciseType, upThreshold, downThreshold, transitionBuffer,
                        minRepDuration, maxRepDuration, speedSensitivity,
                        minConfidence, minLandmarkVisibility,
                        calibrationSessionId, createdAt, lastUpdated, version
                    ) VALUES (
                        'PULL_UP', 30.0, 80.0, 5.0,
                        2000, 20000, 1.0,
                        0.5, 0.5,
                        NULL, $currentTime, $currentTime, 1
                    )
                """)
            }
        }
    }
}
