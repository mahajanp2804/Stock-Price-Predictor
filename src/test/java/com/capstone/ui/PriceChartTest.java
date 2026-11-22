package com.capstone.ui;

import com.capstone.model.PricePoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PriceChartTest {

    @Test
    @DisplayName("axis ticks are round numbers that cover the data")
    void niceTicks() {
        assertArrayEquals(new double[]{300, 320, 340, 360}, PriceChart.niceTicks(312.4, 357.9, 5), 1e-9);
        assertArrayEquals(new double[]{0, 25, 50, 75, 100}, PriceChart.niceTicks(3, 97, 5), 1e-9);
        double[] flat = PriceChart.niceTicks(100, 100, 5);          // a flat line still gets a usable axis
        assertTrue(flat[0] < 100 && flat[flat.length - 1] > 100);
    }

    @Test
    @DisplayName("periods slice by calendar months back from the latest close")
    void slice() {
        List<PricePoint> h = new ArrayList<>();
        LocalDate d = LocalDate.of(2026, 1, 1);
        for (int i = 0; i < 366; i++) h.add(new PricePoint(d.plusDays(i), 100 + i));   // 2026-01-01 … 2027-01-01
        assertEquals(LocalDate.of(2026, 12, 1), PriceChart.slice(h, PriceChart.Period.ONE_MONTH).get(0).date());
        assertEquals(LocalDate.of(2026, 10, 1), PriceChart.slice(h, PriceChart.Period.THREE_MONTHS).get(0).date());
        assertEquals(366, PriceChart.slice(h, PriceChart.Period.ALL).size());
        assertEquals(1, PriceChart.slice(List.of(new PricePoint(d, 5)), PriceChart.Period.ONE_MONTH).size());
    }
}
