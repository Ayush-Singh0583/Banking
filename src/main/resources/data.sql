-- Test data: two accounts so we can try transfers (H2 is in-memory, so this runs on every start)
INSERT INTO account (owner_name, balance) VALUES ('Ayush', 1000.00);
INSERT INTO account (owner_name, balance) VALUES ('Riya', 500.00);
