package com.capstone.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One row of a user's trade history. */
public record Transaction(LocalDateTime timestamp, String type, String stockName,
                          int quantity, BigDecimal price, BigDecimal total) {}
