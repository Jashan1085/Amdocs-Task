package com.amdocs.telecom.dao.impl;
import com.amdocs.telecom.report.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.*;
import java.time.*;
import java.util.*;

@Repository
public class JdbcReportDAO {
    private final JdbcTemplate jdbc;
    public JdbcReportDAO(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public ReportTable generate(ReportType type,LocalDate from,LocalDate to,Instant now) {
        ZoneId zone=ZoneId.of("Asia/Kolkata");
        return jdbc.query(connection -> {
            PreparedStatement query=connection.prepareStatement(ReportQueryFactory.query(type)); query.setQueryTimeout(20);
            query.setTimestamp(1,Timestamp.from(from.atStartOfDay(zone).toInstant()));
            query.setTimestamp(2,Timestamp.from(to.plusDays(1).atStartOfDay(zone).toInstant()));
            query.setTimestamp(3,Timestamp.from(now)); query.setTimestamp(4,Timestamp.from(now)); return query;
        },rs -> {
            List<String> headers=new ArrayList<>(); List<List<String>> rows=new ArrayList<>();
            for(int i=1;i<=rs.getMetaData().getColumnCount();i++) { headers.add(rs.getMetaData().getColumnLabel(i)); }
            while(rs.next()) {
                if(rows.size()>=5000) { throw new IllegalStateException("Report row limit exceeded"); }
                List<String> row=new ArrayList<>();
                for(int i=1;i<=headers.size();i++) { Object value=rs.getObject(i); row.add(value==null ? "" : value instanceof Timestamp ? ((Timestamp)value).toInstant().toString() : value.toString()); }
                rows.add(row);
            }
            return new ReportTable(headers,rows);
        });
    }
}
