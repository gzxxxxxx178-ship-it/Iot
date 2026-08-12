-- V3: Research evidence schema
-- Adds experiment run, metrics and RAG query tables for the research validation workbench.
-- All tables include owner_username for per-user data isolation.

CREATE TABLE IF NOT EXISTS research_experiment_runs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    run_key VARCHAR(128) NOT NULL,
    owner_username VARCHAR(100) NOT NULL,
    experiment_type VARCHAR(32) NOT NULL,
    title VARCHAR(255) NOT NULL,
    source_type VARCHAR(32) NOT NULL DEFAULT 'SIMULATION',
    status VARCHAR(16) NOT NULL,
    result_summary TEXT,
    limitations TEXT,
    manifest_path VARCHAR(500),
    executed_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_research_owner_runkey (owner_username, run_key),
    KEY idx_research_owner_type_time (owner_username, experiment_type, executed_at)
);

CREATE TABLE IF NOT EXISTS research_experiment_metrics (
    id BIGINT NOT NULL AUTO_INCREMENT,
    run_id BIGINT NOT NULL,
    metric_group VARCHAR(64),
    method_name VARCHAR(64),
    metric_name VARCHAR(100) NOT NULL,
    metric_value DOUBLE NOT NULL,
    unit VARCHAR(32),
    higher_is_better BOOLEAN,
    notes VARCHAR(500),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_research_metrics_runid (run_id)
);

CREATE TABLE IF NOT EXISTS research_rag_queries (
    id BIGINT NOT NULL AUTO_INCREMENT,
    owner_username VARCHAR(100) NOT NULL,
    question VARCHAR(1000) NOT NULL,
    answer TEXT,
    abstained BOOLEAN NOT NULL DEFAULT FALSE,
    abstain_reason VARCHAR(128),
    retriever VARCHAR(32) NOT NULL DEFAULT 'bm25',
    threshold_value DOUBLE NOT NULL,
    top_score DOUBLE,
    citations_json TEXT,
    evidence_json TEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_research_rag_owner_time (owner_username, created_at)
);
