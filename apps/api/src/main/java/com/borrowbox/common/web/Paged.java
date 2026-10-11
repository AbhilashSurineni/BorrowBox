package com.borrowbox.common.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.function.Function;

/** { "data": [...], "meta": { page, limit, total, totalPages } } — same as the TS API. */
public record Paged<T>(List<T> data, Meta meta) {
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Meta(int page, int limit, long total, long totalPages, Long unreadCount) {}

  public static <T> Paged<T> of(List<T> data, long total, PageQuery q) {
    return new Paged<>(data, new Meta(q.page(), q.limit(), total, Math.max(1, (total + q.limit() - 1) / q.limit()), null));
  }

  public Paged<T> withUnreadCount(long unread) {
    return new Paged<>(data, new Meta(meta.page(), meta.limit(), meta.total(), meta.totalPages(), unread));
  }

  public <R> Paged<R> map(Function<T, R> fn) {
    return new Paged<>(data.stream().map(fn).toList(), meta);
  }
}
