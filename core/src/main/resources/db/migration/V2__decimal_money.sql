-- Money columns become exact DECIMAL: cash to the cent, share prices to 4 dp.
-- MODIFY is idempotent, so this is safe on databases already upgraded by hand.

ALTER TABLE users        MODIFY balance       DECIMAL(15,2) NOT NULL DEFAULT 10000.00;
ALTER TABLE stocks       MODIFY current_price DECIMAL(15,4) NOT NULL;
ALTER TABLE portfolio    MODIFY avg_price     DECIMAL(15,4) NOT NULL;
ALTER TABLE transactions MODIFY price         DECIMAL(15,4) NOT NULL,
                         MODIFY total         DECIMAL(15,2) NOT NULL;
