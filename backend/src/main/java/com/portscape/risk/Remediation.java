package com.portscape.risk;

/**
 * Uma accao concreta e o que ela vale.
 *
 * <p>O score ja era auditavel -- cada ponto tem uma razao -- mas nao era accionavel: o
 * painel dizia "78/100, e aqui estao as sete razoes" sem dizer por onde comecar. Uma
 * lista de razoes ordenada por pontos parece essa resposta e nao e: fechar a porta que
 * vale 35 pode valer 35, ou pode valer zero, consoante o que mais la esteja.
 *
 * <p><b>Porque e que ha dois numeros.</b> O {@link RiskScore#score()} satura em 100 e a
 * soma das razoes nao. Um host com razoes a somar 135 mostra 100; fechar-lhe uma porta
 * de 30 leva-o a 105 e continua a mostrar 100. Mostrar so o score saturado dava
 * "Fechar 445/tcp: 100 -> 100", que se le como avaria. Por isso ordena-se por
 * {@code pointsRemoved} (nao saturado, e o que mede o efeito real) e mostra-se o
 * {@code scoreAfter} (saturado, e o que a cidade vai usar) -- e quando os dois
 * discordam, isso e a mensagem verdadeira: este host nao se arranja com uma accao.
 *
 * @param code          a regra que deixa de disparar, para o frontend agrupar
 * @param action        o que fazer, ja com os valores concretos
 * @param pointsRemoved quanto sai da soma das razoes -- sem tecto
 * @param scoreAfter    o score que o host passaria a ter, esse sim saturado
 */
public record Remediation(String code, String action, int pointsRemoved, int scoreAfter) {
}
