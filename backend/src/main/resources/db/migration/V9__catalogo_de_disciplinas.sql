-- Catalogo de disciplinas: as turmas em que o aluno pode converter a
-- pontuacao do WECTI em nota (ver DistribuicaoPontuacaoService).
--
-- Importado da planilha "Professores-disciplinas.xlsx" entregue pela
-- coordenacao em outubro de 2026, com 131 turmas. Uma turma e a
-- combinacao (codigo, turno, dia, professor): o mesmo codigo aparece
-- varias vezes porque a mesma disciplina tem turmas diferentes - e e a
-- turma, nao a disciplina, que o aluno escolhe, ja que a nota vai para um
-- professor especifico.
--
-- Os ids sao UUIDv5 derivados dessa chave, e nao aleatorios: se a
-- planilha for reimportada, as turmas que continuam existindo mantem o
-- mesmo id e as notas ja distribuidas continuam apontando para elas.
--
-- CODIGO, DISCIPLINA e PROFESSOR estao exatamente como na planilha.
-- Apenas o DIA foi uniformizado (5 linhas): a planilha trazia
-- "SEGUNDA FERIA", "QUINTAFEIRA" e variantes sem hifen, o que quebraria
-- a mesma terca-feira em tres grupos diferentes num filtro por dia.
--
-- Catalogo novo? Nova migration. Reexecutar esta nao atualiza nada.
CREATE TABLE disciplinas (
    id VARCHAR(36) PRIMARY KEY,
    codigo VARCHAR(10) NOT NULL,
    nome VARCHAR(150) NOT NULL,
    -- MANHA ou NOITE (enum TurnoDisciplina, validado no service)
    turno VARCHAR(10) NOT NULL,
    dia_semana VARCHAR(20) NOT NULL,
    professor VARCHAR(150) NOT NULL,
    CONSTRAINT uk_disciplinas_turma UNIQUE (codigo, turno, dia_semana, professor)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO disciplinas (id, codigo, nome, turno, dia_semana, professor) VALUES
  ('47ecdf20-bfbb-579d-9b12-71c9d9322995', '10546', 'ANÁLISE E PROJETO DE SISTEMAS', 'MANHA', 'SEGUNDA-FEIRA', 'Alexandre Carrocine'),
  ('69ef83ef-7830-59c3-b276-61175f68d1cc', '8281', 'ENGENHARIA DE SOFTWARE', 'MANHA', 'QUARTA-FEIRA', 'Alexandre Carrocine'),
  ('362d3da1-ed6d-5726-b4bf-58453474a3ba', '14372', 'GESTÃO ÁGIL', 'MANHA', 'TERÇA-FEIRA', 'Alexandre Carrocine'),
  ('bbfbd8ce-8cf8-5c2d-98bc-12bc1e131624', '10546', 'ANÁLISE E PROJETO DE SISTEMAS', 'NOITE', 'TERÇA-FEIRA', 'Alexandre Carrocine'),
  ('8454738a-60a2-548a-a8e1-e656b46d1efd', '10546', 'ANÁLISE E PROJETO DE SISTEMAS', 'NOITE', 'QUARTA-FEIRA', 'Alexandre Carrocine'),
  ('dc61d74d-aa0b-5162-9925-32f8c570e315', '8281', 'ENGENHARIA DE SOFTWARE', 'NOITE', 'SEGUNDA-FEIRA', 'Alexandre Carrocine'),
  ('875aba21-330a-598c-8051-585d32f8d36e', '14372', 'GESTÃO ÁGIL', 'NOITE', 'QUINTA-FEIRA', 'Alexandre Carrocine'),
  ('b02833e5-8424-5510-aa46-6f4b88876de1', '14364', 'DESIGN PROFISSIONAL', 'MANHA', 'QUARTA-FEIRA', 'Andrea Martins Cristóvão'),
  ('98c9d2ef-1c58-5ef4-9f81-332a5f5782ba', '14364', 'DESIGN PROFISSIONAL', 'MANHA', 'TERÇA-FEIRA', 'Andrea Martins Cristóvão'),
  ('b4a98e3c-ed2b-5312-bb2c-ac98a4aa975e', '14364', 'DESIGN PROFISSIONAL', 'NOITE', 'SEXTA-FEIRA', 'Andrea Martins Cristóvão'),
  ('b2224c16-e29f-51eb-89f9-99b6f00b3f8f', '14364', 'DESIGN PROFISSIONAL', 'NOITE', 'TERÇA-FEIRA', 'Andrea Martins Cristóvão'),
  ('1bcffb76-40a3-5864-af7d-c44a4cdaeace', '14364', 'DESIGN PROFISSIONAL', 'NOITE', 'SEGUNDA-FEIRA', 'Andrea Martins Cristóvão'),
  ('0e7c6120-92c4-5d69-aada-a31bece1f3af', '14364', 'DESIGN PROFISSIONAL', 'NOITE', 'QUARTA-FEIRA', 'Andrea Martins Cristóvão'),
  ('4fcb9a92-d929-573a-ba96-54203192536b', '14364', 'DESIGN PROFISSIONAL', 'NOITE', 'SÁBADO', 'Andrea Martins Cristóvão'),
  ('8ebe55a5-5519-53f9-9e52-9eefa0701a21', '14364', 'DESIGN PROFISSIONAL', 'NOITE', 'QUINTA-FEIRA', 'Andrea Martins Cristóvão'),
  ('3b5cf2dd-a74f-5cce-bd99-2e317448dbbd', '10535', 'COMPUTABILIDADE E COMPLEXIDADE DE ALGORITMOS', 'MANHA', 'TERÇA-FEIRA', 'Angel Antônio Gonzalez Martinez'),
  ('ad677a4e-b620-5650-b559-c968e0cd1aea', '10536', 'LINGUAGENS FORMAIS E AUTÔMATOS', 'MANHA', 'QUINTA-FEIRA', 'Angel Antônio Gonzalez Martinez'),
  ('e5e8bca3-9717-5ba7-8f17-9447d90eb71a', '10535', 'COMPUTABILIDADE E COMPLEXIDADE DE ALGORITMOS', 'NOITE', 'QUARTA-FEIRA', 'Angel Antônio Gonzalez Martinez'),
  ('8a815702-320a-587d-9380-d38ef360eff0', '10536', 'LINGUAGENS FORMAIS E AUTÔMATOS', 'NOITE', 'TERÇA-FEIRA', 'Angel Antônio Gonzalez Martinez'),
  ('99089043-1b42-5782-b4e7-6fed86eb97b1', '10536', 'LINGUAGENS FORMAIS E AUTÔMATOS', 'NOITE', 'QUINTA-FEIRA', 'Angel Antônio Gonzalez Martinez'),
  ('da8ca912-eec9-5534-8182-bcd2284da96d', '12840', 'LABORATÓRIO DE BANCO DE DADOS AVANÇADO', 'MANHA', 'TERÇA-FEIRA', 'Angela Perez Barcellos'),
  ('d4f58696-ad92-5dbd-bf95-6b1fec413ad4', '12840', 'LABORATÓRIO DE BANCO DE DADOS AVANÇADO', 'MANHA', 'SEXTA-FEIRA', 'Angela Perez Barcellos'),
  ('8b3e1c3b-c584-5945-b3b5-edeb9845cb8f', '13157', 'TRABALHO DE GRADUAÇÃO INTERDISCIPLINAR I', 'MANHA', 'SEGUNDA-FEIRA', 'Angela Perez Barcellos'),
  ('eef1a614-f568-57ad-945a-2f01485a8e2d', '12840', 'LABORATÓRIO DE BANCO DE DADOS AVANÇADO', 'NOITE', 'SEGUNDA-FEIRA', 'Angela Perez Barcellos'),
  ('f9f66f04-a399-5421-b8e1-d9672ae57f38', '12840', 'LABORATÓRIO DE BANCO DE DADOS AVANÇADO', 'NOITE', 'TERÇA-FEIRA', 'Angela Perez Barcellos'),
  ('6169461f-d6a6-572c-927c-67cd9f4082cc', '12840', 'LABORATÓRIO DE BANCO DE DADOS AVANÇADO', 'NOITE', 'QUARTA-FEIRA', 'Angela Perez Barcellos'),
  ('e373c48d-54ff-5699-b212-375b1518fd37', '12840', 'LABORATÓRIO DE BANCO DE DADOS AVANÇADO', 'NOITE', 'QUINTA-FEIRA', 'Angela Perez Barcellos'),
  ('b3b6f831-09d9-58da-982f-a48e5a3ae2d5', '12840', 'LABORATÓRIO DE BANCO DE DADOS AVANÇADO', 'NOITE', 'SEXTA-FEIRA', 'Angela Perez Barcellos'),
  ('5f579be2-ee57-55a9-ae3b-ad9f1f4da568', '13157', 'TRABALHO DE GRADUAÇÃO INTERDISCIPLINAR I', 'NOITE', 'QUARTA-FEIRA', 'Angela Perez Barcellos'),
  ('4eaa3620-edcb-5c08-974e-cb48b906730c', '14362', 'ALGORITMOS E PENSAMENTO COMPUTACIONAL', 'NOITE', 'SEGUNDA-FEIRA', 'Carlos Eduardo Weber'),
  ('98ea4f16-304a-5006-9572-d19a5499f787', '14362', 'ALGORITMOS E PENSAMENTO COMPUTACIONAL', 'NOITE', 'TERÇA-FEIRA', 'Carlos Eduardo Weber'),
  ('75b34824-b980-5119-81ff-3cc0394e77a3', '14362', 'ALGORITMOS E PENSAMENTO COMPUTACIONAL', 'NOITE', 'QUINTA-FEIRA', 'Carlos Eduardo Weber'),
  ('e7bed323-460c-55a8-8589-80fbf7fff3c7', '14362', 'ALGORITMOS E PENSAMENTO COMPUTACIONAL', 'NOITE', 'SEXTA-FEIRA', 'Carlos Eduardo Weber'),
  ('025a2ef9-1709-5248-9adc-0d91fcc3b4ee', '12832', 'REDES DE COMPUTADORES', 'NOITE', 'SEGUNDA-FEIRA', 'Celso Cândido'),
  ('79e34348-f21f-5ba0-8a12-6587b66fccef', '12832', 'REDES DE COMPUTADORES', 'NOITE', 'TERÇA-FEIRA', 'Celso Cândido'),
  ('d9b7aace-f8c1-59c3-9d01-048acba85ac4', '12832', 'REDES DE COMPUTADORES', 'NOITE', 'QUARTA-FEIRA', 'Celso Cândido'),
  ('8bf082a9-a819-577f-b564-7cfd6d3288d9', '12832', 'REDES DE COMPUTADORES', 'NOITE', 'QUINTA-FEIRA', 'Celso Cândido'),
  ('e6bb34ab-ff76-56f9-b305-e67959400cf4', '12832', 'REDES DE COMPUTADORES', 'NOITE', 'SEXTA-FEIRA', 'Celso Cândido'),
  ('e6862be7-1350-569e-b288-e025e4694240', '10545', 'ESTRUTURAS DE DADOS II', 'MANHA', 'QUINTA-FEIRA', 'Cid Rodrigues de Andrade'),
  ('1de9c00d-15ce-56d9-a9bc-0ecc5f729e71', '7090', 'PARADIGMAS DE LINGUAGENS DE PROGRAMAÇÃO', 'MANHA', 'QUARTA-FEIRA', 'Cid Rodrigues de Andrade'),
  ('71b03836-56fc-5670-9092-9a754443954d', '14365', 'MODELAGEM DE BANCO DE DADOS', 'MANHA', 'SEGUNDA-FEIRA', 'Cid Rodrigues de Andrade'),
  ('27cd2a33-2573-521f-b372-7ce828e03b3d', '14363', 'DESENVOLVIMENTO FRONT-END PARA WEB', 'MANHA', 'SEXTA-FEIRA', 'Cid Rodrigues de Andrade'),
  ('06cf6b6a-3351-5297-97ae-c44b6ad9f0f4', '14365', 'MODELAGEM DE BANCO DE DADOS', 'NOITE', 'TERÇA-FEIRA', 'Cid Rodrigues de Andrade'),
  ('626fb3db-a237-5537-9b50-5e59bc842324', '14365', 'MODELAGEM DE BANCO DE DADOS', 'NOITE', 'QUARTA-FEIRA', 'Cid Rodrigues de Andrade'),
  ('1bc3f8d1-be81-578e-9a3c-99ba90b15fc3', '14365', 'MODELAGEM DE BANCO DE DADOS', 'NOITE', 'SEXTA-FEIRA', 'Cid Rodrigues de Andrade'),
  ('ebbf21e7-475d-553c-a5b4-99df0d5470dd', '14364', 'DESIGN PROFISSIONAL', 'NOITE', 'SEGUNDA-FEIRA', 'Cid Rodrigues de Andrade'),
  ('7b4edfcc-ad7e-55a1-96f0-09e7bb43103c', '14365', 'MODELAGEM DE BANCO DE DADOS', 'NOITE', 'QUINTA-FEIRA', 'Cid Rodrigues de Andrade'),
  ('03c0f9ee-8f78-5afa-b01c-c45dc5f4c119', '7865', 'CIÊNCIA DE DADOS E APRENDIZAGEM DE MÁQUINA', 'MANHA', 'TERÇA-FEIRA', 'Cláudia Rodrigues de Carvalho'),
  ('df476602-b485-567d-9697-52b186ee6632', '12832', 'REDES DE COMPUTADORES', 'MANHA', 'QUINTA-FEIRA', 'Cláudia Rodrigues de Carvalho'),
  ('fb899cb4-005f-5043-ae8d-351fbbf77171', '12101', 'TÓPICOS AVANÇADOS EM SISTEMAS DE INFORMAÇÃO II', 'MANHA', 'QUARTA-FEIRA', 'Cláudia Rodrigues de Carvalho'),
  ('cda9d267-d6b9-5c8e-a97e-f9051e9e3e5b', '7865', 'CIÊNCIA DE DADOS E APRENDIZAGEM DE MÁQUINA', 'NOITE', 'SEXTA-FEIRA', 'Cláudia Rodrigues de Carvalho'),
  ('f59ca43a-cbbb-5b13-80ec-49391cf159d7', '14365', 'MODELAGEM DE BANCO DE DADOS', 'MANHA', 'QUINTA-FEIRA', 'Clóvis José Ramos Ferraro'),
  ('3efba622-7146-5e98-bd2b-712fd5d9e5b0', '14365', 'MODELAGEM DE BANCO DE DADOS', 'MANHA', 'SEXTA-FEIRA', 'Clóvis José Ramos Ferraro'),
  ('1bdb466f-be40-5234-b6cb-c930c585cae2', '14365', 'MODELAGEM DE BANCO DE DADOS', 'NOITE', 'SEGUNDA-FEIRA', 'Clóvis José Ramos Ferraro'),
  ('a9f09ff4-d154-563d-8ce9-2dc4132df14f', '14365', 'MODELAGEM DE BANCO DE DADOS', 'NOITE', 'QUINTA-FEIRA', 'Clóvis José Ramos Ferraro'),
  ('9761fb44-ffde-505d-8c23-68e03356ef58', '14365', 'MODELAGEM DE BANCO DE DADOS', 'NOITE', 'TERÇA-FEIRA', 'Clóvis José Ramos Ferraro'),
  ('fbbc8a5a-a665-581a-aca1-3178e248afb6', '14365', 'MODELAGEM DE BANCO DE DADOS', 'NOITE', 'SEXTA-FEIRA', 'Clóvis José Ramos Ferraro'),
  ('23b8272d-5aa1-5378-bb63-035aefcd5d64', '14365', 'MODELAGEM DE BANCO DE DADOS', 'NOITE', 'QUARTA-FEIRA', 'Clóvis José Ramos Ferraro'),
  ('400af52f-1746-551b-982f-bb281a74dae0', '14365', 'MODELAGEM DE BANCO DE DADOS', 'MANHA', 'TERÇA-FEIRA', 'Diego Marques de Carvalho'),
  ('8c242e56-3c33-56e2-9dc3-dc8752d83d34', '14363', 'DESENVOLVIMENTO FRONT-END PARA WEB', 'MANHA', 'QUARTA-FEIRA', 'Diego Marques de Carvalho'),
  ('263ceff0-67b8-5c1c-9906-16003059ee5b', '14365', 'MODELAGEM DE BANCO DE DADOS', 'NOITE', 'QUINTA-FEIRA', 'Diego Marques de Carvalho'),
  ('d4da37fb-c940-59cb-8b38-25a577ecf12e', '14365', 'MODELAGEM DE BANCO DE DADOS', 'NOITE', 'SÁBADO', 'Diego Marques de Carvalho'),
  ('07c8643e-2cfe-5b2e-8b09-e2dafe1b0230', '14365', 'MODELAGEM DE BANCO DE DADOS', 'NOITE', 'TERÇA-FEIRA', 'Diego Marques de Carvalho'),
  ('50d6f40f-1aeb-5e81-aed6-6bd693b6d8fe', '14364', 'DESIGN PROFISSIONAL', 'NOITE', 'SEXTA-FEIRA', 'Fabiola Paes Nogueira'),
  ('6c1ce95d-50ad-597d-bc7c-657c149ac186', '14364', 'DESIGN PROFISSIONAL', 'NOITE', 'QUARTA-FEIRA', 'Fabiola Paes Nogueira'),
  ('045e734d-7db6-53c9-bf1a-a7af682e08f2', '14364', 'DESIGN PROFISSIONAL', 'NOITE', 'QUINTA-FEIRA', 'Fabiola Paes Nogueira'),
  ('3408d38b-55ae-5aa1-836b-5de14eb8d6a3', '14364', 'DESIGN PROFISSIONAL', 'NOITE', 'TERÇA-FEIRA', 'Fabiola Paes Nogueira'),
  ('761b292e-a89a-5f61-8abf-7ecebc541839', '14362', 'ALGORITMOS E PENSAMENTO COMPUTACIONAL', 'MANHA', 'SEGUNDA-FEIRA', 'Francisco de Assis Cavallaro'),
  ('44a26f14-b635-5564-bf69-a69089ffea08', '14362', 'ALGORITMOS E PENSAMENTO COMPUTACIONAL', 'MANHA', 'TERÇA-FEIRA', 'Francisco de Assis Cavallaro'),
  ('cb0e972c-1a18-5430-9de5-710bf3574aaa', '14362', 'ALGORITMOS E PENSAMENTO COMPUTACIONAL', 'MANHA', 'QUARTA-FEIRA', 'Francisco de Assis Cavallaro'),
  ('c81686af-a40f-5f1d-a48b-dacf4f3ee60b', '14362', 'ALGORITMOS E PENSAMENTO COMPUTACIONAL', 'MANHA', 'QUINTA-FEIRA', 'Francisco de Assis Cavallaro'),
  ('a5214065-5747-5fc5-bbc1-13f89e0b33d5', '14362', 'ALGORITMOS E PENSAMENTO COMPUTACIONAL', 'NOITE', 'QUARTA-FEIRA', 'Francisco de Assis Cavallaro'),
  ('7608ed7a-2f49-58c4-89f8-992e112d8492', '14362', 'ALGORITMOS E PENSAMENTO COMPUTACIONAL', 'NOITE', 'SEXTA-FEIRA', 'Francisco de Assis Cavallaro'),
  ('e12724e8-fb72-58ca-8de5-6bf65da5dbe2', '14362', 'ALGORITMOS E PENSAMENTO COMPUTACIONAL', 'NOITE', 'QUINTA-FEIRA', 'Francisco de Assis Cavallaro'),
  ('729d31c8-c7a9-5aef-8564-f9af35fcde92', '14362', 'ALGORITMOS E PENSAMENTO COMPUTACIONAL', 'NOITE', 'TERÇA-FEIRA', 'Francisco de Assis Cavallaro'),
  ('25143f40-2f77-5a2c-8a6f-147259af9c2b', '14362', 'ALGORITMOS E PENSAMENTO COMPUTACIONAL', 'NOITE', 'SEGUNDA-FEIRA', 'Francisco de Assis Cavallaro'),
  ('d01c43d3-704d-5026-a992-0562bfa546bc', '12101', 'TÓPICOS AVANÇADOS EM SISTEMAS DE INFORMAÇÃO II', 'NOITE', 'QUARTA-FEIRA', 'Gabriel Rosa Paz'),
  ('209c855d-73e3-58d2-9768-ad025bee7659', '12101', 'TÓPICOS AVANÇADOS EM SISTEMAS DE INFORMAÇÃO II', 'NOITE', 'QUINTA-FEIRA', 'Gabriel Rosa Paz'),
  ('6054e8c9-1326-59f0-90c1-2c7b11852a3b', '12101', 'TÓPICOS AVANÇADOS EM SISTEMAS DE INFORMAÇÃO II', 'NOITE', 'SEXTA-FEIRA', 'Gabriel Rosa Paz'),
  ('fdf77bd6-0180-572c-a129-ff3316c8ae0d', '14365', 'MODELAGEM DE BANCO DE DADOS', 'NOITE', 'SEGUNDA-FEIRA', 'Gabriel Rosa Paz'),
  ('f3e18c7c-c9e3-5a59-b356-0e97a6c11ef7', '14363', 'DESENVOLVIMENTO FRONT-END PARA WEB', 'NOITE', 'SÁBADO', 'Gabriel Rosa Paz'),
  ('6fee0810-367d-5349-aae0-b3b67aee8f69', '10914', 'ANÁLISE E PROJETO DE SISTEMAS II', 'MANHA', 'SEGUNDA-FEIRA', 'Jadir Custódio Mendonça Junior'),
  ('550acc3e-699d-53df-b89f-ac79ce7a0f49', '10537', 'PROGRAMAÇÃO PARA DISPOSITIVOS MÓVEIS', 'MANHA', 'SEXTA-FEIRA', 'Jadir Custódio Mendonça Junior'),
  ('405112d0-f48e-5864-ae17-c3a731e034cc', '14339', 'DESENVOLVIMENTO DE APLICATIVOS MÓVEIS', 'MANHA', 'QUARTA-FEIRA', 'Jadir Custódio Mendonça Junior'),
  ('8c7fc4a1-151c-5e46-ad7d-43451b144a3f', '10537', 'PROGRAMAÇÃO PARA DISPOSITIVOS MÓVEIS', 'NOITE', 'TERÇA-FEIRA', 'Jadir Custódio Mendonça Junior'),
  ('22646928-4154-5708-a80d-71a2319d50a9', '10537', 'PROGRAMAÇÃO PARA DISPOSITIVOS MÓVEIS', 'NOITE', 'QUARTA-FEIRA', 'Jadir Custódio Mendonça Junior'),
  ('ecff15f8-d97e-58a8-b8e7-0211035132f9', '10537', 'PROGRAMAÇÃO PARA DISPOSITIVOS MÓVEIS', 'NOITE', 'SEXTA-FEIRA', 'Jadir Custódio Mendonça Junior'),
  ('fe77d270-ed15-5feb-b67d-fd9d8fedc2c7', '10509', 'TENDÊNCIAS EM CIÊNCIA DA COMPUTAÇÃO', 'MANHA', 'QUINTA-FEIRA', 'Juliano Ratusznei'),
  ('d8be1f33-888d-5b63-ae6f-9f7e245ddecc', '10545', 'ESTRUTURAS DE DADOS II', 'NOITE', 'TERÇA-FEIRA', 'Juliano Ratusznei'),
  ('45a2c81f-624f-5cca-8486-2e402bd78618', '10545', 'ESTRUTURAS DE DADOS II', 'NOITE', 'QUINTA-FEIRA', 'Juliano Ratusznei'),
  ('3735374c-46d3-57fe-a3e7-2e11cfdad868', '7090', 'PARADIGMAS DE LINGUAGENS DE PROGRAMAÇÃO', 'NOITE', 'SEGUNDA-FEIRA', 'Juliano Ratusznei'),
  ('d005c1ad-c7aa-50e4-9505-c2177c69348d', '10509', 'TENDÊNCIAS EM CIÊNCIA DA COMPUTAÇÃO', 'NOITE', 'QUARTA-FEIRA', 'Juliano Ratusznei'),
  ('187f4af7-7158-574b-b818-0c2b7bf64cf1', '14362', 'ALGORITMOS E PENSAMENTO COMPUTACIONAL', 'NOITE', 'QUARTA-FEIRA', 'Lucimara Pires Martins'),
  ('86f8e988-0ea1-55b7-8460-7f05887faf68', '14363', 'DESENVOLVIMENTO FRONT-END PARA WEB', 'NOITE', 'QUARTA-FEIRA', 'Luis Fernando Pizzaro Bueno Ramos'),
  ('d2f48a04-ef60-592d-a1b1-7d6de076bd76', '14363', 'DESENVOLVIMENTO FRONT-END PARA WEB', 'NOITE', 'SEXTA-FEIRA', 'Luis Fernando Pizzaro Bueno Ramos'),
  ('a6715e37-4738-5c80-8315-c0bed5cda7f6', '14339', 'DESENVOLVIMENTO DE APLICATIVOS MÓVEIS', 'NOITE', 'SEGUNDA-FEIRA', 'Luis Fernando Pizzaro Bueno Ramos'),
  ('6753e9d5-a11e-5c2e-8f09-5292d6ca0690', '14339', 'DESENVOLVIMENTO DE APLICATIVOS MÓVEIS', 'NOITE', 'TERÇA-FEIRA', 'Luis Fernando Pizzaro Bueno Ramos'),
  ('6d138246-254a-52d6-9c9c-11641d30ab57', '14339', 'DESENVOLVIMENTO DE APLICATIVOS MÓVEIS', 'NOITE', 'QUINTA-FEIRA', 'Luis Fernando Pizzaro Bueno Ramos'),
  ('1c7f9e97-1677-5460-893c-760930220ec5', '14363', 'DESENVOLVIMENTO FRONT-END PARA WEB', 'MANHA', 'QUINTA-FEIRA', 'Marcelo de Freitas Pintaud'),
  ('e1b326ff-815b-5b82-88f9-73234339c5e1', '14363', 'DESENVOLVIMENTO FRONT-END PARA WEB', 'NOITE', 'TERÇA-FEIRA', 'Marcelo de Freitas Pintaud'),
  ('b6d657c6-d9c4-5c82-b05d-5d71c0221f37', '14363', 'DESENVOLVIMENTO FRONT-END PARA WEB', 'NOITE', 'QUARTA-FEIRA', 'Marcelo de Freitas Pintaud'),
  ('5feb5ba0-5358-5877-912e-7489d49b0bac', '14363', 'DESENVOLVIMENTO FRONT-END PARA WEB', 'NOITE', 'QUINTA-FEIRA', 'Marcelo de Freitas Pintaud'),
  ('05de6749-8261-5496-a3d0-b03441a889a7', '14362', 'ALGORITMOS E PENSAMENTO COMPUTACIONAL', 'NOITE', 'TERÇA-FEIRA', 'Osvaldo Camargo Botelho dos Santos'),
  ('75d1588f-56f0-511b-bc81-cba171d05321', '14362', 'ALGORITMOS E PENSAMENTO COMPUTACIONAL', 'NOITE', 'QUINTA-FEIRA', 'Osvaldo Camargo Botelho dos Santos'),
  ('ed6b8a14-08ff-59f1-97a6-a85106006c8c', '14363', 'DESENVOLVIMENTO FRONT-END PARA WEB', 'NOITE', 'QUINTA-FEIRA', 'Paulo Henrique Mendes Carvalho'),
  ('faaf341d-1466-5162-84c6-1712c0419593', '14363', 'DESENVOLVIMENTO FRONT-END PARA WEB', 'NOITE', 'SEGUNDA-FEIRA', 'Paulo Henrique Mendes Carvalho'),
  ('5c4e4023-aaa8-5b6b-b982-fd2ae03bacab', '14363', 'DESENVOLVIMENTO FRONT-END PARA WEB', 'MANHA', 'TERÇA-FEIRA', 'Paulo Sérgio Fratta Junior'),
  ('0012e7f1-fe94-5c15-8721-747025b9a0cf', '14363', 'DESENVOLVIMENTO FRONT-END PARA WEB', 'NOITE', 'SEXTA-FEIRA', 'Paulo Sérgio Fratta Junior'),
  ('09a5b17a-60c9-5bf2-9941-a8401f0106e8', '14363', 'DESENVOLVIMENTO FRONT-END PARA WEB', 'NOITE', 'SÁBADO', 'Paulo Sérgio Fratta Junior'),
  ('eed38f0d-3105-535f-9c3b-312bfc6bcec7', '14363', 'DESENVOLVIMENTO FRONT-END PARA WEB', 'NOITE', 'SEGUNDA-FEIRA', 'Paulo Sérgio Fratta Junior'),
  ('d3e3628d-6627-5c82-a5ce-cb9197520f73', '14363', 'DESENVOLVIMENTO FRONT-END PARA WEB', 'NOITE', 'TERÇA-FEIRA', 'Paulo Sérgio Fratta Junior'),
  ('b0177b4e-6593-594b-af5c-791652b92881', '14340', 'PROJETO DE SOFTWARE', 'MANHA', 'SEGUNDA-FEIRA', 'Rafael Oliveira Cotrin'),
  ('2c67c52e-450c-5c88-b0f3-c023c3d63aca', '10914', 'ANÁLISE E PROJETO DE SISTEMAS II', 'NOITE', 'SEGUNDA-FEIRA', 'Rafael Oliveira Cotrin'),
  ('8b7e59e2-77f3-5511-94a3-a252b2e355f1', '10914', 'ANÁLISE E PROJETO DE SISTEMAS II', 'NOITE', 'TERÇA-FEIRA', 'Rafael Oliveira Cotrin'),
  ('512f8b38-9049-5111-8cfa-b6145e429465', '14362', 'ALGORITMOS E PENSAMENTO COMPUTACIONAL', 'NOITE', 'SÁBADO', 'Rafael Oliveira Cotrin'),
  ('0c80e1a3-d077-5712-8dcf-de20af363851', '14340', 'PROJETO DE SOFTWARE', 'NOITE', 'QUARTA-FEIRA', 'Rafael Oliveira Cotrin'),
  ('d07cd86a-3de8-5109-8417-515e16a37818', '14340', 'PROJETO DE SOFTWARE', 'NOITE', 'QUINTA-FEIRA', 'Rafael Oliveira Cotrin'),
  ('de5126dc-840e-52b0-86fa-2cf85578c8d2', '14340', 'PROJETO DE SOFTWARE', 'NOITE', 'SEXTA-FEIRA', 'Rafael Oliveira Cotrin'),
  ('f33da4bd-5fe8-5164-ba1a-9ce04d4f4b16', '14338', 'DESENVOLVIMENTO BACK-END', 'MANHA', 'TERÇA-FEIRA', 'Renato Alves Pereira'),
  ('3eb7ab1e-d6a7-5c8c-9b83-2529e0ee179f', '14338', 'DESENVOLVIMENTO BACK-END', 'NOITE', 'SEGUNDA-FEIRA', 'Renato Alves Pereira'),
  ('6b53b098-259e-56d6-8536-3de8799a9c81', '14338', 'DESENVOLVIMENTO BACK-END', 'NOITE', 'QUARTA-FEIRA', 'Renato Alves Pereira'),
  ('88871404-da1b-5983-9c3d-839f04c2f049', '12832', 'REDES DE COMPUTADORES', 'MANHA', 'TERÇA-FEIRA', 'Rodrigo Rodrigues'),
  ('223d7b61-e7c3-5973-bb8b-778ec349040c', '14364', 'DESIGN PROFISSIONAL', 'MANHA', 'QUINTA-FEIRA', 'Rodrigo Rodrigues'),
  ('c96b3c80-f2a1-5c41-879e-da6551d6832a', '14364', 'DESIGN PROFISSIONAL', 'MANHA', 'SEGUNDA-FEIRA', 'Rodrigo Rodrigues'),
  ('f7f0a398-e46a-5ff9-a151-600ddaf2b06a', '14364', 'DESIGN PROFISSIONAL', 'NOITE', 'SEGUNDA-FEIRA', 'Rodrigo Rodrigues'),
  ('9e1818e1-dbba-5f62-8c87-955781e323ed', '14364', 'DESIGN PROFISSIONAL', 'NOITE', 'TERÇA-FEIRA', 'Rodrigo Rodrigues'),
  ('2ab2c47e-f5ae-5483-ac37-d95995226158', '10227', 'PLANEJAMENTO ESTRATÉGICO DE TI', 'NOITE', 'SEGUNDA-FEIRA', 'Tatiana da Silva Hortelano Vieco'),
  ('8f21f688-eec6-55cb-b9e8-51bae7e67825', '10227', 'PLANEJAMENTO ESTRATÉGICO DE TI', 'MANHA', 'SEXTA-FEIRA', 'Wagner Tufano'),
  ('9a69a935-2546-5f21-a6cc-c8f01d3ddb1f', '10252', 'TÓPICOS ESPECIAIS EM TI', 'MANHA', 'QUARTA-FEIRA', 'Wagner Tufano'),
  ('935382b8-2c01-5d33-8ac1-5bbdb7dc0592', '10252', 'TÓPICOS ESPECIAIS EM TI', 'NOITE', 'QUARTA-FEIRA', 'Wagner Tufano'),
  ('8ecd944f-d62f-501c-9bf2-5c7503f24187', '10252', 'TÓPICOS ESPECIAIS EM TI', 'NOITE', 'QUINTA-FEIRA', 'Wagner Tufano');


-- Quanto da pontuacao do WECTI o aluno jogou em cada turma.
--
-- Uma linha por (aluno, turma). A conversao e 1000 pontos = 1,0 de nota,
-- entao os pontos ficam guardados em PONTOS, nao em nota: a nota e
-- derivada na leitura. Guardar a nota convertida criaria duas fontes de
-- verdade para o mesmo numero, e qualquer mudanca na conversao deixaria
-- as linhas antigas mentindo.
CREATE TABLE distribuicoes_pontuacao (
    id VARCHAR(36) PRIMARY KEY,
    aluno_id VARCHAR(36) NOT NULL,
    disciplina_id VARCHAR(36) NOT NULL,
    pontos INT NOT NULL,
    criado_em DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em DATETIME NULL,
    CONSTRAINT uk_distribuicao_aluno_disciplina UNIQUE (aluno_id, disciplina_id),
    CONSTRAINT fk_distribuicao_aluno FOREIGN KEY (aluno_id) REFERENCES usuarios(id),
    CONSTRAINT fk_distribuicao_disciplina FOREIGN KEY (disciplina_id) REFERENCES disciplinas(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
