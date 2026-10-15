package com.amdocs.telecom.service;
import com.amdocs.telecom.dao.impl.JdbcReportDAO;
import com.amdocs.telecom.dto.ReportForm;
import com.amdocs.telecom.report.*;
import com.amdocs.telecom.security.*;
import com.amdocs.telecom.exception.WorkflowException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.slf4j.*;
import javax.validation.Validator;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
public class ReportService implements DisposableBean {
    private static final Logger LOG=LoggerFactory.getLogger(ReportService.class);
    private final JdbcReportDAO dao;
    private final TicketWorkflowService workflow;
    private final Validator validator;
    private final Clock clock;
    private final Map<UUID,ReportJob> jobs=new HashMap<>();
    private final ExecutorService executor=new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(16),
            task -> { Thread thread=new Thread(task,"report-generator"); thread.setDaemon(true); return thread; },new ThreadPoolExecutor.AbortPolicy());
    public ReportService(JdbcReportDAO dao,TicketWorkflowService workflow,Validator validator,Clock clock) { this.dao=dao; this.workflow=workflow; this.validator=validator; this.clock=clock; }
    private void authorize(TelecomPrincipal principal,Role role) {
        if(role!=Role.SERVICE_DESK && role!=Role.NETWORK_MANAGER) { throw new WorkflowException(HttpStatus.FORBIDDEN,"Reports are restricted to desk and manager roles."); }
        workflow.actor(principal,role,false);
    }
    public synchronized UUID request(TelecomPrincipal principal,Role role,ReportForm form) {
        authorize(principal,role); prune();
        if(form==null || !validator.validate(form).isEmpty() || form.getTo().isBefore(form.getFrom())
                || ChronoUnit.DAYS.between(form.getFrom(),form.getTo())>=366 || form.getFrom().getYear()<2000 || form.getTo().getYear()>2100) {
            throw new WorkflowException(HttpStatus.BAD_REQUEST,"Choose a valid report, format and inclusive date range of at most 366 days (2000-2100).");
        }
        if(jobs.values().stream().filter(j -> j.getOwner()==principal.getUserId() && !j.isTerminal()).count()>=3) { throw busy(); }
        if(jobs.size()>=32) { jobs.values().stream().filter(ReportJob::isTerminal).min(Comparator.comparing(ReportJob::getCreatedAt)).ifPresent(j -> { jobs.remove(j.getId()); j.cancel(); }); }
        if(jobs.size()>=32) { throw busy(); }
        ReportJob job=new ReportJob(principal.getUserId(),principal.getAuthVersion(),role,form,clock.instant());
        Callable<Void> build=() -> {
            job.running();
            try { authorize(principal,role); job.complete(ReportEncoder.encode(dao.generate(job.getType(),job.getFrom(),job.getTo(),clock.instant()),job.getFormat())); }
            catch(RuntimeException failure) { job.failed(); LOG.warn("Report {} failed; details suppressed.",job.getId()); }
            return null;
        };
        FutureTask<Void> future=new FutureTask<>(build); job.future(future); jobs.put(job.getId(),job);
        try { executor.execute(future); } catch(RejectedExecutionException full) { jobs.remove(job.getId()); future.cancel(false); throw busy(); }
        return job.getId();
    }
    public synchronized List<ReportJob> list(TelecomPrincipal principal,Role role) {
        authorize(principal,role); prune();
        return jobs.values().stream().filter(j -> j.getOwner()==principal.getUserId() && j.getAuthVersion()==principal.getAuthVersion() && j.getRole()==role)
                .sorted(Comparator.comparing(ReportJob::getCreatedAt).reversed().thenComparing(ReportJob::getId)).collect(Collectors.toList());
    }
    public synchronized ReportJob status(TelecomPrincipal principal,Role role,UUID id) {
        authorize(principal,role); prune(); ReportJob job=jobs.get(id);
        if(job==null || job.getOwner()!=principal.getUserId() || job.getAuthVersion()!=principal.getAuthVersion() || job.getRole()!=role) { throw new WorkflowException(HttpStatus.NOT_FOUND,"Report job unavailable or expired."); }
        return job;
    }
    public synchronized byte[] download(TelecomPrincipal principal,Role role,UUID id) {
        ReportJob job=status(principal,role,id);
        if(!job.getStatus().equals("COMPLETE")) { throw new WorkflowException(HttpStatus.CONFLICT,"The report is not ready to download."); }
        return job.content();
    }
    public synchronized void delete(TelecomPrincipal principal,Role role,UUID id) { ReportJob job=status(principal,role,id); jobs.remove(id); job.cancel(); ((ThreadPoolExecutor)executor).purge(); }
    private void prune() {
        Instant expiry=clock.instant().minusSeconds(1800);
        jobs.values().removeIf(job -> { if(job.isTerminal() && job.getCreatedAt().isBefore(expiry)) { job.cancel(); return true; } return false; });
    }
    private static WorkflowException busy() { return new WorkflowException(HttpStatus.TOO_MANY_REQUESTS,"Report capacity reached. Wait for a job to finish and try again."); }
    @Override public synchronized void destroy() {
        jobs.values().forEach(ReportJob::cancel); jobs.clear(); executor.shutdownNow();
        try { executor.awaitTermination(5,TimeUnit.SECONDS); } catch(InterruptedException interrupted) { Thread.currentThread().interrupt(); }
    }
}
