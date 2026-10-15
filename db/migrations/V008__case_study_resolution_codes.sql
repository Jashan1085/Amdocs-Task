-- Preserve historical milestone-6 values while supporting the case-study catalog.
BEGIN;
ALTER TABLE ticket_resolution DROP CONSTRAINT ticket_resolution_resolution_code_check;
ALTER TABLE ticket_resolution ADD CONSTRAINT ticket_resolution_resolution_code_check CHECK(resolution_code IN
    ('CONFIGURATION_FIXED','HARDWARE_REPLACED','SERVICE_RESTORED','CUSTOMER_GUIDANCE','OTHER',
     'HARDWARE_FAILURE','CONFIGURATION_ERROR','NETWORK_CONGESTION','SOFTWARE_FAILURE',
     'FIBER_CUT','POWER_FAILURE','CUSTOMER_DEVICE','UNKNOWN'));
COMMIT;
