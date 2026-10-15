package com.amdocs.telecom.assessment;
import com.amdocs.telecom.dto.ReportForm;
import com.amdocs.telecom.report.*;
import java.nio.file.*;
import java.util.*;

/** Offline synthetic examples using the same formatter as authorized downloads. */
public final class SampleExports {
    private SampleExports() { }
    public static void main(String[] args) throws Exception {
        Path directory=Paths.get(args.length==0 ? "docs/samples" : args[0]); Files.createDirectories(directory);
        ReportTable table=new ReportTable(Arrays.asList("ticket_number","service_code","region","status","description"),Arrays.asList(
                Arrays.asList("SAMPLE-001","SAMPLE-SVC-01","West","OPEN","Link down, awaiting review"),
                Arrays.asList("SAMPLE-002","SAMPLE-SVC-02","North","CLOSED","Router \"A\" restored\nCustomer confirmed"),
                Arrays.asList("SAMPLE-003","SAMPLE-SVC-03","West","OPEN","=SUM(A1:A2)")));
        for(ReportForm.Format format:ReportForm.Format.values()) {
            Files.write(directory.resolve("synthetic-incidents."+format.name().toLowerCase(Locale.ROOT)),ReportEncoder.encode(table,format));
        }
        System.out.println("Synthetic CSV/TXT samples generated (no database access).");
    }
}
