package com.capstone.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static com.capstone.model.Order.Side.*;
import static com.capstone.model.Order.Type.*;
import static org.junit.jupiter.api.Assertions.*;

class OrderTest {

    private static final BigDecimal T = new BigDecimal("100.0000");

    @Test
    @DisplayName("limit buy fills at or below the trigger; limit sell at or above")
    void limits() {
        assertTrue(Order.triggers(BUY, LIMIT, T, 99.99));
        assertTrue(Order.triggers(BUY, LIMIT, T, 100.00));
        assertFalse(Order.triggers(BUY, LIMIT, T, 100.01));
        assertTrue(Order.triggers(SELL, LIMIT, T, 100.01));
        assertTrue(Order.triggers(SELL, LIMIT, T, 100.00));
        assertFalse(Order.triggers(SELL, LIMIT, T, 99.99));
    }

    @Test
    @DisplayName("stop buy fills when the price rises to the trigger; stop sell when it falls to it")
    void stops() {
        assertTrue(Order.triggers(BUY, STOP, T, 100.50));
        assertFalse(Order.triggers(BUY, STOP, T, 99.50));
        assertTrue(Order.triggers(SELL, STOP, T, 99.50));
        assertTrue(Order.triggers(SELL, STOP, T, 100.00));
        assertFalse(Order.triggers(SELL, STOP, T, 100.50));
    }

    @Test
    @DisplayName("prices are compared at 4 decimal places, like stored prices")
    void precision() {
        assertTrue(Order.triggers(BUY, LIMIT, T, 100.00004));     // rounds to 100.0000
        assertFalse(Order.triggers(BUY, LIMIT, T, 100.00006));    // rounds to 100.0001
    }
}
