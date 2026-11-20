-- Fictional demonstration records. Loaded only when staff select Load demo records.
INSERT INTO students (roll_no, full_name, department_code, semester, email, status) VALUES
  ('CSE24-001', 'Aarohi Reddy', 'CSE', 3, 'aarohi@example.org', 'Active'),
  ('ECE23-014', 'Adil Khan', 'ECE', 5, 'adil@example.org', 'Active'),
  ('IT25-009', 'Diya Patel', 'IT', 1, 'diya@example.org', 'Active'),
  ('CSE24-018', 'Harini Rao', 'CSE', 3, 'harini@example.org', 'Active'),
  ('CE22-004', 'Ishan Shah', 'CE', 8, '', 'Graduated'),
  ('ME23-007', 'Kavya Nair', 'ME', 5, 'kavya@example.org', 'Active'),
  ('EE24-011', 'Neel Verma', 'EE', 3, '', 'Active'),
  ('CSE24-022', 'Saanvi Iyer', 'CSE', 3, 'saanvi@example.org', 'Active');

INSERT INTO marks (student_id, subject, assessment, score, maximum)
SELECT id, 'Advanced Java', 'Practical 1', 27, 30 FROM students WHERE roll_no = 'CSE24-001';

INSERT INTO attendance (student_id, class_date, state)
SELECT id, CURRENT_DATE - 1, 'Present' FROM students WHERE status = 'Active';
