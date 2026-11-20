-- Campus Register / H2. The application runs this file on every startup.
CREATE TABLE IF NOT EXISTS departments (
    code VARCHAR(12) PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS students (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    roll_no VARCHAR(24) NOT NULL UNIQUE,
    full_name VARCHAR(90) NOT NULL,
    department_code VARCHAR(12) NOT NULL REFERENCES departments(code),
    semester SMALLINT NOT NULL CHECK (semester BETWEEN 1 AND 8),
    email VARCHAR(160) NOT NULL DEFAULT '',
    status VARCHAR(12) NOT NULL CHECK (status IN ('Active', 'Graduated', 'Withdrawn')),
    version INTEGER NOT NULL DEFAULT 1
);
CREATE INDEX IF NOT EXISTS idx_students_cohort ON students(department_code, semester);
CREATE INDEX IF NOT EXISTS idx_students_name ON students(full_name);

CREATE TABLE IF NOT EXISTS attendance (
    student_id BIGINT NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    class_date DATE NOT NULL,
    state VARCHAR(8) NOT NULL CHECK (state IN ('Present', 'Absent')),
    PRIMARY KEY (student_id, class_date)
);
CREATE INDEX IF NOT EXISTS idx_attendance_date ON attendance(class_date);

CREATE TABLE IF NOT EXISTS marks (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    student_id BIGINT NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    subject VARCHAR_IGNORECASE(80) NOT NULL,
    assessment VARCHAR_IGNORECASE(60) NOT NULL,
    score DECIMAL(7,2) NOT NULL,
    maximum DECIMAL(7,2) NOT NULL,
    version INTEGER NOT NULL DEFAULT 1,
    UNIQUE (student_id, subject, assessment),
    CHECK (maximum > 0 AND score >= 0 AND score <= maximum)
);

CREATE TABLE IF NOT EXISTS staff_account (
    id SMALLINT PRIMARY KEY CHECK (id = 1),
    salt VARBINARY(32) NOT NULL,
    password_hash VARBINARY(64) NOT NULL
);

MERGE INTO departments (code, name) KEY(code) VALUES
    ('CE', 'Civil Engineering'), ('CSE', 'Computer Science & Engineering'),
    ('ECE', 'Electronics & Communication'), ('EE', 'Electrical Engineering'),
    ('IT', 'Information Technology'), ('ME', 'Mechanical Engineering');
