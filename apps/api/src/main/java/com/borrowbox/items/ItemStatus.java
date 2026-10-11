package com.borrowbox.items;

/** Derived item status (FR-20). REMOVED is only ever shown to admins and owners. */
public enum ItemStatus { AVAILABLE, RESERVED, ON_LOAN, PAUSED, REMOVED }
