package com.amdocs.telecom.report;
import com.amdocs.telecom.dto.ReportForm;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class ReportEncoderTest {
    @Test void csvPreservesQuotesCommasUnicodeAndNewlinesWhileNeutralizingFormulaPrefixes() {
        ReportTable table=new ReportTable(Arrays.asList("name","text"),Arrays.asList(Arrays.asList("a,\"b","line1\nline2"),Arrays.asList(" =SUM(A1)","\t@evil"),Arrays.asList("Pune","café")));
        String output=new String(ReportEncoder.encode(table,ReportForm.Format.CSV),StandardCharsets.UTF_8);
        assertThat(output).isEqualTo("\"name\",\"text\"\r\n\"a,\"\"b\",\"line1\nline2\"\r\n\"' =SUM(A1)\",\"'\t@evil\"\r\n\"Pune\",\"café\"\r\n");
    }
    @Test void txtIsQuotedTabSeparatedAndByteSizeIsBounded() {
        ReportTable table=new ReportTable(Arrays.asList("a","b"),Arrays.asList(Arrays.asList("x\ty","-danger")));
        assertThat(new String(ReportEncoder.encode(table,ReportForm.Format.TXT),StandardCharsets.UTF_8)).contains("\"a\"\t\"b\"\r\n","\"x\ty\"\t\"'-danger\"");
        char[] large=new char[1024*1024]; Arrays.fill(large,'x');
        assertThatThrownBy(() -> ReportEncoder.encode(new ReportTable(Arrays.asList("text"),Arrays.asList(Arrays.asList(new String(large)))),ReportForm.Format.CSV)).isInstanceOf(IllegalStateException.class);
    }
}
