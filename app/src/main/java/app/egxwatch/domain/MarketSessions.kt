package app.egxwatch.domain

import app.egxwatch.data.*
import java.time.*

class EgxSessionManager(private val settings:Settings,private val config:EngineConfig,private val offHours:Boolean=false) {
    fun isOpen(now:Instant)=offHours || config.calendar(settings).isOpen(now)
}
class GoldSessionManager(private val config:GoldConfig) {
    fun isOpen(now:Instant):Boolean {
        val local=now.atZone(ZoneId.of(config.zone));val date=local.toLocalDate();val time=local.toLocalTime()
        val holidays=config.holidays.split(',', '\n').filter { it.isNotBlank() }.map { LocalDate.parse(it.trim()) }
        if(date in holidays) return false
        val days=config.days.split(',').map { DayOfWeek.of(it.toInt()) };val start=LocalTime.parse(config.start);val end=LocalTime.parse(config.end)
        return if(start==end) local.dayOfWeek in days else if(start<end) local.dayOfWeek in days && time>=start && time<end else
            (local.dayOfWeek in days && time>=start) || (local.minusDays(1).dayOfWeek in days && time<end && date.minusDays(1) !in holidays)
    }
}
object GlobalGold {
    val instrument=Instrument("GLOBAL:XAUUSD","XAU/USD","Global spot gold · USD per troy ounce",InstrumentType.COMMODITY,"USD","https://gold-api.com/docs","2026-09-25")
}
