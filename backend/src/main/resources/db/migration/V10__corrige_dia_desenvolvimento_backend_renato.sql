-- Corrige o dia da turma noturna de Desenvolvimento Back-end do Renato.
-- Mantem o ID da turma para preservar distribuicoes de nota que ja possam
-- referenciar este registro.
UPDATE disciplinas
SET dia_semana = 'TERÇA-FEIRA'
WHERE id = '6b53b098-259e-56d6-8536-3de8799a9c81'
  AND codigo = '14338'
  AND nome = 'DESENVOLVIMENTO BACK-END'
  AND turno = 'NOITE'
  AND dia_semana = 'QUARTA-FEIRA'
  AND professor = 'Renato Alves Pereira';
