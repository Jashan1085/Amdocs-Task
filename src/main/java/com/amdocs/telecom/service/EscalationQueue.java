package com.amdocs.telecom.service;
import com.amdocs.telecom.dto.SlaWorkItem;
import java.util.*;
public final class EscalationQueue {
    private final PriorityQueue<SlaWorkItem> queue=new PriorityQueue<>(Comparator.comparingInt(SlaWorkItem::getRank).thenComparingLong(SlaWorkItem::getTicketId));
    public synchronized void addAll(Collection<SlaWorkItem> items) { queue.addAll(items); }
    public synchronized Optional<SlaWorkItem> poll() { return Optional.ofNullable(queue.poll()); }
}
