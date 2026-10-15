package com.amdocs.telecom.service;
import com.amdocs.telecom.dao.impl.JdbcNotificationDAO;
import com.amdocs.telecom.dto.ResultPage;
import com.amdocs.telecom.exception.WorkflowException;
import com.amdocs.telecom.security.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.slf4j.*;
import java.time.*;
import java.util.*;

@Service
public class NotificationService {
    private static final Logger LOG=LoggerFactory.getLogger(NotificationService.class);
    private final JdbcNotificationDAO dao;
    private final NotificationDelivery delivery;
    private final TicketWorkflowService workflow;
    private final Clock clock;
    private final TransactionTemplate transaction,nested;
    public NotificationService(JdbcNotificationDAO dao,NotificationDelivery delivery,TicketWorkflowService workflow,Clock clock,PlatformTransactionManager manager) {
        this.dao=dao; this.delivery=delivery; this.workflow=workflow; this.clock=clock;
        transaction=new TransactionTemplate(manager); nested=new TransactionTemplate(manager); nested.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);
    }
    public int runOnce() {
        int result=0;
        for(Long id:dao.pending(clock.instant())) {
            if(Thread.currentThread().isInterrupted()) { break; }
            try { if(process(id)) { result++; } } catch(RuntimeException failure) { LOG.warn("Notification processing unavailable for {}; next scan will retry.",id); }
        }
        return result;
    }
    public boolean process(long id) {
        return transaction.execute(status -> {
            Instant now=clock.instant(); Map<String,Object> row=dao.lock(id,now).orElse(null); if(row==null) { return false; }
            int attempts=((Number)row.get("attempts")).intValue();
            if(attempts>=3) { dao.failed(id,attempts,now); return false; }
            dao.processing(id);
            try { nested.execute(inner -> { delivery.deliver(id,now); return null; }); }
            catch(RuntimeException failure) { dao.failed(id,attempts+1,now); return false; }
            dao.delivered(id,now); return true;
        });
    }
    public ResultPage<Map<String,Object>> inbox(TelecomPrincipal principal,Role role,boolean unread,int page) {
        AuthAccount actor=workflow.actor(principal,role,false);
        if(page<0 || page>1000000) { throw new WorkflowException(HttpStatus.BAD_REQUEST,"Invalid page number."); }
        return new ResultPage<>(dao.inbox(role.name(),profile(actor,role),unread,page*20),page);
    }
    public void read(TelecomPrincipal principal,Role role,long id) {
        transaction.execute(status -> {
            AuthAccount actor=workflow.actor(principal,role,true);
            if(role==Role.SERVICE_DESK) { throw new WorkflowException(HttpStatus.FORBIDDEN,"The desk processing view cannot mark recipient messages as read."); }
            if(dao.read(id,role.name(),profile(actor,role),clock.instant())!=1) { throw new WorkflowException(HttpStatus.NOT_FOUND,"Notification unavailable."); }
            return null;
        });
    }
    private Long profile(AuthAccount actor,Role role) { return role==Role.CUSTOMER ? actor.getCustomerId() : (role==Role.NETWORK_ENGINEER ? actor.getEngineerId() : null); }
}
