ALTER TABLE restaurant
    ADD COLUMN dine_in  boolean NOT NULL DEFAULT true,
    ADD COLUMN take_out boolean NOT NULL DEFAULT false,
    ADD COLUMN delivery boolean NOT NULL DEFAULT false;
