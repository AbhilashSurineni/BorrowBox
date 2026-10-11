package com.borrowbox.common.web;

/** page >= 1 (default 1), 1 <= limit <= 100 (default 20). */
public record PageQuery(int page, int limit) {
  public int offset() {
    return (page - 1) * limit;
  }

  public org.springframework.data.domain.Pageable pageable(org.springframework.data.domain.Sort sort) {
    return new OffsetPageable(offset(), limit, sort);
  }
}
