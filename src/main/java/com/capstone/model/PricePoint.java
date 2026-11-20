package com.capstone.model;

import java.time.LocalDate;

/** One trading day's closing price. */
public record PricePoint(LocalDate date, double close) {}
