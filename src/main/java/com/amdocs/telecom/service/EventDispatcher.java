package com.amdocs.telecom.service;
import org.springframework.stereotype.Service;
import org.slf4j.*;
import java.util.Set;
import java.util.concurrent.*;

@Service
public class EventDispatcher {
    private static final Logger LOG=LoggerFactory.getLogger(EventDispatcher.class);
    private final BlockingQueue<Long> queue;
    private final Set<Long> scheduled=ConcurrentHashMap.newKeySet();
    private final NetworkEventService events;
    @org.springframework.beans.factory.annotation.Autowired
    public EventDispatcher(NetworkEventService events) { this(events,256); }
    EventDispatcher(NetworkEventService events,int capacity) { this.events=events; queue=new ArrayBlockingQueue<>(capacity); }
    public boolean enqueue(long id) {
        if(!scheduled.add(id)) { return true; }
        if(queue.offer(id)) { return true; }
        scheduled.remove(id); return false;
    }
    public void recover() { for(Long id:events.pending()) { if(!enqueue(id)) { break; } } }
    public boolean processNext() throws InterruptedException {
        Long id=queue.poll(1,TimeUnit.SECONDS); if(id==null) { return false; }
        try { events.process(id); } catch(RuntimeException failure) { LOG.warn("Network event {} unavailable; recovery will retry.",id); }
        finally { scheduled.remove(id); }
        return true;
    }
}
