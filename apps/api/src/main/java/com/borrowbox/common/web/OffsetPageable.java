package com.borrowbox.common.web;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Pageable for an arbitrary offset/limit (Spring's PageRequest is page-number based). */
public record OffsetPageable(long offset, int limit, Sort sort) implements Pageable {
  @Override
  public int getPageNumber() {
    return (int) (offset / limit);
  }

  @Override
  public int getPageSize() {
    return limit;
  }

  @Override
  public long getOffset() {
    return offset;
  }

  @Override
  public Sort getSort() {
    return sort;
  }

  @Override
  public Pageable next() {
    return new OffsetPageable(offset + limit, limit, sort);
  }

  @Override
  public Pageable previousOrFirst() {
    return new OffsetPageable(Math.max(0, offset - limit), limit, sort);
  }

  @Override
  public Pageable first() {
    return new OffsetPageable(0, limit, sort);
  }

  @Override
  public Pageable withPage(int pageNumber) {
    return new OffsetPageable((long) pageNumber * limit, limit, sort);
  }

  @Override
  public boolean hasPrevious() {
    return offset > 0;
  }
}
