DROP TABLE IF EXISTS clubs;
DROP TABLE IF EXISTS schools;

CREATE TABLE IF NOT EXISTS schools (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    school_name VARCHAR(200) NOT NULL
);

CREATE TABLE IF NOT EXISTS clubs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(150) NOT NULL,
    alias_name VARCHAR(150),
    description TEXT,
    category VARCHAR(100),
    meeting_schedule VARCHAR(150),
    location VARCHAR(150),
    contact_email VARCHAR(150),
    advisor VARCHAR(150),
    image_url VARCHAR(300),
    member_count INT NOT NULL DEFAULT 0,
    achievements JSON NOT NULL DEFAULT (JSON_ARRAY()),
    school_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_clubs_school FOREIGN KEY (school_id) REFERENCES schools(id)
);
