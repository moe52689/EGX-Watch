package app.egxwatch.domain

import java.time.*

enum class LocalChartRange(val label:String,val days:Long,val minimumSpanDays:Long) {
    TODAY("TODAY",0,0), FIVE_DAYS("5D",5,0), MONTH("1M",30,20), THREE_MONTHS("3M",90,60),
    SIX_MONTHS("6M",180,120), YEAR("1Y",365,250), ALL("ALL",Long.MAX_VALUE,0);
    fun available(first:Long?,last:Long?,count:Long):Boolean=first!=null && last!=null && count>0 && Duration.ofMillis(last-first).toDays()>=minimumSpanDays
    fun start(now:Instant,zone:ZoneId):Long=when(this) {
        TODAY->now.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
        ALL->0L
        else->now.minusSeconds(days*86400).toEpochMilli()
    }
}
