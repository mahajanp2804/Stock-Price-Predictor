package com.capstone.data;

import com.capstone.model.WatchItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SymbolSearchTest {

    @Test
    @DisplayName("keeps US common stocks and ETFs, drops other types and duplicates")
    void parse() {
        String json = "{\"count\":5,\"result\":["
            + "{\"description\":\"NVIDIA Corporation\",\"displaySymbol\":\"NVDA\",\"symbol\":\"NVDA\",\"type\":\"Common Stock\"},"
            + "{\"description\":\"SPDR S&P 500 ETF\",\"displaySymbol\":\"SPY\",\"symbol\":\"SPY\",\"type\":\"ETP\"},"
            + "{\"description\":\"NVDA CALL\",\"displaySymbol\":\"NVDA2\",\"symbol\":\"NVDA2\",\"type\":\"Warrant\"},"
            + "{\"description\":\"NVIDIA Corporation\",\"displaySymbol\":\"NVDA\",\"symbol\":\"NVDA\",\"type\":\"Common Stock\"},"
            + "{\"description\":\"\",\"displaySymbol\":\"XYZ\",\"symbol\":\"XYZ\",\"type\":\"Common Stock\"}]}";
        assertEquals(List.of(
            new WatchItem("NVDA", "NVIDIA Corporation"),
            new WatchItem("SPY", "SPDR S&P 500 ETF"),
            new WatchItem("XYZ", "XYZ")), SymbolSearch.parse(json));
    }

    @Test
    @DisplayName("no matches or an error body give an empty list")
    void empty() {
        assertTrue(SymbolSearch.parse("{\"count\":0,\"result\":[]}").isEmpty());
        assertTrue(SymbolSearch.parse("{\"error\":\"Invalid API key\"}").isEmpty());
    }
}
