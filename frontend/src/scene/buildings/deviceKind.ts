/**
 * Que tipo de coisa e uma maquina, deduzido do fabricante do seu MAC.
 *
 * <p>O nmap resolve o MAC por ARP e traduz o prefixo (OUI) no nome do fabricante --
 * "Espressif Inc.", "Ubiquiti Networks". E o unico campo que diz <i>o que</i> a maquina
 * e; o IP so diz onde esta e as portas so dizem o que ela expoe.
 *
 * <p>A classificacao vive no frontend, e nao no backend, pela mesma razao que a paleta
 * de cores: e uma decisao de apresentacao. Mudar a forma de um router nao tem que
 * obrigar a mexer no dominio.
 *
 * <p><b>O tipo escolhe a forma, nunca a altura.</b> A altura continua a sair do numero
 * de portas abertas, que e informacao. Um router com dez portas abertas tem de se ver
 * que tem dez portas abertas.
 */
export type DeviceKind = 'GATEWAY' | 'IOT' | 'GENERIC';

/**
 * Palavras que aparecem no nome do fabricante. Deliberadamente curta: apanhar os casos
 * frequentes numa rede domestica e cair em GENERIC para tudo o resto e melhor do que
 * uma tabela enorme que fica desactualizada e da falsos positivos.
 */
const GATEWAY_VENDORS = [
  'ubiquiti', 'mikrotik', 'tp-link', 'tplink', 'netgear', 'zyxel', 'd-link', 'dlink',
  'cisco', 'aruba', 'ruckus', 'technicolor', 'sagemcom', 'arris', 'avm', 'fritz',
  'huawei technolog', 'zte', 'askey', 'sercomm', 'cambium', 'juniper',
];

const IOT_VENDORS = [
  'espressif', 'tuya', 'shelly', 'sonoff', 'itead', 'raspberry', 'sonos', 'roku',
  'signify', 'philips lighting', 'nest labs', 'amazon technolog', 'tp-link tech',
  'broadlink', 'xiaomi communi', 'yeelight', 'wyze', 'ring llc', 'ecobee',
];

/**
 * @param vendor o campo do host, que vem a null sempre que o nmap nao resolveu o MAC
 *               -- a propria maquina do scan, alvos fora do segmento local, scans sem
 *               privilegios. Nesses casos a forma volta a sair so do numero de portas.
 */
export function deviceKindOf(vendor?: string | null): DeviceKind {
  if (!vendor) { return 'GENERIC'; }
  const name = vendor.toLowerCase();

  // Os routers ganham a IoT quando ha ambiguidade: a TP-Link faz as duas coisas, e
  // enganar-se a marcar um router como lampada e pior do que o contrario.
  if (GATEWAY_VENDORS.some(v => name.includes(v))) { return 'GATEWAY'; }
  if (IOT_VENDORS.some(v => name.includes(v))) { return 'IOT'; }
  return 'GENERIC';
}
