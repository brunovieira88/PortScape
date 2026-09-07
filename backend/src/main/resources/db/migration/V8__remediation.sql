-- O plano de remediacao: o que fazer primeiro, e quanto e que cada accao vale.
--
-- Gravado e nao recalculado na leitura, pela mesma razao que o score (ver V3) e os
-- CVEs por porta (ver V7): um scan de ha um mes tem de continuar a mostrar o que se
-- recomendava nessa altura, e o plano depende dos pesos e dos CVEs que se conheciam
-- nesse dia.
CREATE TABLE remediation (
    host_id        BIGINT       NOT NULL REFERENCES host (id) ON DELETE CASCADE,
    -- A ordem e escolhida pelo RemediationPlanner (mais eficaz primeiro) e tem de
    -- sobreviver a ida e volta a base de dados.
    position       INTEGER      NOT NULL,
    code           VARCHAR(64)  NOT NULL,
    action         TEXT         NOT NULL,
    -- Sem tecto: e a soma NAO saturada das razoes que mede o efeito real. Num host com
    -- razoes a somar 145 o score mostra 100, e fechar uma porta de 35 nao o mexe --
    -- points_removed e o unico numero que diz que a accao vale alguma coisa.
    points_removed INTEGER      NOT NULL,
    -- O score que o host passaria a ter, esse sim saturado em 100.
    score_after    INTEGER      NOT NULL,
    PRIMARY KEY (host_id, position)
);
