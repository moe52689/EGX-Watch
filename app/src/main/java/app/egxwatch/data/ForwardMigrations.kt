package app.egxwatch.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object ForwardMigrations {
    val FROM_3=object:Migration(3,4) {
        override fun migrate(db:SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS market_observations (id TEXT NOT NULL PRIMARY KEY, instrumentId TEXT NOT NULL, ticker TEXT NOT NULL, name TEXT NOT NULL, seriesKey TEXT NOT NULL, providerTime INTEGER NOT NULL, receivedAt INTEGER NOT NULL, sessionDate TEXT NOT NULL, price TEXT NOT NULL, currency TEXT NOT NULL, provider TEXT NOT NULL, freshness TEXT NOT NULL, kind TEXT NOT NULL, timestampBasis TEXT NOT NULL, open TEXT, high TEXT, low TEXT, previousClose TEXT, volume INTEGER, bid TEXT, ask TEXT, changePercent TEXT, payload TEXT NOT NULL)")
            db.execSQL("CREATE INDEX index_market_observations_instrumentId_providerTime ON market_observations(instrumentId,providerTime)")
            db.execSQL("CREATE UNIQUE INDEX index_market_observations_seriesKey_providerTime ON market_observations(seriesKey,providerTime)")
            db.execSQL("CREATE TABLE IF NOT EXISTS observed_sessions (seriesKey TEXT NOT NULL, sessionDate TEXT NOT NULL, instrumentId TEXT NOT NULL, firstTime INTEGER NOT NULL, lastTime INTEGER NOT NULL, open TEXT NOT NULL, high TEXT NOT NULL, low TEXT NOT NULL, close TEXT NOT NULL, samples INTEGER NOT NULL, validSamples INTEGER NOT NULL, occupiedSlots INTEGER NOT NULL, expectedSlots INTEGER NOT NULL, firstVolume INTEGER, lastVolume INTEGER, lastSlot INTEGER NOT NULL, PRIMARY KEY(seriesKey,sessionDate))")
            db.execSQL("CREATE INDEX index_observed_sessions_instrumentId_sessionDate ON observed_sessions(instrumentId,sessionDate)")
            db.execSQL("CREATE TABLE IF NOT EXISTS collection_series (seriesKey TEXT NOT NULL PRIMARY KEY, instrumentId TEXT NOT NULL, startedAt INTEGER NOT NULL, lastReceivedAt INTEGER NOT NULL, lastProviderTime INTEGER NOT NULL, lastFingerprint TEXT NOT NULL, observations INTEGER NOT NULL)")
            db.execSQL("CREATE INDEX index_collection_series_instrumentId ON collection_series(instrumentId)")
            // Externally sourced histories and assessments cannot masquerade as forward-collected data.
            db.execSQL("DELETE FROM historical_prices")
            db.execSQL("DELETE FROM analyses")
        }
    }
    val FROM_4=object:Migration(4,5) {
        override fun migrate(db:SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS gold_config (id INTEGER NOT NULL PRIMARY KEY, enabled INTEGER NOT NULL, freeProvider INTEGER NOT NULL, providerUrl TEXT NOT NULL, fallbackUrl TEXT NOT NULL, interval INTEGER NOT NULL, days TEXT NOT NULL, start TEXT NOT NULL, end TEXT NOT NULL, zone TEXT NOT NULL, holidays TEXT NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS gold_status (id INTEGER NOT NULL PRIMARY KEY, lastAttempt INTEGER, lastSuccess INTEGER, payload TEXT, message TEXT NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS forward_preferences (id INTEGER NOT NULL PRIMARY KEY, onboardingSeen INTEGER NOT NULL, egxOffHours INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS gold_rules (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, type TEXT NOT NULL, threshold REAL NOT NULL, periodMinutes INTEGER NOT NULL, cooldownMinutes INTEGER NOT NULL, enabled INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS gold_rule_state (ruleId INTEGER NOT NULL PRIMARY KEY, fingerprint TEXT NOT NULL, lastCondition INTEGER NOT NULL, lastNotifiedAt INTEGER)")
            db.execSQL("CREATE TABLE IF NOT EXISTS alert_center (id TEXT NOT NULL PRIMARY KEY, category TEXT NOT NULL, instrumentId TEXT, timestamp INTEGER NOT NULL, createdAt INTEGER NOT NULL, title TEXT NOT NULL, body TEXT NOT NULL, price TEXT, provider TEXT, read INTEGER NOT NULL, dismissed INTEGER NOT NULL)")
            db.execSQL("CREATE INDEX index_alert_center_instrumentId_timestamp ON alert_center(instrumentId,timestamp)")
            db.execSQL("CREATE INDEX index_alert_center_createdAt ON alert_center(createdAt)")
        }
    }
}
