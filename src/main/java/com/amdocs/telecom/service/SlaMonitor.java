package com.amdocs.telecom.service;
import com.amdocs.telecom.dao.impl.JdbcSlaDAO;
import com.amdocs.telecom.dto.SlaWorkItem;
import org.slf4j.*;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class SlaMonitor {
    private static final Logger LOG=LoggerFactory.getLogger(SlaMonitor.class);
    private final JdbcSlaDAO dao;
    private final EscalationService escalations;
    public SlaMonitor(JdbcSlaDAO dao,EscalationService escalations) { this.dao=dao; this.escalations=escalations; }
    public int runOnce() {
        int rank=0,total=0; long id=0;
        while(true) {
            List<SlaWorkItem> batch=dao.scan(rank,id);
            if(batch.isEmpty()) { return total; }
            EscalationQueue queue=new EscalationQueue(); queue.addAll(batch);
            Optional<SlaWorkItem> item;
            while((item=queue.poll()).isPresent()) {
                if(Thread.currentThread().isInterrupted()) { return total; }
                try { total+=escalations.inspect(item.get().getTicketId()); }
                catch(RuntimeException failure) { LOG.warn("SLA check failed for ticket {}; will retry next scan.",item.get().getTicketId()); }
            }
            SlaWorkItem last=batch.get(batch.size()-1); rank=last.getRank(); id=last.getTicketId();
        }
    }
}
