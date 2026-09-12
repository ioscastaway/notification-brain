package com.ioscastaway.notificationbrain.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.ioscastaway.notificationbrain.brain.FeedbackChip
import com.ioscastaway.notificationbrain.brain.Outcome
import com.ioscastaway.notificationbrain.brain.Verdict

@Database(entities = [NotificationRecord::class, CrashRecord::class], version = 1, exportSchema = true)
@TypeConverters(Converters::class)
abstract class BrainDatabase : RoomDatabase() {
    abstract fun dao(): BrainDao

    companion object {
        fun open(context: Context): BrainDatabase =
            Room.databaseBuilder(context.applicationContext, BrainDatabase::class.java, "brain.db")
                .fallbackToDestructiveMigration(dropAllTables = true) // an experiment, not a product
                .build()
    }
}

class Converters {
    @TypeConverter fun verdictToString(v: Verdict) = v.name
    @TypeConverter fun stringToVerdict(s: String) = Verdict.valueOf(s)
    @TypeConverter fun outcomeToString(o: Outcome) = o.name
    @TypeConverter fun stringToOutcome(s: String) = Outcome.valueOf(s)
    @TypeConverter fun chipToString(c: FeedbackChip?) = c?.name
    @TypeConverter fun stringToChip(s: String?) = s?.let { FeedbackChip.valueOf(it) }
}
