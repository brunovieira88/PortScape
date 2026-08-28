# CLAUDE.md

Guia de contexto para o Claude Code trabalhar neste projeto. Lê isto antes de qualquer alteração.

## Visão geral do projeto

**Nome:** Portscape
**Objetivo:** Ferramenta de auditoria de rede que transforma o resultado de um scan (nmap) numa "cidade" 3D interativa e navegável. Cada dispositivo na rede vira um edifício: altura = nº de portas abertas, cor = nível de risco, dispositivos novos/não autorizados destacam-se visualmente.

**Contexto:** Projeto pessoal de portfolio (não académico). Prioridade: impacto visual + qualidade de código defensável em entrevista, não velocidade de entrega. Scans limitados à rede local do autor — sem qualquer scanning de redes de terceiros.

## Stack

- **Backend:** Java 21 + Spring Boot 3 (REST API, execução assíncrona do scan)
- **Scanning:** nmap via `ProcessBuilder`, output em XML (`-oX -`), parse com JAXB ou uma lib tipo Nmap4j
- **Persistência:** PostgreSQL — guarda histórico de scans para permitir comparação/baseline entre snapshots
- **Frontend:** React + TypeScript + React Three Fiber (Three.js) para a cena 3D
- **Comunicação:** REST simples (polling do estado do job) — não usar WebSocket a não ser que sirva um propósito claro (ex: progresso do scan em tempo real)

## Estrutura de diretórios (alvo)

```
portscape/
├── backend/
│   ├── src/main/java/com/portscape/
│   │   ├── scan/          # execução do nmap, parsing XML
│   │   ├── risk/          # lógica de scoring de risco
│   │   ├── domain/        # entidades: Host, Port, Scan, Baseline
│   │   ├── api/           # controllers REST
│   │   └── layout/        # cálculo de posições 3D (grid por subnet)
│   └── src/test/java/...
├── frontend/
│   ├── src/
│   │   ├── scene/         # componentes Three.js (City, Building, Grid)
│   │   ├── ui/            # painel lateral de detalhes, lista de scans
│   │   └── api/           # cliente REST
└── CLAUDE.md
```

## Modelo de dados (núcleo)

- `Scan`: id, timestamp, subnet alvo, estado (pending/running/done/failed)
- `Host`: ip, hostname, os_guess, scan_id
- `Port`: host_id, número, protocolo, serviço, versão detetada
- `RiskScore`: host_id, score (0-100), razões (lista de strings explicando o score)
- `Baseline`: snapshot anterior usado para detetar hosts novos/alterados

O JSON servido ao frontend deve incluir, por host: posição calculada (x, z — grid por subnet; y é sempre 0, altura do edifício vem de `port_count`), cor derivada do `risk_score`, e um flag `is_new` / `is_changed` face ao baseline.

## Regras de scoring de risco (lógica própria — não delegar ao nmap)

Isto é o valor acrescentado do projeto, não só "correr nmap e mostrar". Pesos sugeridos (ajustáveis):
- Portas de alto risco expostas (Telnet 23, FTP sem TLS 21, RDP 3389, SMB 445) pesam mais que portas comuns/esperadas (80, 443)
- Serviço com versão desatualizada conhecida — se houver tempo, cruzar com NVD API para CVEs reais; caso contrário, lista estática de versões conhecidas por serem problemáticas é aceitável para o MVP
- Dispositivo fora do baseline (não visto em scans anteriores) soma risco por si só, independentemente das portas

## Fases de desenvolvimento (ordem sugerida)

1. **Backend — scan + parsing:** endpoint que corre nmap contra uma subnet e devolve JSON estruturado (sem scoring nem 3D ainda). Validar com a rede local do autor.
2. **Backend — scoring + persistência:** guardar scans na BD, implementar scoring, endpoint de comparação com baseline.
3. **Backend — layout 3D:** calcular posições (x, z) por subnet/host, expor no JSON.
4. **Frontend — cena estática:** grid + edifícios com altura/cor a partir de um JSON de exemplo (mockar antes de ligar à API real).
5. **Frontend — integração:** ligar à API, painel de detalhes ao clicar, lista de scans anteriores.
6. **Polish:** animações de câmara, destaque visual para hosts novos/alterados, dark mode "cidade à noite".
7. **Modo demo estático (GitHub Pages)**: o frontend deve conseguir correr sozinho, sem backend, carregando um JSON de exemplo pré-gerado (scan fictício/sintético) em vez de chamar a API real. Serve para ter um link ao vivo no README que qualquer pessoa pode abrir e explorar a cidade 3D sem instalar nada. Implementar via variável de ambiente/flag de build que troca a fonte de dados (API real vs. ficheiro JSON estático) — não duplicar componentes da cena 3D para isto.
## Convenções de código

- Java: seguir convenções Spring Boot standard, controllers finos (lógica em services), DTOs separados das entidades JPA
- Preferir simplicidade sobre over-engineering — evitar abstrações prematuras (ex: não introduzir padrões de plugin para "múltiplos scanners" antes de haver um segundo scanner de facto)
- Commits pequenos e descritivos, um passo de cada vez das fases acima
- Não introduzir WebSocket, filas de mensagens ou microserviços — este projeto é propositadamente um monólito simples; a complexidade deve estar na visualização e no scoring, não na infraestrutura
- Nomes de variáveis/métodos em inglês (convenção standard da indústria), comentários e mensagens de commit podem ser em português ou inglês, mas consistentes dentro do mesmo ficheiro
- Tratamento de erros explícito: parsing de XML do nmap e chamadas a processos externos devem tratar falhas (nmap não instalado, scan falhado, XML malformado) com exceções específicas, não deixar exceções genéricas subirem até ao utilizador
- Configuração (subnet por defeito, caminho do nmap, etc.) em `application.yml`, nunca hardcoded no código

## Testes

- Toda a lógica de negócio (scoring de risco, parsing do XML do nmap, cálculo de layout 3D) deve ter testes unitários — é a parte do projeto mais fácil de testar isoladamente e a mais valiosa de mostrar numa entrevista
- Usar JUnit 5 + Mockito (standard Spring Boot)
- Parsing do nmap: incluir pelo menos um teste com um ficheiro XML de exemplo real (guardar um `sample-scan.xml` em `src/test/resources`) em vez de só mocks — garante que o parser lida com o formato real do nmap
- Controllers: testes de integração leves com `@SpringBootTest` ou `@WebMvcTest` para os endpoints principais, não é preciso cobertura exaustiva aqui
- Não é preciso perseguir 100% de cobertura — prioriza testar lógica com decisões (scoring, deteção de baseline) sobre código trivial (getters/setters, DTOs)
- Cada fase de desenvolvimento (ver secção acima) só se considera "terminada" quando tiver testes para a lógica nova introduzida nessa fase

## Segurança e ética (importante)

- Este projeto só deve correr scans contra redes que o autor possui ou tem autorização explícita para testar (tipicamente `192.168.x.x` / `10.x.x.x` local)
- Não implementar nenhuma funcionalidade de scanning de IPs públicos/arbitrários
- README final deve deixar isto claro para quem visitar o repositório

## O que evitar sem alinhar primeiro

- Autenticação/multi-utilizador — é um projeto pessoal single-user, a não ser que surja uma boa razão
- Trocar nmap por scanner próprio "from scratch" — não é o foco do projeto
- Scans de redes remotas/públicas continuam fora de causa (ver secção de segurança e ética acima) — esta é a única restrição rígida

Fora isso, sugestões e ideias novas são bem-vindas mesmo que fujam ao plano inicial — dashboards de tendências, alertas, features de visualização mais ambiciosas, etc. Propõe antes de implementar algo grande fora do escopo da fase atual, mas não é preciso pedir autorização para pequenas melhorias ou ideias que encaixem naturalmente no que já está a ser construído.
