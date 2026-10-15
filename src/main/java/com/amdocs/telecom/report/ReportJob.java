package com.amdocs.telecom.report;
import com.amdocs.telecom.dto.ReportForm;
import com.amdocs.telecom.security.Role;
import java.time.*;
import java.util.UUID;
import java.util.concurrent.Future;
public final class ReportJob {
    private final UUID id=UUID.randomUUID();
    private final long owner,authVersion;
    private final Role role;
    private final ReportType type;
    private final ReportForm.Format format;
    private final LocalDate from,to;
    private final Instant createdAt;
    private volatile String status="QUEUED";
    private volatile byte[] content;
    private Future<?> future;
    public ReportJob(long owner,long authVersion,Role role,ReportForm form,Instant createdAt) {
        this.owner=owner; this.authVersion=authVersion; this.role=role; type=form.getType(); format=form.getFormat(); from=form.getFrom(); to=form.getTo(); this.createdAt=createdAt;
    }
    public UUID getId() { return id; } public long getOwner() { return owner; } public long getAuthVersion() { return authVersion; }
    public Role getRole() { return role; } public ReportType getType() { return type; } public ReportForm.Format getFormat() { return format; }
    public LocalDate getFrom() { return from; } public LocalDate getTo() { return to; } public Instant getCreatedAt() { return createdAt; }
    public String getStatus() { return status; }
    public boolean isTerminal() { return status.equals("COMPLETE") || status.equals("FAILED"); }
    public void running() { status="RUNNING"; }
    public void complete(byte[] bytes) { content=bytes; status="COMPLETE"; }
    public void failed() { content=null; status="FAILED"; }
    public byte[] content() { return content.clone(); }
    public void future(Future<?> value) { future=value; }
    public void cancel() { if(future!=null) { future.cancel(true); } content=null; }
    public String getFilename() { return type.name().toLowerCase(java.util.Locale.ROOT)+"-"+id+"."+format.name().toLowerCase(java.util.Locale.ROOT); }
}
