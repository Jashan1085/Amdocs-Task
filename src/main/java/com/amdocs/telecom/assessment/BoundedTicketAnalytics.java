package com.amdocs.telecom.assessment;
import com.amdocs.telecom.model.*;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Assessment examples on explicitly bounded snapshots; production reports aggregate in SQL. */
public final class BoundedTicketAnalytics {
    public static final class Sample {
        private final TroubleTicket ticket;
        private final String region;
        private final long customer;
        public Sample(TroubleTicket ticket,String region,long customer) { this.ticket=Objects.requireNonNull(ticket); this.region=Objects.requireNonNull(region); this.customer=customer; }
        public TroubleTicket getTicket() { return ticket; } public String getRegion() { return region; } public long getCustomer() { return customer; }
    }
    private final List<Sample> rows;
    public BoundedTicketAnalytics(List<Sample> rows) {
        if(rows==null || rows.size()>1000 || rows.contains(null)) { throw new IllegalArgumentException("At most 1000 non-null snapshots are allowed"); }
        this.rows=Collections.unmodifiableList(new ArrayList<>(rows));
    }
    private static boolean active(TroubleTicket ticket) { return ticket.getStatus()!=TicketStatus.RESOLVED && ticket.getStatus()!=TicketStatus.CLOSED && ticket.getStatus()!=TicketStatus.CANCELLED; }
    private <T> Map<T,Long> counts(Function<Sample,T> key) { return rows.stream().collect(Collectors.groupingBy(key,Collectors.counting())); }
    public Map<TicketStatus,Long> byStatus() { return counts(row -> row.getTicket().getStatus()); }
    public Map<TicketPriority,Long> byPriority() { return counts(row -> row.getTicket().getPriority()); }
    public Map<String,Long> byRegion() { return counts(Sample::getRegion); }
    public Map<Long,Long> engineerWorkload() {
        return rows.stream().map(Sample::getTicket).filter(BoundedTicketAnalytics::active).filter(t -> t.getAssignedEngineerId()!=null)
                .collect(Collectors.groupingBy(TroubleTicket::getAssignedEngineerId,Collectors.counting()));
    }
    public long activeBreaches(Instant now) {
        return rows.stream().map(Sample::getTicket).filter(BoundedTicketAnalytics::active)
                .filter(t -> (t.getFirstResponseAt()==null && !now.isBefore(t.getResponseDeadline())) || (t.getResolvedAt()==null && !now.isBefore(t.getResolutionDeadline()))).count();
    }
    public OptionalDouble averageResolutionMinutes() {
        return rows.stream().map(Sample::getTicket).filter(t -> t.getResolvedAt()!=null).mapToDouble(TicketMetric.resolutionMinutes()::measure).average();
    }
    public List<IncidentCategory> topCategories() {
        return counts(row -> row.getTicket().getCategory()).entrySet().stream()
                .sorted(Map.Entry.<IncidentCategory,Long>comparingByValue().reversed().thenComparing(e -> e.getKey().name())).limit(3).map(Map.Entry::getKey).collect(Collectors.toList());
    }
    public Map<Long,Double> engineerPerformanceMinutes() {
        return rows.stream().map(Sample::getTicket).filter(t -> t.getAssignedEngineerId()!=null && t.getResolvedAt()!=null)
                .collect(Collectors.groupingBy(TroubleTicket::getAssignedEngineerId,Collectors.averagingDouble(TicketMetric.resolutionMinutes()::measure)));
    }
    public Set<Long> repeatedIncidentCustomers() { return counts(Sample::getCustomer).entrySet().stream().filter(e -> e.getValue()>1).map(Map.Entry::getKey).collect(Collectors.toSet()); }
}
