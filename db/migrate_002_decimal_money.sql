-- One-time upgrade for databases created before money columns became DECIMAL.
-- Safe to run once on an existing stockdb; new installs only need stockdb.sql.
USE stockdb;

ALTER TABLE users        MODIFY balance       DECIMAL(15,2) NOT NULL DEFAULT 10000.00;
ALTER TABLE stocks       MODIFY current_price DECIMAL(15,4) NOT NULL;
ALTER TABLE portfolio    MODIFY avg_price     DECIMAL(15,4) NOT NULL;
ALTER TABLE transactions MODIFY price         DECIMAL(15,4) NOT NULL,
                         MODIFY total         DECIMAL(15,2) NOT NULL;
