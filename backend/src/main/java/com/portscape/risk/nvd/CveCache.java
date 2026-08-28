package com.portscape.risk.nvd;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portscape.config.NvdProperties;
import com.portscape.persistence.CveLookupEntity;
import com.portscape.persistence.CveLookupRepository;

/**
 * Cache das respostas do NVD, em Postgres.
 *
 * <p>E o que torna a consulta de CVEs praticavel: o NVD limita a 5 pedidos / 30s sem
 * API key, e um /24 pode ter dezenas de CPEs distintos. Ao segundo scan da mesma rede
 * quase tudo vem daqui, e o custo desaparece.
 *
 * <p>Persistente e nao em memoria por causa disso mesmo -- uma cache que morre a cada
 * restart nao resolveria o problema que existe para resolver.
 */
@Component
public class CveCache {

    private static final Logger log = LoggerFactory.getLogger(CveCache.class);
    private static final TypeReference<List<Cve>> CVE_LIST = new TypeReference<>() {
    };

    private final CveLookupRepository repository;
    private final ObjectMapper objectMapper;
    private final NvdProperties properties;
    private final Clock clock;

    public CveCache(CveLookupRepository repository, ObjectMapper objectMapper,
                    NvdProperties properties, Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.clock = clock;
    }

    /** @return os CVEs guardados, ou vazio se nao houver entrada ou ja tiver expirado */
    @Transactional(readOnly = true)
    public Optional<List<Cve>> get(String cpe) {
        return repository.findById(cpe)
                .flatMap(entity -> deserialize(entity).filter(cves -> isFresh(entity, cves)));
    }

    @Transactional
    public void put(String cpe, List<Cve> cves) {
        try {
            String payload = objectMapper.writeValueAsString(cves);
            CveLookupEntity entity = repository.findById(cpe)
                    .orElseGet(() -> new CveLookupEntity(cpe, payload, clock.instant()));
            entity.setPayload(payload);
            entity.setFetchedAt(clock.instant());
            repository.save(entity);
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException e) {
            // Falhar a escrever na cache nao pode custar o scan -- so o proximo lookup.
            log.warn("Nao foi possivel guardar em cache os CVEs de {}: {}", cpe, e.toString());
        }
    }

    /**
     * Uma entrada vazia expira mais cedo. "Sem CVEs" tanto pode ser um produto
     * realmente sem vulnerabilidades como um nome que o NVD nao reconheceu -- e o
     * segundo caso nao merece uma semana de silencio.
     */
    private boolean isFresh(CveLookupEntity entity, List<Cve> cves) {
        Duration ttl = cves.isEmpty() ? properties.emptyCacheTtl() : properties.cacheTtl();
        return entity.getFetchedAt().plus(ttl).isAfter(clock.instant());
    }

    /**
     * Uma entrada ilegivel (formato antigo, escrita a meio) e tratada como ausente:
     * volta-se a consultar o NVD em vez de rebentar.
     */
    private Optional<List<Cve>> deserialize(CveLookupEntity entity) {
        try {
            return Optional.of(objectMapper.readValue(entity.getPayload(), CVE_LIST));
        } catch (Exception e) {
            log.warn("Entrada de cache ilegivel para {}, vai ser reconsultada: {}",
                    entity.getCpe(), e.toString());
            return Optional.empty();
        }
    }
}
