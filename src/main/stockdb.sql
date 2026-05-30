CREATE DATABASE IF NOT EXISTS stockdb;
USE stockdb;

-- Users table: password column sized for BCrypt (60 chars)
CREATE TABLE IF NOT EXISTS users (
    id       INT          PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(60)  NOT NULL,
    balance  DOUBLE       NOT NULL DEFAULT 10000
);

CREATE TABLE IF NOT EXISTS stocks (
    id            INT          PRIMARY KEY AUTO_INCREMENT,
    stock_name    VARCHAR(100) NOT NULL UNIQUE,
    current_price DOUBLE       NOT NULL
);

-- Portfolio scoped to a user; references both users and stocks
CREATE TABLE IF NOT EXISTS portfolio (
    id        INT    PRIMARY KEY AUTO_INCREMENT,
    user_id   INT    NOT NULL,
    stock_id  INT    NOT NULL,
    quantity  INT    NOT NULL,
    avg_price DOUBLE NOT NULL,
    FOREIGN KEY (user_id)  REFERENCES users(id)  ON DELETE CASCADE,
    FOREIGN KEY (stock_id) REFERENCES stocks(id) ON DELETE CASCADE,
    UNIQUE (user_id, stock_id)
);

-- Full history of every buy and sell
CREATE TABLE IF NOT EXISTS transactions (
    id         INT          PRIMARY KEY AUTO_INCREMENT,
    user_id    INT          NOT NULL,
    stock_name VARCHAR(100) NOT NULL,
    quantity   INT          NOT NULL,
    price      DOUBLE       NOT NULL,
    total      DOUBLE       NOT NULL,
    type       ENUM('BUY','SELL') NOT NULL,
    ts         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
