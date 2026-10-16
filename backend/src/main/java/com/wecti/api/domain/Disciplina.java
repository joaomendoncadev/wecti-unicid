package com.wecti.api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Uma <b>turma</b> do catalogo da coordenacao - a unidade em que o aluno
 * converte pontuacao do WECTI em nota.
 *
 * <p>Turma, e nao disciplina: o mesmo {@code codigo} aparece varias vezes
 * na planilha, uma por combinacao de turno, dia e professor. "MODELAGEM
 * DE BANCO DE DADOS" tem turmas de manha e de noite, com professores
 * diferentes. Como a nota e entregue a um professor especifico, o aluno
 * precisa escolher a turma dele, nao so a materia - por isso a chave
 * natural e (codigo, turno, dia, professor), e nao o codigo sozinho.
 *
 * <p>Catalogo so de leitura: entra por migration (ver
 * V9__catalogo_de_disciplinas.sql) e nao ha tela de cadastro. Planilha
 * nova vira migration nova.
 */
@Entity
@Table(name = "disciplinas", uniqueConstraints = @UniqueConstraint(
        columnNames = {"codigo", "turno", "dia_semana", "professor"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Disciplina {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 10)
    private String codigo;

    @Column(nullable = false, length = 150)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TurnoDisciplina turno;

    @Column(name = "dia_semana", nullable = false, length = 20)
    private String diaSemana;

    @Column(nullable = false, length = 150)
    private String professor;
}
