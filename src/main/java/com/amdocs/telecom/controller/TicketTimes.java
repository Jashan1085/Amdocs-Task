package com.amdocs.telecom.controller;

import org.springframework.stereotype.Component;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Component("ticketTimes")
public class TicketTimes {
    private final DateTimeFormatter display=DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm 'IST'",Locale.ENGLISH)
            .withZone(ZoneId.of("Asia/Kolkata"));
    public String format(Instant value) { return value==null ? "Awaiting action" : display.format(value); }
}
