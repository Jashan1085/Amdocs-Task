package com.amdocs.telecom.report;
import com.amdocs.telecom.dto.ReportForm;
/** Produces a formatting strategy without exposing delimiter choices to request input. */
public final class ReportFormatterFactory {
    private ReportFormatterFactory() { }
    public static ReportFormatter create(ReportForm.Format format) {
        if(format==null) { throw new IllegalArgumentException("Choose a format"); }
        switch(format) {
            case CSV: return table -> ReportEncoder.delimited(table,',');
            case TXT: return table -> ReportEncoder.delimited(table,'\t');
            default: throw new IllegalArgumentException("Unsupported format");
        }
    }
}
