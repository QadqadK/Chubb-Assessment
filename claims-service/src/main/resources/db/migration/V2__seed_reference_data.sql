-- Reference and demo data. All seeded users share the password "Password123!".

INSERT INTO markets (code, name, currency) VALUES
    ('MY', 'Malaysia',    'MYR'),
    ('SG', 'Singapore',   'SGD'),
    ('HK', 'Hong Kong',   'HKD'),
    ('TH', 'Thailand',    'THB'),
    ('ID', 'Indonesia',   'IDR'),
    ('VN', 'Vietnam',     'VND');

INSERT INTO teams (id, name, market_code) VALUES
    ('b1000000-0000-0000-0000-000000000001', 'Malaysia Claims Team', 'MY'),
    ('b1000000-0000-0000-0000-000000000002', 'Singapore Claims Team', 'SG');

INSERT INTO users (id, email, password_hash, full_name, role, market_code, team_id) VALUES
    ('a1000000-0000-0000-0000-000000000001', 'manager.my@example.com',  '$2b$10$4lQEnXZEvGY4JDh.q.rMOuj/jlYbtNGeig0z1TWeqpEy5kihanYye', 'Mariam Manager', 'MANAGER',  'MY', 'b1000000-0000-0000-0000-000000000001'),
    ('a1000000-0000-0000-0000-000000000002', 'officer.my1@example.com', '$2b$10$4lQEnXZEvGY4JDh.q.rMOuj/jlYbtNGeig0z1TWeqpEy5kihanYye', 'Omar Officer',   'OFFICER',  'MY', 'b1000000-0000-0000-0000-000000000001'),
    ('a1000000-0000-0000-0000-000000000003', 'officer.my2@example.com', '$2b$10$4lQEnXZEvGY4JDh.q.rMOuj/jlYbtNGeig0z1TWeqpEy5kihanYye', 'Olivia Officer', 'OFFICER',  'MY', 'b1000000-0000-0000-0000-000000000001'),
    ('a1000000-0000-0000-0000-000000000004', 'officer.sg1@example.com', '$2b$10$4lQEnXZEvGY4JDh.q.rMOuj/jlYbtNGeig0z1TWeqpEy5kihanYye', 'Wei Officer',     'OFFICER',  'SG', 'b1000000-0000-0000-0000-000000000002'),
    ('a1000000-0000-0000-0000-000000000005', 'claimant.my@example.com', '$2b$10$4lQEnXZEvGY4JDh.q.rMOuj/jlYbtNGeig0z1TWeqpEy5kihanYye', 'Aisha Claimant',  'CLAIMANT', 'MY', NULL),
    ('a1000000-0000-0000-0000-000000000006', 'claimant.sg@example.com', '$2b$10$4lQEnXZEvGY4JDh.q.rMOuj/jlYbtNGeig0z1TWeqpEy5kihanYye', 'Ben Claimant',    'CLAIMANT', 'SG', NULL);

INSERT INTO policies (id, policy_number, holder_id, policy_type, market_code, currency, coverage_limit, start_date, end_date) VALUES
    ('c1000000-0000-0000-0000-000000000001', 'MY-MOT-000101', 'a1000000-0000-0000-0000-000000000005', 'MOTOR',    'MY', 'MYR',  80000.00, '2026-01-01', '2027-01-01'),
    ('c1000000-0000-0000-0000-000000000002', 'MY-PRO-000102', 'a1000000-0000-0000-0000-000000000005', 'PROPERTY', 'MY', 'MYR', 500000.00, '2026-01-01', '2027-01-01'),
    ('c1000000-0000-0000-0000-000000000003', 'SG-MOT-000201', 'a1000000-0000-0000-0000-000000000006', 'MOTOR',    'SG', 'SGD',  60000.00, '2026-03-01', '2027-03-01');
