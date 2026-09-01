-- Identidade fisica do host.
--
-- O IP e um aluguer, nao uma identidade: numa rede com DHCP o mesmo dispositivo muda
-- de endereco entre scans, e comparar por IP fazia disso "um host desapareceu e nasceu
-- outro". O nmap ja resolvia o MAC por ARP e o parser deitava-o fora.
--
-- Anulavel de proposito: nao ha MAC para a propria maquina que corre o scan, para
-- alvos fora do segmento local, nem em scans sem privilegios. Os scans ja gravados
-- ficam com NULL e continuam a ser comparados por IP, que e o melhor disponivel.
ALTER TABLE host ADD COLUMN mac    VARCHAR(17);
ALTER TABLE host ADD COLUMN vendor VARCHAR(255);

-- A procura de um dispositivo pela sua identidade fisica atravessa scans.
CREATE INDEX idx_host_mac ON host (mac) WHERE mac IS NOT NULL;
