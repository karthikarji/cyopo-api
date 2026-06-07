-- V19__fix_coupon_timestamp_timezone.sql
ALTER TABLE billing.coupons
ALTER COLUMN valid_from  TYPE TIMESTAMPTZ
    USING valid_from AT TIME ZONE 'UTC',
    ALTER COLUMN valid_until TYPE TIMESTAMPTZ
    USING valid_until AT TIME ZONE 'UTC';