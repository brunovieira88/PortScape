-- Os CVEs deixam de existir so como texto dentro de uma razao de risco.
--
-- Tabela normalizada e nao JSONB, ao contrario do cve_lookup: aquilo e uma cache por
-- CPE, com TTL, que se deita fora e se volta a consultar. Isto e o registo do que o
-- NVD sabia no momento do scan -- pela mesma razao que o score e gravado e nao
-- recalculado na leitura (ver V3), um scan de ha um mes tem de continuar a mostrar as
-- falhas que se conheciam nesse dia, e nao as de hoje.
CREATE TABLE port_cve (
    port_id        BIGINT       NOT NULL REFERENCES port (id) ON DELETE CASCADE,
    -- A ordem e escolhida pelo PortCveEnricher (pior CVSS primeiro, sem score no fim)
    -- e tem de sobreviver a ida e volta a base de dados: ordenar por cvss_score na
    -- leitura nao a reproduz, porque os CVEs sem score nao tem por onde desempatar.
    position       INTEGER      NOT NULL,
    cve_id         VARCHAR(32)  NOT NULL,
    cvss_score     NUMERIC(3,1),
    severity       VARCHAR(16),
    -- 256 e nao 128: um vector CVSS v4.0 real do NVD tem 174 caracteres, porque a
    -- API emite todas as metricas opcionais como ":X" (nao definida). Um v3.1 cabe
    -- em 45 e um v2 em 26 -- e por isso que so um scan a um produto com metricas
    -- v4.0 e que fazia isto rebentar.
    cvss_vector    VARCHAR(256),
    published_at   TIMESTAMPTZ,
    description    TEXT,
    -- Listagem KEV da CISA. NULL significa "nao consta do catalogo", que nao e o
    -- mesmo que "nao esta a ser explorado" -- ver KevCatalog.
    kev_date_added DATE,
    kev_ransomware BOOLEAN,
    kev_name       VARCHAR(255),
    kev_action     TEXT,
    PRIMARY KEY (port_id, position)
);

-- Quantos CVEs existiam mesmo, antes do tecto de portscape.nvd.max-cves-per-port.
-- Sem isto, mostrar 25 seria indistinguivel de mostrar 25 de 431 -- e essa e a
-- diferenca entre truncar e mentir por omissao.
ALTER TABLE port ADD COLUMN cve_total INTEGER NOT NULL DEFAULT 0;

-- As entradas em cache foram escritas antes de o Cve ter vector e data de publicacao,
-- e desserializam sem eles. E cache: deita-se fora em vez de se guardar meia
-- informacao durante os sete dias do TTL.
DELETE FROM cve_lookup;
