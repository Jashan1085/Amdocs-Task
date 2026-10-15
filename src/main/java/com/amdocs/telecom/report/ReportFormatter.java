package com.amdocs.telecom.report;
@FunctionalInterface
public interface ReportFormatter {
    byte[] format(ReportTable table);
}
