package com.cyopo.common.response;

import lombok.Getter;

import java.util.List;

@Getter
public class PageResponse<T> {

    private final List<T> data;
    private final long total;
    private final int page;
    private final int limit;
    private final int totalPages;

    public PageResponse(List<T> data, long total, int page, int limit) {
        this.data = data;
        this.total = total;
        this.page = page;
        this.limit = limit;
        this.totalPages = (int) Math.ceil((double) total / limit);
    }
}