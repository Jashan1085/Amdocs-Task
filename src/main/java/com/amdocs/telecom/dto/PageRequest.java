package com.amdocs.telecom.dto;

/** Zero-based offset paging. DAOs always apply a deterministic order before paging. */
public final class PageRequest {

    public static final int MAX_LIMIT = 100;

    private final int limit;
    private final int offset;

    public PageRequest(int limit, int offset) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("Page limit must be between 1 and " + MAX_LIMIT);
        }
        if (offset < 0) {
            throw new IllegalArgumentException("Page offset must be non-negative");
        }
        this.limit = limit;
        this.offset = offset;
    }

    public int getLimit() { return limit; }

    public int getOffset() { return offset; }
}
