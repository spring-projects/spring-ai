BEGIN
    EXECUTE IMMEDIATE 'CREATE TABLE SPRING_AI_BATCH_EXECUTION (
        batch_id VARCHAR2(255) NOT NULL PRIMARY KEY,
        endpoint VARCHAR2(255) NOT NULL,
        status VARCHAR2(50) NOT NULL,
        request_count NUMBER(10) NOT NULL,
        input_file_id VARCHAR2(255),
        created_at TIMESTAMP NOT NULL,
        updated_at TIMESTAMP NOT NULL
    )';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -955 THEN
            RAISE;
        END IF;
END;
