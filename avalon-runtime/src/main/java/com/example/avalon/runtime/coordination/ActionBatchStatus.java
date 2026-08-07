package com.example.avalon.runtime.coordination;

public enum ActionBatchStatus {
    OPEN,
    PARTIALLY_COLLECTED,
    COMPLETED,
    EXPIRED,
    CANCELLED,
    INVALIDATED,
    COMMITTED
}
