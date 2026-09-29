-- Presenca lancada a mao pelo admin.
--
-- Veio do 2o dia da WECTI 2026 (29/09): aluno inscrito, presente na
-- palestra, que nao conseguiu ler o QR de entrada, o de saida ou os dois
-- - camera do celular com defeito, sinal fraco, ou conta travada na hora
-- da leitura. Sem check-in ele nao pontua, nao tira certificado e ainda
-- aparece como no-show, por uma palestra que assistiu inteira.
--
-- A compensacao registra o FATO que faltou registrar (ele esteve la), e
-- nao os pontos: pontuacao, certificado, teto de 2000 e saida do no-show
-- saem todos do check-in e passam a valer sozinhos. Lancar os pontos
-- direto resolveria so a pontuacao, deixaria o certificado negado e
-- furaria o teto (ver PontuacaoExtra, que fica FORA do teto de propósito
-- por ser premiacao de gincana).
--
-- As duas colunas sao a trilha de auditoria: NULAS quando o check-in veio
-- do QR code, preenchidas quando um admin afirmou a presenca. Sem isso
-- nao haveria como distinguir, depois, presenca comprovada de presenca
-- declarada - e e informacao que alguem vai pedir na hora de conferir a
-- lista final.
-- Em statements separados, como as migrations anteriores: se uma parte
-- falhar no deploy, o erro aponta a linha exata em vez de um ALTER
-- composto inteiro.
--
-- VARCHAR(36) igual a usuarios.id, e a coluna herda o
-- utf8mb4/utf8mb4_unicode_ci da tabela - charset diferente entre as duas
-- pontas faz o MySQL recusar a foreign key.
ALTER TABLE checkins ADD COLUMN registrado_por_id VARCHAR(36) NULL;
ALTER TABLE checkins ADD COLUMN justificativa VARCHAR(200) NULL;
ALTER TABLE checkins ADD CONSTRAINT fk_checkins_registrado_por
    FOREIGN KEY (registrado_por_id) REFERENCES usuarios(id);
