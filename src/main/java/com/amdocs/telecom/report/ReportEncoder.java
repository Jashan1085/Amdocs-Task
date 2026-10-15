package com.amdocs.telecom.report;
import com.amdocs.telecom.dto.ReportForm;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class ReportEncoder {
    private ReportEncoder() { }
    public static byte[] encode(ReportTable table,ReportForm.Format format) {
        return ReportFormatterFactory.create(format).format(table);
    }
    static byte[] delimited(ReportTable table,char delimiter) {
        ByteArrayOutputStream output=new ByteArrayOutputStream();
        row(output,table.getHeaders(),delimiter);
        for(List<String> row:table.getRows()) { row(output,row,delimiter); }
        return output.toByteArray();
    }
    private static void row(ByteArrayOutputStream output,List<String> values,char delimiter) {
        StringJoiner line=new StringJoiner(String.valueOf(delimiter));
        for(String value:values) { line.add("\""+safe(value).replace("\"","\"\"")+"\""); }
        byte[] bytes=(line.toString()+"\r\n").getBytes(StandardCharsets.UTF_8);
        if(output.size()+bytes.length>1024*1024) { throw new IllegalStateException("Report byte limit exceeded"); }
        output.write(bytes,0,bytes.length);
    }
    static String safe(String value) {
        if(value==null) { return ""; }
        int i=0; while(i<value.length() && (Character.isWhitespace(value.charAt(i)) || Character.isSpaceChar(value.charAt(i)) || value.charAt(i)=='\ufeff')) { i++; }
        if((i<value.length() && "=+-@".indexOf(value.charAt(i))>=0) || (!value.isEmpty() && (value.charAt(0)=='\t' || value.charAt(0)=='\r' || value.charAt(0)=='\n'))) { return "'"+value; }
        return value;
    }
}
