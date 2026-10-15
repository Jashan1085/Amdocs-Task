package com.amdocs.telecom.report;
import java.util.*;
public final class ReportTable {
    private final List<String> headers;
    private final List<List<String>> rows;
    public ReportTable(List<String> headers,List<List<String>> rows) {
        this.headers=Collections.unmodifiableList(new ArrayList<>(headers));
        List<List<String>> copy=new ArrayList<>();
        for(List<String> row:rows) { copy.add(Collections.unmodifiableList(new ArrayList<>(row))); }
        this.rows=Collections.unmodifiableList(copy);
    }
    public List<String> getHeaders() { return headers; }
    public List<List<String>> getRows() { return rows; }
}
