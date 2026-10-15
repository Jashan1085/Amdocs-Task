package com.amdocs.telecom.dto;
import com.amdocs.telecom.report.ReportType;
import org.springframework.format.annotation.DateTimeFormat;
import javax.validation.constraints.*;
import java.time.LocalDate;
public class ReportForm {
    public enum Format { CSV, TXT }
    @NotNull private ReportType type;
    @NotNull private Format format;
    @NotNull @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) private LocalDate from;
    @NotNull @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) private LocalDate to;
    public ReportType getType() { return type; } public void setType(ReportType value) { type=value; }
    public Format getFormat() { return format; } public void setFormat(Format value) { format=value; }
    public LocalDate getFrom() { return from; } public void setFrom(LocalDate value) { from=value; }
    public LocalDate getTo() { return to; } public void setTo(LocalDate value) { to=value; }
}
