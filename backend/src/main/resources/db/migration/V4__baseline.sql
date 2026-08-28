-- Baseline fixado por rede.
--
-- Chave primaria no target, e nao no scan: por rede so faz sentido haver uma
-- referencia de cada vez. Sem entrada aqui, a comparacao cai no scan anterior
-- (baseline implicito) -- ver BaselineResolver.

CREATE TABLE baseline (
    target  VARCHAR(64) PRIMARY KEY,
    scan_id UUID        NOT NULL REFERENCES scan (id) ON DELETE CASCADE,
    pinned_at TIMESTAMPTZ NOT NULL
);
