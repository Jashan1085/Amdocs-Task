package com.amdocs.telecom.dto;

import java.util.*;

public final class ResultPage<T> {
    public static final int SIZE = 20;
    private final List<T> items;
    private final int number;
    private final boolean next;
    public ResultPage(List<T> rows, int number) {
        this.items = Collections.unmodifiableList(new ArrayList<>(rows.subList(0, Math.min(SIZE, rows.size()))));
        this.next = rows.size() > SIZE; this.number = number;
    }
    public List<T> getItems() { return items; }
    public int getNumber() { return number; }
    public boolean isNext() { return next; }
    public boolean isPrevious() { return number > 0; }
}
