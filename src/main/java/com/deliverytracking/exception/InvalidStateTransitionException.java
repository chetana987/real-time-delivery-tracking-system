package com.deliverytracking.exception;

import com.deliverytracking.entity.OrderStatus;

public class InvalidStateTransitionException extends RuntimeException {

    public InvalidStateTransitionException(OrderStatus current, OrderStatus requested) {
        super("Invalid status transition: " + current + " -> " + requested);
    }
}
