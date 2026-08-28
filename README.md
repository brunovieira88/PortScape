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
| 2 | Backend — scoring de risco + persistência | — |
| 3 | Backend — layout 3D | — |
| 4–6 | Frontend — cena, integração, polish | — |
| 7 | Demo estático (GitHub Pages) | — |

## Requisitos

- Java 21
- Maven 3.9+
- nmap 7.9+

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
cd backend
mvn test
mvn spring-boot:run
```

## Deteção automática da rede

Se o `POST /api/scans` não indicar `target`, a app pergunta ao sistema
operativo qual é a interface da rota por defeito (a mesma que qualquer
aplicação normal usaria para sair para a internet) e deriva a subnet a partir
daí — sem enviar nenhum pacote. Isto evita escolher a interface errada numa
máquina com várias ativas (Wi-Fi + Ethernet, VPN), e mantém o `target` correto
mesmo que mudes de rede entre scans.

`portscape.nmap.default-target` no `application.yml` só entra em jogo se essa
deteção falhar (sem rota por defeito, ambiente isolado).

## API

| Método | Rota | Resposta |
|---|---|---|
| `POST` | `/api/scans` | `202` + `Location` — arranca um scan (body `{"target":"192.168.1.0/24"}`, opcional — sem ele, deteta a rede local automaticamente) |
| `GET` | `/api/scans/{id}` | estado do scan e, quando `DONE`, os hosts |
| `GET` | `/api/scans` | histórico de scans (sumários) |

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
  "hosts": [
    { "ip": "192.168.1.1", "hostname": "router.lan",
      "osGuess": "Linux 5.4 - 5.15", "osAccuracy": 94, "portCount": 2,
      "ports": [
        {"number": 23, "protocol": "tcp", "state": "open",
         "service": "telnet", "product": "BusyBox telnetd", "version": null},
        {"number": 80, "protocol": "tcp", "state": "open",
         "service": "http", "product": "lighttpd", "version": "1.4.59"}
      ] }
  ]
}
```

Quando o scan falha, o estado é `FAILED` e a resposta traz
`error: {code, message}` — por exemplo `NMAP_PRIVILEGE`,
`NMAP_NOT_FOUND`, `NMAP_XML_PARSE_FAILED`.

## Configuração

Tudo em `backend/src/main/resources/application.yml`, sob `portscape.nmap`:
`command`, `default-target`, `arguments`, `timeout`, `host-timeout`.

## Notas

Os scans desta fase vivem em memória e perdem-se ao reiniciar. A fase 2 traz
PostgreSQL, histórico e comparação com baseline.
