-- DopamineLite Notification Service Database Schema
-- PostgreSQL 14+

-- Create database (run as postgres user)
-- CREATE DATABASE dopaminelite_notifications;

-- Connect to the database
-- \c dopaminelite_notifications;

-- The notifications table will be auto-created by Hibernate with ddl-auto=update
-- This script provides the schema for reference and manual creation if needed

CREATE TABLE IF NOT EXISTS notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    channel VARCHAR(20) NOT NULL CHECK (channel IN ('IN_APP', 'EMAIL', 'WHATSAPP')),
    title VARCHAR(500) NOT NULL,
    body TEXT NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    read_at TIMESTAMP,
    delivery_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (delivery_status IN ('PENDING', 'SENT', 'FAILED')),
    template_key VARCHAR(100),
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_user_channel ON notifications(user_id, channel);
CREATE INDEX IF NOT EXISTS idx_user_is_read ON notifications(user_id, is_read);
CREATE INDEX IF NOT EXISTS idx_created_at ON notifications(created_at);
CREATE INDEX IF NOT EXISTS idx_delivery_status ON notifications(delivery_status);

-- Optional: Function to update updated_at timestamp automatically
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Optional: Trigger to auto-update updated_at
CREATE TRIGGER update_notifications_updated_at
    BEFORE UPDATE ON notifications
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Sample data for testing (optional)
-- INSERT INTO notifications (id, user_id, channel, title, body, is_read, delivery_status, template_key, metadata)
-- VALUES 
--     (gen_random_uuid(), gen_random_uuid(), 'IN_APP', 'Welcome!', 'Welcome to DopamineLite', false, 'SENT', 'STUDENT_VERIFIED', '{}'),
--     (gen_random_uuid(), gen_random_uuid(), 'EMAIL', 'Payment Approved', 'Your payment has been approved', false, 'SENT', 'PAYMENT_STATUS_CHANGED', '{"status": "APPROVED"}');

COMMENT ON TABLE notifications IS 'Stores all notifications for users across different channels';
COMMENT ON COLUMN notifications.user_id IS 'Reference to the user who receives this notification';
COMMENT ON COLUMN notifications.channel IS 'Delivery channel: IN_APP, EMAIL, or WHATSAPP';
COMMENT ON COLUMN notifications.is_read IS 'Whether the user has read this notification (mainly for IN_APP)';
COMMENT ON COLUMN notifications.delivery_status IS 'Status of delivery attempt: PENDING, SENT, or FAILED';
COMMENT ON COLUMN notifications.template_key IS 'Template identifier used to generate this notification';
COMMENT ON COLUMN notifications.metadata IS 'Additional event-specific data stored as JSON';
