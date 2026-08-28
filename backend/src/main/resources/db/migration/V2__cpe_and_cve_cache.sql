-- CPEs por porta (vem da fase de deteccao de versao do nmap) e cache das
-- consultas ao NVD. A cache e o que torna a consulta de CVEs viavel: sem ela,
-- o rate limit do NVD (5 pedidos / 30s sem API key) dominava a duracao do scan.

CREATE TABLE port_cpe (
    port_id BIGINT       NOT NULL REFERENCES port (id) ON DELETE CASCADE,
    cpe     VARCHAR(255) NOT NULL,
    PRIMARY KEY (port_id, cpe)
);

CREATE TABLE cve_lookup (
    cpe        VARCHAR(255) PRIMARY KEY,
    payload    JSONB        NOT NULL,
    fetched_at TIMESTAMPTZ  NOT NULL
);
