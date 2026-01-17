-- ========================================
-- PostgreSQL Database Initialization Script
-- Creates schemas, tables, and sample data for all microservices
-- ========================================

-- Create schemas for each microservice
CREATE SCHEMA IF NOT EXISTS evaluation;
CREATE SCHEMA IF NOT EXISTS sampling;
CREATE SCHEMA IF NOT EXISTS evidence;
CREATE SCHEMA IF NOT EXISTS remediation;
CREATE SCHEMA IF NOT EXISTS jira_service;

-- Grant privileges to dbadmin
GRANT ALL ON SCHEMA evaluation TO dbadmin;
GRANT ALL ON SCHEMA sampling TO dbadmin;
GRANT ALL ON SCHEMA evidence TO dbadmin;
GRANT ALL ON SCHEMA remediation TO dbadmin;
GRANT ALL ON SCHEMA jira_service TO dbadmin;

-- ========================================
-- Evaluation Service Tables
-- ========================================

CREATE TABLE IF NOT EXISTS evaluation.users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    age INTEGER,
    department VARCHAR(100),
    salary DECIMAL(12, 2),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_users_username ON evaluation.users(username);
CREATE INDEX IF NOT EXISTS idx_users_email ON evaluation.users(email);
CREATE INDEX IF NOT EXISTS idx_users_department ON evaluation.users(department);

-- Insert sample data
INSERT INTO evaluation.users (username, email, first_name, last_name, age, department, salary) 
VALUES 
    ('john.doe', 'john.doe@example.com', 'John', 'Doe', 30, 'Engineering', 75000.00),
    ('jane.smith', 'jane.smith@example.com', 'Jane', 'Smith', 28, 'Marketing', 65000.00),
    ('bob.wilson', 'bob.wilson@example.com', 'Bob', 'Wilson', 35, 'Sales', 70000.00),
    ('alice.johnson', 'alice.johnson@example.com', 'Alice', 'Johnson', 32, 'Engineering', 80000.00),
    ('charlie.brown', 'charlie.brown@example.com', 'Charlie', 'Brown', 29, 'HR', 60000.00)
ON CONFLICT (username) DO NOTHING;

-- ========================================
-- Sampling Service Tables
-- ========================================

CREATE TABLE IF NOT EXISTS sampling.samples (
    id BIGSERIAL PRIMARY KEY,
    sample_name VARCHAR(200) NOT NULL,
    sample_type VARCHAR(50),
    sample_data TEXT,
    status VARCHAR(50) DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_samples_status ON sampling.samples(status);
CREATE INDEX IF NOT EXISTS idx_samples_created_at ON sampling.samples(created_at);

-- Insert sample data
INSERT INTO sampling.samples (sample_name, sample_type, sample_data, status) 
VALUES 
    ('Sample-001', 'TYPE_A', '{"data": "value1"}', 'COMPLETED'),
    ('Sample-002', 'TYPE_B', '{"data": "value2"}', 'PENDING'),
    ('Sample-003', 'TYPE_A', '{"data": "value3"}', 'PROCESSING')
ON CONFLICT DO NOTHING;

-- ========================================
-- Evidence Service Tables
-- ========================================

CREATE TABLE IF NOT EXISTS evidence.evidence_records (
    id BIGSERIAL PRIMARY KEY,
    evidence_id VARCHAR(100) UNIQUE NOT NULL,
    evidence_type VARCHAR(50),
    description TEXT,
    file_path VARCHAR(500),
    metadata JSONB,
    created_by VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_evidence_id ON evidence.evidence_records(evidence_id);
CREATE INDEX IF NOT EXISTS idx_evidence_type ON evidence.evidence_records(evidence_type);
CREATE INDEX IF NOT EXISTS idx_evidence_created_at ON evidence.evidence_records(created_at);

-- Insert sample data
INSERT INTO evidence.evidence_records (evidence_id, evidence_type, description, file_path, metadata, created_by) 
VALUES 
    ('EVID-001', 'DOCUMENT', 'Compliance document', '/evidence/doc001.pdf', '{"size": "2MB", "format": "PDF"}', 'system'),
    ('EVID-002', 'IMAGE', 'Screenshot evidence', '/evidence/img002.png', '{"size": "500KB", "format": "PNG"}', 'system')
ON CONFLICT (evidence_id) DO NOTHING;

-- ========================================
-- Remediation Service Tables
-- ========================================

CREATE TABLE IF NOT EXISTS remediation.remediation_tasks (
    id BIGSERIAL PRIMARY KEY,
    task_id VARCHAR(100) UNIQUE NOT NULL,
    issue_type VARCHAR(100),
    severity VARCHAR(20),
    description TEXT,
    status VARCHAR(50) DEFAULT 'OPEN',
    assigned_to VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_remediation_task_id ON remediation.remediation_tasks(task_id);
CREATE INDEX IF NOT EXISTS idx_remediation_status ON remediation.remediation_tasks(status);
CREATE INDEX IF NOT EXISTS idx_remediation_severity ON remediation.remediation_tasks(severity);

-- Insert sample data
INSERT INTO remediation.remediation_tasks (task_id, issue_type, severity, description, status, assigned_to) 
VALUES 
    ('REM-001', 'SECURITY', 'HIGH', 'Fix SQL injection vulnerability', 'IN_PROGRESS', 'john.doe'),
    ('REM-002', 'PERFORMANCE', 'MEDIUM', 'Optimize database queries', 'OPEN', 'jane.smith'),
    ('REM-003', 'BUG', 'LOW', 'Fix UI alignment issue', 'COMPLETED', 'bob.wilson')
ON CONFLICT (task_id) DO NOTHING;

-- ========================================
-- JIRA Service Tables
-- ========================================

CREATE TABLE IF NOT EXISTS jira_service.jira_tickets (
    id BIGSERIAL PRIMARY KEY,
    ticket_key VARCHAR(50) UNIQUE NOT NULL,
    project_key VARCHAR(20),
    summary VARCHAR(500),
    description TEXT,
    issue_type VARCHAR(50),
    priority VARCHAR(20),
    status VARCHAR(50) DEFAULT 'TO_DO',
    assignee VARCHAR(100),
    reporter VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_jira_ticket_key ON jira_service.jira_tickets(ticket_key);
CREATE INDEX IF NOT EXISTS idx_jira_project_key ON jira_service.jira_tickets(project_key);
CREATE INDEX IF NOT EXISTS idx_jira_status ON jira_service.jira_tickets(status);

-- Insert sample data
INSERT INTO jira_service.jira_tickets (ticket_key, project_key, summary, description, issue_type, priority, status, assignee, reporter) 
VALUES 
    ('PROJ-001', 'PROJ', 'Implement user authentication', 'Add OAuth2 authentication flow', 'Story', 'HIGH', 'IN_PROGRESS', 'john.doe', 'alice.johnson'),
    ('PROJ-002', 'PROJ', 'Database migration', 'Migrate from H2 to PostgreSQL', 'Task', 'MEDIUM', 'TO_DO', 'jane.smith', 'alice.johnson'),
    ('PROJ-003', 'PROJ', 'Fix login bug', 'Users cannot login after password reset', 'Bug', 'CRITICAL', 'DONE', 'bob.wilson', 'charlie.brown')
ON CONFLICT (ticket_key) DO NOTHING;

-- ========================================
-- Create Update Timestamp Trigger Function
-- ========================================

CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Apply triggers to tables with updated_at column
CREATE TRIGGER update_users_updated_at BEFORE UPDATE ON evaluation.users FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_remediation_tasks_updated_at BEFORE UPDATE ON remediation.remediation_tasks FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_jira_tickets_updated_at BEFORE UPDATE ON jira_service.jira_tickets FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ========================================
-- Grant Table Permissions
-- ========================================

GRANT ALL ON ALL TABLES IN SCHEMA evaluation TO dbadmin;
GRANT ALL ON ALL TABLES IN SCHEMA sampling TO dbadmin;
GRANT ALL ON ALL TABLES IN SCHEMA evidence TO dbadmin;
GRANT ALL ON ALL TABLES IN SCHEMA remediation TO dbadmin;
GRANT ALL ON ALL TABLES IN SCHEMA jira_service TO dbadmin;

GRANT ALL ON ALL SEQUENCES IN SCHEMA evaluation TO dbadmin;
GRANT ALL ON ALL SEQUENCES IN SCHEMA sampling TO dbadmin;
GRANT ALL ON ALL SEQUENCES IN SCHEMA evidence TO dbadmin;
GRANT ALL ON ALL SEQUENCES IN SCHEMA remediation TO dbadmin;
GRANT ALL ON ALL SEQUENCES IN SCHEMA jira_service TO dbadmin;

-- ========================================
-- Print Confirmation
-- ========================================

DO $$
DECLARE
    user_count INTEGER;
    sample_count INTEGER;
    evidence_count INTEGER;
    remediation_count INTEGER;
    jira_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO user_count FROM evaluation.users;
    SELECT COUNT(*) INTO sample_count FROM sampling.samples;
    SELECT COUNT(*) INTO evidence_count FROM evidence.evidence_records;
    SELECT COUNT(*) INTO remediation_count FROM remediation.remediation_tasks;
    SELECT COUNT(*) INTO jira_count FROM jira_service.jira_tickets;
    
    RAISE NOTICE '';
    RAISE NOTICE '========================================';
    RAISE NOTICE 'Database initialized successfully!';
    RAISE NOTICE '========================================';
    RAISE NOTICE 'Schemas created: 5';
    RAISE NOTICE 'Users: %', user_count;
    RAISE NOTICE 'Samples: %', sample_count;
    RAISE NOTICE 'Evidence records: %', evidence_count;
    RAISE NOTICE 'Remediation tasks: %', remediation_count;
    RAISE NOTICE 'JIRA tickets: %', jira_count;
    RAISE NOTICE '========================================';
END $$;
