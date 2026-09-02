import { useCallback, useEffect, useRef, useState } from 'react';
import { DEMO_MODE } from '../demoMode';
import { demoScan } from '../mock/demoScan';
import { ApiError, getScan, listScans, startScan as requestScan } from './client';
import type { Scan } from './types';

/** Intervalo entre sondagens ao backend enquanto um scan decorre. */
const POLL_INTERVAL_MS = 1500;

/**
 * Atraso entre o scan terminar e a cidade aparecer. Serve para a barra de progresso
 * se ver a chegar aos 100% em vez de saltar do 90 para o ecra seguinte.
 */
const REVEAL_DELAY_MS = 600;

export interface ScanJob {
  /** O scan que a cidade esta a mostrar. Comeca no exemplo e nunca fica vazio. */
  scanData: Scan;
  /** Ainda nao se sabe se ha um scan guardado -- nao desenhar cidade nenhuma. */
  isBooting: boolean;
  isScanning: boolean;
  /** Linha de estado do scan em curso, ja formatada para o painel. */
  scanStatus: string;
  progress: number;
  scanError: string | null;
  startScan: (target?: string) => Promise<void>;
  loadScan: (id: string) => Promise<void>;
  /** O scan foi apagado no backend: se e o que esta no ecra, larga-lo. */
  forgetScan: (id: string) => void;
}

export interface ScanJobCallbacks {
  /** Uma cidade nova acabou de aparecer no ecra. */
  onScanShown?: () => void;
  /** A cidade que estava no ecra foi apagada e voltou-se ao exemplo. */
  onScanForgotten?: () => void;
}

/**
 * Toda a conversa com o backend sobre scans: arranque, sondagem, progresso e erros.
 *
 * <p>Vive fora do componente pelo mesmo motivo que o {@link buildCityGrid}: e aqui que
 * estao as unicas decisoes do frontend que ja produziram bugs -- a barra de progresso
 * inventada que ignorava o campo real do backend, a sondagem que continuava para
 * sempre depois de um scan ser apagado, e a resposta atrasada que pintava por cima do
 * scan que o utilizador tinha aberto entretanto. Dentro do App, so se testava montando
 * a cena 3D inteira; aqui testa-se com temporizadores falsos e um cliente mockado.
 *
 * <p>O que e interface -- que paineis estao abertos, que host esta seleccionado -- fica
 * de fora de proposito, e chega ca por callbacks. O hook decide *quando* a cidade muda;
 * quem manda no ecra e o componente.
 */
export function useScanJob(callbacks: ScanJobCallbacks = {}): ScanJob {
  const [scanData, setScanData] = useState<Scan>(demoScan);
  const [isBooting, setIsBooting] = useState(true);
  const [isScanning, setIsScanning] = useState(false);
  const [scanStatus, setScanStatus] = useState<string>('');
  // O progresso vem do backend. Ja foi uma curva inventada que assintotava nos 95%,
  // enquanto o campo `progress` real chegava em cada sondagem e era deitado fora.
  const [progress, setProgress] = useState<number>(0);
  const [scanError, setScanError] = useState<string | null>(null);

  // Os callbacks sao lidos por dentro do intervalo, que so e criado uma vez. Guardados
  // numa ref, um componente que os redefina a cada render nao deixa o intervalo preso
  // a versao com que arrancou.
  const handlers = useRef(callbacks);
  handlers.current = callbacks;

  // Sondagem e temporizadores em curso, para poderem ser cancelados no desmonte.
  const poll = useRef<ReturnType<typeof setInterval> | null>(null);
  const reveal = useRef<ReturnType<typeof setTimeout> | null>(null);
  // Cada coisa que possa vir a pintar o ecra -- um scan carregado do historico ou um
  // scan novo a decorrer -- leva um numero. So o mais recente escreve. Sem isto,
  // clicar em dois scans seguidos deixava a resposta mais lenta sobrepor-se, e um scan
  // a decorrer roubava o ecra a um scan antigo que o utilizador tivesse aberto no meio.
  const loadSeq = useRef(0);

  // O scan que esta no ecra, legivel de dentro de um callback sem o prender a um
  // render. Ver o forgetScan.
  const shown = useRef(scanData);
  shown.current = scanData;

  const stopPolling = useCallback(() => {
    if (poll.current) {
      clearInterval(poll.current);
      poll.current = null;
    }
    if (reveal.current) {
      clearTimeout(reveal.current);
      reveal.current = null;
    }
  }, []);

  useEffect(() => {
    if (DEMO_MODE) { setIsBooting(false); return; }
    listScans()
      .then(scans => (scans?.length ? getScan(scans[0].id) : null))
      .then(latest => { if (latest) setScanData(latest); })
      // Sem backend fica-se no cenario de exemplo, que e o comportamento util aqui.
      .catch(() => {})
      .finally(() => setIsBooting(false));
  }, []);

  // Desmontar tem de matar a sondagem: sem isto ficava um setInterval a bater no
  // backend depois de a app ja nao estar la.
  useEffect(() => stopPolling, [stopPolling]);

  const loadScan = useCallback(async (id: string) => {
    const seq = ++loadSeq.current;
    try {
      const fullScan = await getScan(id);
      if (seq !== loadSeq.current) { return; }
      setScanData(fullScan);
      setScanError(null);
      handlers.current.onScanShown?.();
    } catch (e) {
      if (seq === loadSeq.current) {
        setScanError(e instanceof ApiError ? e.message : 'Could not load the scan.');
      }
    }
  }, []);

  const startScan = useCallback(async (target?: string) => {
    if (DEMO_MODE) {
      setScanError('This is a static demo running on sample data — clone the repo and run the backend to scan a real network.');
      return;
    }

    stopPolling();
    const seq = ++loadSeq.current;
    setIsScanning(true);
    setScanError(null);
    setScanStatus('INITIALIZING SCAN...');
    setProgress(0);

    let started;
    try {
      started = await requestScan(target || undefined);
    } catch (e) {
      // O backend explica-se: alvo fora de uma rede privada, fila cheia, nmap sem
      // permissoes. Mostrar essa mensagem em vez de um erro generico e a diferenca
      // entre o utilizador perceber o que fez e ficar a adivinhar.
      setScanError(e instanceof ApiError ? e.message : 'Could not start the scan.');
      setScanStatus('');
      setIsScanning(false);
      return;
    }

    poll.current = setInterval(async () => {
      try {
        const current = await getScan(started.id);
        // O utilizador abriu outro scan entretanto: deixa-lo ver o que escolheu.
        if (seq !== loadSeq.current) { stopPolling(); return; }
        setScanStatus(`SCAN STATUS: ${current.status}`);
        setProgress(current.progress ?? 0);

        if (current.status !== 'DONE' && current.status !== 'FAILED') { return; }
        stopPolling();

        if (current.status === 'FAILED') {
          setScanError(current.error?.message || 'The scan failed.');
          setIsScanning(false);
          return;
        }

        setProgress(100);
        // Pequeno atraso para a barra se ver a chegar aos 100%.
        reveal.current = setTimeout(() => {
          setIsScanning(false);
          if (seq !== loadSeq.current) { return; }
          setScanData(current);
          handlers.current.onScanShown?.();
        }, REVEAL_DELAY_MS);
      } catch (e) {
        // Um scan apagado a meio, ou o backend em baixo: parar em vez de sondar
        // para sempre, que era o que acontecia antes.
        stopPolling();
        setScanError(e instanceof ApiError ? e.message : 'Lost contact with the scan.');
        setIsScanning(false);
      }
    }, POLL_INTERVAL_MS);
  }, [stopPolling]);

  const forgetScan = useCallback((id: string) => {
    // A cidade estava a mostrar este scan: sem isto ficava um fantasma no ecra, e
    // voltar a clicar nele dava 404.
    if (shown.current.id !== id) { return; }
    loadSeq.current++;
    stopPolling();
    setScanData(demoScan);
    handlers.current.onScanForgotten?.();
  }, [stopPolling]);

  return {
    scanData, isBooting, isScanning, scanStatus, progress, scanError,
    startScan, loadScan, forgetScan,
  };
}
