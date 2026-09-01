import { describe, it, expect, vi, afterEach } from 'vitest';
import { startScan, getScan, listScans, ApiError } from './client';

/** Uma resposta do fetch, com o corpo que o teste quiser. */
function respond(status: number, body: unknown, ok = status < 400) {
  return {
    ok,
    status,
    json: async () => {
      if (typeof body === 'string') { throw new SyntaxError('not json'); }
      return body;
    },
  } as Response;
}

const stubFetch = (res: Response) => {
  const spy = vi.fn().mockResolvedValue(res);
  vi.stubGlobal('fetch', spy);
  return spy;
};

afterEach(() => vi.unstubAllGlobals());

describe('erros da API', () => {

  // O corpo real que o backend devolve para um alvo fora de uma rede privada.
  const RFC7807 = {
    type: 'about:blank',
    title: 'Target invalido',
    status: 400,
    detail: "Target recusado: '8.8.8.8/24' nao pertence a uma rede privada.",
    code: 'INVALID_TARGET',
  };

  it('leva ate a interface a explicacao que o backend deu', async () => {
    stubFetch(respond(400, RFC7807));

    // Isto e a regressao que interessa: antes lancava-se um Error generico e o
    // utilizador que escrevia um IP publico ficava sem saber porque foi recusado.
    await expect(startScan('8.8.8.8/24')).rejects.toMatchObject({
      code: 'INVALID_TARGET',
      status: 400,
      message: RFC7807.detail,
    });
  });

  it('o erro e um ApiError, para a interface o poder distinguir', async () => {
    stubFetch(respond(503, { detail: 'Fila cheia', code: 'SCAN_QUEUE_FULL' }));

    await expect(startScan()).rejects.toBeInstanceOf(ApiError);
  });

  it('uma resposta sem corpo JSON nao rebenta o tratamento do erro', async () => {
    // Um 502 do proxy, ou o backend em baixo: nao vem RFC 7807 nenhum.
    stubFetch(respond(502, 'Bad Gateway'));

    const erro = await getScan('abc').catch(e => e);
    expect(erro).toBeInstanceOf(ApiError);
    expect(erro.code).toBeNull();
    expect(erro.status).toBe(502);
    expect(erro.message).toBeTruthy();
  });

  it('cada operacao tem a sua mensagem de recurso', async () => {
    stubFetch(respond(500, 'boom'));
    const doScan = await getScan('abc').catch(e => e.message);
    stubFetch(respond(500, 'boom'));
    const doHistorico = await listScans().catch(e => e.message);

    expect(doScan).not.toEqual(doHistorico);
  });
});

describe('chamadas bem sucedidas', () => {

  it('devolve o corpo ja descodificado', async () => {
    stubFetch(respond(200, { id: 'abc', status: 'DONE', progress: 100 }));

    await expect(getScan('abc')).resolves.toMatchObject({ id: 'abc', progress: 100 });
  });

  it('so envia o target quando ha um: sem ele o backend deteta a rede', async () => {
    const spy = stubFetch(respond(202, { id: 'abc' }));
    await startScan();

    expect(JSON.parse(spy.mock.calls[0][1].body)).toEqual({});
  });

  it('envia o target quando o utilizador escreveu um', async () => {
    const spy = stubFetch(respond(202, { id: 'abc' }));
    await startScan('192.168.1.0/24');

    expect(JSON.parse(spy.mock.calls[0][1].body)).toEqual({ target: '192.168.1.0/24' });
  });
});
