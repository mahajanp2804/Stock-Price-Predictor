-- Stocks are now identified by ticker symbol (stock_name holds e.g. 'AAPL'),
-- so any ticker can be added. Rename the five original stocks everywhere and
-- keep their company names for display.

UPDATE stocks            SET stock_name = 'AAPL'  WHERE stock_name = 'Apple';
UPDATE stocks            SET stock_name = 'MSFT'  WHERE stock_name = 'Microsoft';
UPDATE stocks            SET stock_name = 'AMZN'  WHERE stock_name = 'Amazon';
UPDATE stocks            SET stock_name = 'GOOGL' WHERE stock_name = 'Google';
UPDATE stocks            SET stock_name = 'TSLA'  WHERE stock_name = 'Tesla';

UPDATE transactions      SET stock_name = 'AAPL'  WHERE stock_name = 'Apple';
UPDATE transactions      SET stock_name = 'MSFT'  WHERE stock_name = 'Microsoft';
UPDATE transactions      SET stock_name = 'AMZN'  WHERE stock_name = 'Amazon';
UPDATE transactions      SET stock_name = 'GOOGL' WHERE stock_name = 'Google';
UPDATE transactions      SET stock_name = 'TSLA'  WHERE stock_name = 'Tesla';

UPDATE price_history     SET stock_name = 'AAPL'  WHERE stock_name = 'Apple';
UPDATE price_history     SET stock_name = 'MSFT'  WHERE stock_name = 'Microsoft';
UPDATE price_history     SET stock_name = 'AMZN'  WHERE stock_name = 'Amazon';
UPDATE price_history     SET stock_name = 'GOOGL' WHERE stock_name = 'Google';
UPDATE price_history     SET stock_name = 'TSLA'  WHERE stock_name = 'Tesla';

UPDATE history_fetch_log SET stock_name = 'AAPL'  WHERE stock_name = 'Apple';
UPDATE history_fetch_log SET stock_name = 'MSFT'  WHERE stock_name = 'Microsoft';
UPDATE history_fetch_log SET stock_name = 'AMZN'  WHERE stock_name = 'Amazon';
UPDATE history_fetch_log SET stock_name = 'GOOGL' WHERE stock_name = 'Google';
UPDATE history_fetch_log SET stock_name = 'TSLA'  WHERE stock_name = 'Tesla';

ALTER TABLE stocks ADD COLUMN company_name VARCHAR(200) NULL;

UPDATE stocks SET company_name = 'Apple Inc'             WHERE stock_name = 'AAPL';
UPDATE stocks SET company_name = 'Microsoft Corp'        WHERE stock_name = 'MSFT';
UPDATE stocks SET company_name = 'Amazon.com Inc'        WHERE stock_name = 'AMZN';
UPDATE stocks SET company_name = 'Alphabet Inc Class A'  WHERE stock_name = 'GOOGL';
UPDATE stocks SET company_name = 'Tesla Inc'             WHERE stock_name = 'TSLA';
