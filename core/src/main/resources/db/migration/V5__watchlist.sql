-- Each user's list of stocks to follow (and trade). Up to 20 per user,
-- enforced in WatchlistManager.

CREATE TABLE IF NOT EXISTS watchlist (
    user_id      INT          NOT NULL,
    symbol       VARCHAR(100) NOT NULL,
    company_name VARCHAR(200) NOT NULL,
    added_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, symbol),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Existing users get the five stocks the app used to show everyone
INSERT IGNORE INTO watchlist (user_id, symbol, company_name)
SELECT u.id, d.symbol, d.company_name
FROM users u
CROSS JOIN (
    SELECT 'AAPL' AS symbol, 'Apple Inc' AS company_name UNION ALL
    SELECT 'MSFT', 'Microsoft Corp' UNION ALL
    SELECT 'AMZN', 'Amazon.com Inc' UNION ALL
    SELECT 'GOOGL', 'Alphabet Inc Class A' UNION ALL
    SELECT 'TSLA', 'Tesla Inc'
) d;
