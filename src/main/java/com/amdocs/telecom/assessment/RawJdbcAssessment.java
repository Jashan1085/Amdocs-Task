package com.amdocs.telecom.assessment;
import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

/** Explicit scratch-database exercise, never a Spring bean or public application route. */
public final class RawJdbcAssessment {
    private RawJdbcAssessment() { }
    public static Map<String,Long> run(DataSource source) throws SQLException {
        try(Connection connection=source.getConnection()) {
            String url=connection.getMetaData().getURL();
            if(!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/telecom_oss_(auth|dao)_test(?:\\?.*)?")) {
                throw new IllegalArgumentException("Assessment requires the disposable integration database");
            }
            connection.setAutoCommit(false);
            try {
                try(Statement ddl=connection.createStatement()) {
                    ddl.execute("CREATE TEMP TABLE jdbc_demo_customer(id BIGINT PRIMARY KEY,name VARCHAR(100) NOT NULL UNIQUE) ON COMMIT DROP");
                    ddl.execute("CREATE TEMP TABLE jdbc_demo_incident(id BIGINT PRIMARY KEY,customer_id BIGINT NOT NULL REFERENCES jdbc_demo_customer(id),state VARCHAR(20) NOT NULL CHECK(state IN ('OPEN','RESOLVED')),minutes INTEGER CHECK(minutes>=0),created_at DATE NOT NULL) ON COMMIT DROP");
                }
                try(PreparedStatement insert=connection.prepareStatement("INSERT INTO jdbc_demo_customer VALUES (?,?)")) {
                    insert.setLong(1,1); insert.setString(2,"Demo ' customer"); insert.addBatch();
                    insert.setLong(1,2); insert.setString(2,"No incidents"); insert.addBatch(); insert.executeBatch();
                }
                try(PreparedStatement insert=connection.prepareStatement("INSERT INTO jdbc_demo_incident VALUES (?,1,'OPEN',NULL,DATE '2026-10-15')")) {
                    for(int i=1;i<=3;i++) { insert.setInt(1,i); insert.addBatch(); } insert.executeBatch();
                }
                Savepoint beforeFailure=connection.setSavepoint("before_invalid_row"); boolean recovered=false;
                try(PreparedStatement invalid=connection.prepareStatement("INSERT INTO jdbc_demo_incident VALUES (99,1,'OPEN',-1,CURRENT_DATE)")) { invalid.executeUpdate(); }
                catch(SQLException expected) { connection.rollback(beforeFailure); recovered=true; }
                connection.releaseSavepoint(beforeFailure);
                if(!recovered) { throw new IllegalStateException("Expected constraint refusal"); }
                try(PreparedStatement update=connection.prepareStatement("UPDATE jdbc_demo_incident SET state=?,minutes=? WHERE id=?")) {
                    update.setString(1,"RESOLVED"); update.setInt(2,30); update.setInt(3,1); update.executeUpdate();
                }
                try(PreparedStatement delete=connection.prepareStatement("DELETE FROM jdbc_demo_incident WHERE id=?")) { delete.setInt(1,3); delete.executeUpdate(); }
                Map<String,Long> result=new LinkedHashMap<>();
                try(PreparedStatement select=connection.prepareStatement("SELECT c.id,COUNT(*) AS tickets,SUM(CASE WHEN i.state='RESOLVED' THEN 1 ELSE 0 END) AS resolved,"
                        +"COALESCE(AVG(i.minutes),0)::bigint AS average_minutes,EXTRACT(YEAR FROM MIN(i.created_at))::bigint AS created_year "
                        +"FROM jdbc_demo_customer c JOIN jdbc_demo_incident i ON i.customer_id=c.id "
                        +"WHERE c.id IN (SELECT customer_id FROM jdbc_demo_incident) GROUP BY c.id HAVING COUNT(*)>=? ORDER BY c.id")) {
                    select.setInt(1,2);
                    try(ResultSet rows=select.executeQuery()) {
                        if(!rows.next()) { throw new IllegalStateException("Assessment aggregate missing"); }
                        for(String column:new String[]{"tickets","resolved","average_minutes","created_year"}) { result.put(column,rows.getLong(column)); }
                    }
                }
                try(PreparedStatement select=connection.prepareStatement("SELECT c.id,COUNT(i.id) AS incidents FROM jdbc_demo_customer c LEFT JOIN jdbc_demo_incident i ON i.customer_id=c.id GROUP BY c.id HAVING COUNT(i.id)=0 ORDER BY c.id");ResultSet rows=select.executeQuery()) {
                    result.put("customers_without_incidents",rows.next() ? 1L : 0L);
                }
                connection.commit(); // ON COMMIT DROP removes both demonstration tables.
                try(Statement ddl=connection.createStatement()) { ddl.execute("CREATE TEMP TABLE jdbc_rollback_probe(id INTEGER) ON COMMIT DROP"); ddl.execute("INSERT INTO jdbc_rollback_probe VALUES (1)"); }
                connection.rollback(); // Full transaction rollback removes the probe DDL and data.
                try(PreparedStatement select=connection.prepareStatement("SELECT to_regclass('pg_temp.jdbc_rollback_probe') IS NULL");ResultSet rows=select.executeQuery()) {
                    rows.next(); result.put("full_rollback_verified",rows.getBoolean(1) ? 1L : 0L);
                }
                connection.rollback(); return Collections.unmodifiableMap(result);
            } catch(SQLException | RuntimeException failure) {
                try { connection.rollback(); } catch(SQLException rollback) { failure.addSuppressed(rollback); }
                throw failure;
            }
        }
    }
}
