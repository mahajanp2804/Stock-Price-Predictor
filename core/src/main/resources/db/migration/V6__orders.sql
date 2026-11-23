-- Limit and stop orders. OPEN orders are checked against each new quote and
-- filled at that market price once triggered (FILLED), or REJECTED if the
-- trade can't go through (e.g. not enough cash or shares).

CREATE TABLE IF NOT EXISTS orders (
    id            INT           PRIMARY KEY AUTO_INCREMENT,
    user_id       INT           NOT NULL,
    symbol        VARCHAR(100)  NOT NULL,
    side          ENUM('BUY','SELL')  NOT NULL,
    order_type    ENUM('LIMIT','STOP') NOT NULL,
    quantity      INT           NOT NULL,
    trigger_price DECIMAL(15,4) NOT NULL,
    status        ENUM('OPEN','FILLED','REJECTED','CANCELLED') NOT NULL DEFAULT 'OPEN',
    created_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    closed_at     TIMESTAMP     NULL,
    fill_price    DECIMAL(15,4) NULL,
    message       VARCHAR(300)  NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_orders_open (status, symbol),
    INDEX idx_orders_user (user_id, status)
);
