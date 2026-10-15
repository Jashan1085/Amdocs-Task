package com.amdocs.telecom.report;

/** Closed SQL factory: user input never supplies identifiers or SQL fragments. */
public final class ReportQueryFactory {
    private ReportQueryFactory() { }
    public static String query(ReportType type) {
        String base="WITH scope AS (SELECT t.*,s.region,s.service_code FROM trouble_ticket t JOIN telecom_service s USING(service_id) "
                +"WHERE t.created_at>=? AND t.created_at<? AND t.created_at<=?), moment AS (SELECT CAST(? AS TIMESTAMPTZ) AS at) ";
        String sql;
        switch(type) {
            case TICKET_VOLUME:
                sql="SELECT (created_at AT TIME ZONE 'Asia/Kolkata')::date AS created_day,priority,status,COUNT(*) AS tickets FROM scope GROUP BY 1,2,3 ORDER BY 1,2,3"; break;
            case SLA_COMPLIANCE:
                sql="SELECT COUNT(*) AS eligible_tickets,COUNT(first_response_at) AS response_completed,"
                        +"COUNT(*) FILTER(WHERE first_response_at<=response_deadline) AS response_met,"
                        +"COUNT(*) FILTER(WHERE first_response_at>response_deadline) AS response_late,"
                        +"COUNT(*) FILTER(WHERE first_response_at IS NULL AND at>=response_deadline) AS response_open_overdue,"
                        +"ROUND(100.0*COUNT(*) FILTER(WHERE first_response_at<=response_deadline)/NULLIF(COUNT(first_response_at),0),2) AS response_completed_met_percent,"
                        +"COUNT(resolved_at) AS resolution_completed,COUNT(*) FILTER(WHERE resolved_at<=resolution_deadline) AS resolution_met,"
                        +"COUNT(*) FILTER(WHERE resolved_at>resolution_deadline) AS resolution_late,"
                        +"COUNT(*) FILTER(WHERE resolved_at IS NULL AND at>=resolution_deadline) AS resolution_open_overdue,"
                        +"ROUND(100.0*COUNT(*) FILTER(WHERE resolved_at<=resolution_deadline)/NULLIF(COUNT(resolved_at),0),2) AS resolution_completed_met_percent "
                        +"FROM scope CROSS JOIN moment WHERE status<>'CANCELLED'"; break;
            case ENGINEER_PERFORMANCE:
                sql="SELECT COALESCE(e.employee_code,'UNASSIGNED') AS engineer,COALESCE(e.engineer_name,'Unassigned') AS engineer_name,"
                        +"COUNT(*) AS assigned_tickets,COUNT(*) FILTER(WHERE t.status NOT IN ('RESOLVED','CLOSED','CANCELLED')) AS active_tickets,"
                        +"COUNT(t.resolved_at) AS resolved_tickets,COUNT(*) FILTER(WHERE t.resolved_at<=t.resolution_deadline) AS resolution_met,"
                        +"ROUND(AVG(EXTRACT(EPOCH FROM (t.resolved_at-t.created_at))/60),2) AS average_resolution_minutes "
                        +"FROM scope t LEFT JOIN network_engineer e ON e.engineer_id=t.assigned_engineer_id GROUP BY e.employee_code,e.engineer_name ORDER BY 1"; break;
            case INCIDENT_CATEGORY:
                sql="SELECT category,COUNT(*) AS tickets,COUNT(*) FILTER(WHERE status NOT IN ('RESOLVED','CLOSED','CANCELLED')) AS active_tickets "
                        +"FROM scope GROUP BY category ORDER BY tickets DESC,category"; break;
            case REGIONAL_INCIDENT:
                sql="SELECT region,COUNT(*) AS tickets,COUNT(*) FILTER(WHERE priority='CRITICAL') AS critical_tickets,"
                        +"COUNT(*) FILTER(WHERE status NOT IN ('RESOLVED','CLOSED','CANCELLED')) AS active_tickets FROM scope GROUP BY region ORDER BY tickets DESC,region"; break;
            case AVERAGE_RESOLUTION:
                sql="SELECT priority,COUNT(resolved_at) AS resolved_tickets,COUNT(*) FILTER(WHERE status NOT IN ('RESOLVED','CLOSED','CANCELLED')) AS unresolved_tickets,"
                        +"COUNT(*) FILTER(WHERE status NOT IN ('RESOLVED','CLOSED','CANCELLED') AND at>=resolution_deadline) AS unresolved_overdue,"
                        +"ROUND(AVG(EXTRACT(EPOCH FROM (resolved_at-created_at))/60),2) AS average_resolution_minutes "
                        +"FROM scope CROSS JOIN moment WHERE status<>'CANCELLED' GROUP BY priority ORDER BY priority"; break;
            case CRITICAL_INCIDENT:
                sql="SELECT ticket_number,service_code,region,category,status,created_at,response_deadline,resolution_deadline,first_response_at,resolved_at,description "
                        +"FROM scope WHERE priority='CRITICAL' ORDER BY created_at,ticket_id"; break;
            default: throw new IllegalArgumentException("Unknown report");
        }
        return base+sql+" LIMIT 5001";
    }
}
