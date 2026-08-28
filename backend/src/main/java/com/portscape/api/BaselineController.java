package com.portscape.api;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.portscape.api.dto.BaselineDto;
import com.portscape.api.dto.PinBaselineRequest;
import com.portscape.baseline.BaselineService;

import jakarta.validation.Valid;

/**
 * Fixar um baseline e opcional: sem nada fixado, cada scan compara-se com o anterior
 * da mesma rede. Isto serve para congelar um estado conhecido como bom e medir tudo
 * contra ele, em vez de contra a ultima vez.
 *
 * <p>O target vai em corpo/query e nao no caminho de proposito: contem uma barra
 * ({@code 192.168.1.0/24}) e uma barra codificada num path variable e rejeitada pelo
 * Tomcat por defeito. Levantar essa restricao so para embelezar o URL nao valeria a
 * troca.
 */
@RestController
@RequestMapping("/api/baselines")
public class BaselineController {

    private final BaselineService baselineService;

    public BaselineController(BaselineService baselineService) {
        this.baselineService = baselineService;
    }

    @GetMapping
    public List<BaselineDto> list() {
        return baselineService.findAll().stream().map(BaselineDto::from).toList();
    }

    /**
     * Fixa um scan como referencia. A rede vem do proprio scan -- assim nao ha forma
     * de fixar um scan de uma rede como baseline de outra.
     */
    @PostMapping
    public BaselineDto pin(@Valid @RequestBody PinBaselineRequest request) {
        return BaselineDto.from(baselineService.pin(request.scanId()));
    }

    /** Volta ao baseline implicito (o scan anterior) para aquela rede. */
    @DeleteMapping
    public ResponseEntity<Void> unpin(@RequestParam String target) {
        return baselineService.unpin(target)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
