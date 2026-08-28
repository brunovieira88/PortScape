# Portscape

Ferramenta de auditoria de rede que transforma o resultado de um scan nmap numa
cidade 3D navegável: cada dispositivo é um edifício, a altura vem do número de
portas abertas e a cor do nível de risco.

> **Uso responsável.** O Portscape só faz scan de redes privadas
> (`10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`, loopback). Qualquer outro
> alvo é rejeitado com HTTP 400 — a restrição está no código
> (`TargetValidator`), não apenas neste README. Faz scan apenas de redes que
> possuis ou tens autorização explícita para testar.

## Estado

| Fase | Descrição | Estado |
|---|---|---|
| 1 | Backend — scan + parsing | ✅ concluída |
| 2 | Backend — scoring de risco + persistência | ✅ concluída |
| 3 | Backend — layout 3D | — |
| 4–6 | Frontend — cena, integração, polish | — |
| 7 | Demo estático (GitHub Pages) | — |

> O Portscape contacta a API pública do NVD (`services.nvd.nist.gov`) para obter
> CVEs. Envia **apenas** identificadores CPE de software (ex.
> `cpe:2.3:a:openbsd:openssh:9.6`) — nunca endereços IP, nomes de máquinas ou
> resultados de scan. Podes desligar isto com `portscape.nvd.enabled: false`.

## Requisitos

- Java 21
- Maven 3.9+
- nmap 7.9+
- Docker (para o PostgreSQL e para os testes de integração)

## Scan privilegiado

A configuração por defeito usa `-sS` (SYN scan) e `-O` (deteção de OS), que
exigem root. Sem privilégios o scan falha com `NMAP_PRIVILEGE` e uma mensagem a
explicar as opções.

**Opção A — sudoers (recomendada):**

```bash
sudo visudo -f /etc/sudoers.d/portscape-nmap
# <utilizador> ALL=(root) NOPASSWD: /opt/homebrew/Cellar/nmap/*/bin/nmap
```

e em `application.yml`:

```yaml
portscape:
  nmap:
    command: ["sudo", "-n", "/opt/homebrew/bin/nmap"]
```

**Opção B — sem privilégios:** remover `-sS` e `-O` de
`portscape.nmap.arguments` (fica `--open`). O scan funciona, mas sem
`osGuess`.

Não é recomendado pôr o setuid bit no nmap: um nmap setuid-root permite executar
scripts NSE como root, e o `brew upgrade` repõe as permissões na mesma.

## Scan em duas fases (bug do nmap em macOS)

Em macOS, correr `-sV` (deteção de versão) como root faz o nmap falhar a
vincular as sondas de versão (`NSOCK ERROR mksock_bind_addr ... Invalid
argument`) — todas as portas saem como `tcpwrapped`, mesmo as óbvias como um
SSH ou HTTP normais. Não depende de `-sS` vs. `-sT` nem de `-O`: acontece
sempre que `-sV` corre como root nesta plataforma.

Por isso o Portscape faz o scan em duas invocações:

1. **Descoberta** (privilegiada, `portscape.nmap.arguments`) — hosts, portas,
   OS. Sem `-sV`.
2. **Deteção de versão** (sem privilégios, fixo no código como `-sT -sV`) —
   só contra os hosts e portas que a fase 1 encontrou abertos.

A app junta os dois resultados (`ScanResultMerger`): portas e OS vêm sempre da
fase 1; serviço/produto/versão vêm da fase 2 quando disponíveis. Se a fase 2
falhar, o scan fica `DONE` na mesma, só sem versões — não é razão para reprovar
o scan todo. Ver `NmapCommandBuilder` para o detalhe.

## Correr

```bash
docker compose up -d          # PostgreSQL
cd backend
mvn test                      # unitários, segundos, sem Docker
mvn verify                    # + testes de integração (Testcontainers, precisa de Docker)
mvn spring-boot:run
```

O `compose.yaml` tem **só** a base de dados. O nmap corre nativamente no host e não
em container: em Docker Desktop no macOS, `--network host` é a VM LinuxKit e não o
macOS, e o scan reporta hosts que não existem na rede real — falha silenciosa, pior
que um erro.

O schema é gerido pelo Flyway (`backend/src/main/resources/db/migration`) e o
Hibernate corre em `ddl-auto: validate`, para as entidades e as migrações não
divergirem sem ninguém dar por isso.

## Deteção automática da rede

Se o `POST /api/scans` não indicar `target`, a app pergunta ao sistema
operativo qual é a interface da rota por defeito (a mesma que qualquer
aplicação normal usaria para sair para a internet) e deriva a subnet a partir
daí — sem enviar nenhum pacote. Isto evita escolher a interface errada numa
máquina com várias ativas (Wi-Fi + Ethernet, VPN), e mantém o `target` correto
mesmo que mudes de rede entre scans.

`portscape.nmap.default-target` no `application.yml` só entra em jogo se essa
deteção falhar (sem rota por defeito, ambiente isolado).

## Scoring de risco

O nmap diz o que está aberto; o Portscape diz o que isso significa. O score vai de
0 a 100 (satura no topo) e cada ponto tem uma razão associada, para o painel de
detalhes poder responder "porquê 78?" em vez de mostrar só o número.

| Regra | O que pontua |
|---|---|
| `OPEN_PORT` | cada porta aberta, com peso por número de porta — Telnet (23) e SMB (445) pesam muito, HTTPS (443) quase nada. **Não é o número de portas que faz o edifício ficar vermelho, são as portas erradas.** |
| `KNOWN_CVE` | CVEs reais do NVD para a versão detetada, ponderados pelo CVSS do pior. Uma falha crítica pesa mais que várias menores. |
| `UNKNOWN_HOST` | o dispositivo não existia no baseline — soma risco só por existir, independentemente das portas |
| `NEW_PORT` | portas que um host conhecido não tinha abertas no baseline |

Os pesos estão todos em `application.yml` sob `portscape.risk` — são um juízo
editorial, não uma constante do universo.

**CVEs.** Os CPEs que o nmap emite quase nunca batem certo com o dicionário do NIST
(o nmap diz `matt_johnston:dropbear_ssh_server`, o NVD conhece
`dropbear_ssh_project:dropbear_ssh`; para o nginx o nmap diz `igor_sysoev` e o NIST
diz `f5`). Por isso o cliente faz dois pedidos: resolve primeiro o nome canónico em
`/cpes/2.0` e só depois pede os CVEs em `/cves/2.0`. Um CPE **sem versão** é ignorado
de propósito — casaria com todos os CVEs alguma vez publicados para o produto, e
atribuí-los ao host seria inventar risco.

As respostas ficam em cache no Postgres (sem ela, o rate limit do NVD — 5 pedidos por
30s sem API key — dominava a duração do scan). Podes pôr uma key em
`PORTSCAPE_NVD_API_KEY` para subir para 50.

Se o NVD falhar, **o scan não falha**: termina `DONE` com `cveLookupDegraded: true` e
o score sai só das regras de portas. A flag existe para não se confundir "não há CVEs"
com "não foi possível verificar" — ler o segundo como o primeiro faria uma rede
inteira parecer segura.

## Baseline e deteção de mudanças

Cada scan é comparado com uma referência, resolvida por esta ordem:

1. o scan **fixado** para aquela rede, se existir (`POST /api/baselines`);
2. senão, o **último scan concluído** da mesma rede;
3. senão, nenhum — no primeiro scan de uma rede não há termo de comparação, e marcar
   todos os hosts como novos seria ruído, não sinal. Os hosts vêm `UNKNOWN`, que não é
   o mesmo que `UNCHANGED`.

Um host é `CHANGED` se as portas abertas ou o palpite de OS mudarem. A versão do
serviço não conta: o nmap acerta-a de forma intermitente e trataria isso como mudança
encheria a cidade de falsos alarmes.

**O score é gravado, o diff é calculado na leitura.** O score depende dos CVEs que o
NVD conhecia no momento do scan — recalculá-lo semanas depois daria outro número e o
histórico deixava de ser comparável. O diff depende do baseline *atual*, e gravá-lo
deixaria as flags a mentir assim que alguém fixasse outro baseline.

## API

| Método | Rota | Resposta |
|---|---|---|
| `POST` | `/api/scans` | `202` + `Location` — arranca um scan (body `{"target":"192.168.1.0/24"}`, opcional — sem ele, deteta a rede local automaticamente) |
| `GET` | `/api/scans/{id}` | estado do scan e, quando `DONE`, os hosts com risco e flags de mudança |
| `GET` | `/api/scans/{id}/diff` | comparação completa com o baseline, incluindo os hosts que desapareceram |
| `GET` | `/api/scans` | histórico de scans (sumários) |
| `POST` | `/api/baselines` | fixa um scan como referência (body `{"scanId":"..."}`) — a rede vem do próprio scan |
| `DELETE` | `/api/baselines?target=192.168.1.0/24` | volta ao baseline implícito |
| `GET` | `/api/baselines` | baselines fixados |

O `target` vai em query e não no caminho porque contém uma barra (`192.168.1.0/24`),
e uma barra codificada num path variable é rejeitada pelo Tomcat por defeito.

Scans são assíncronos — um `/24` demora minutos. O `POST` devolve logo e o
cliente faz polling ao `GET`.

```bash
curl -XPOST localhost:8080/api/scans \
  -H 'Content-Type: application/json' -d '{"target":"192.168.1.0/24"}'

curl localhost:8080/api/scans/<id> | jq
```

```json
{
  "id": "e910311a-…", "target": "192.168.1.0/24", "status": "DONE",
  "startedAt": "2026-08-28T15:39:31Z", "finishedAt": "2026-08-28T15:41:43Z",
  "durationMs": 132000, "hostsUp": 1,
  "baselineScanId": "dd1a1521-…", "cveLookupDegraded": false,
  "hosts": [
    { "ip": "192.168.1.254", "hostname": "router.lan",
      "osGuess": "Linux 5.4 - 5.15", "osAccuracy": 94, "portCount": 2,
      "riskScore": 100,
      "riskReasons": [
        {"code": "OPEN_PORT", "description": "Porta 23/tcp aberta (telnet)", "points": 35},
        {"code": "KNOWN_CVE",
         "description": "CVE-2020-36254 (CVSS 8.1) em Dropbear sshd 2017.75 na porta 22 -- e mais 4 CVE(s) conhecido(s)",
         "points": 35}
      ],
      "change": "UNCHANGED", "isNew": false, "isChanged": false,
      "ports": [
        {"number": 22, "protocol": "tcp", "state": "open",
         "service": "ssh", "product": "Dropbear sshd", "version": "2017.75",
         "cpes": ["cpe:/a:matt_johnston:dropbear_ssh_server:2017.75"]},
        {"number": 23, "protocol": "tcp", "state": "open",
         "service": "telnet", "product": "BusyBox telnetd", "version": null, "cpes": []}
      ] }
  ]
}
```

Quando o scan falha, o estado é `FAILED` e a resposta traz
`error: {code, message}` — por exemplo `NMAP_PRIVILEGE`,
`NMAP_NOT_FOUND`, `NMAP_XML_PARSE_FAILED`.

## Configuração

Tudo em `backend/src/main/resources/application.yml`:

- `portscape.nmap` — `command`, `default-target`, `arguments`, `timeout`, `host-timeout`
- `portscape.nvd` — `enabled`, `base-url`, `api-key`, `timeout`,
  `min-request-interval`, `cache-ttl`, `empty-cache-ttl`
- `portscape.risk` — `port-weights` e os pesos de cada regra
- Base de dados por variáveis de ambiente: `POSTGRES_URL`, `POSTGRES_USER`,
  `POSTGRES_PASSWORD` (com defaults de desenvolvimento)

## Notas

O `empty-cache-ttl` é mais curto que o `cache-ttl` de propósito: "sem CVEs" tanto sai
de um produto sem vulnerabilidades como de um nome que o NVD não reconheceu, e guardar
o segundo caso durante uma semana esconderia o problema durante uma semana.
