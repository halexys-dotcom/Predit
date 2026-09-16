#!/usr/bin/env python3
"""
Inspeciona uma copia da BD do Predit (predit.db) e imprime o estado essencial.

Uso:
    python tools/verificar-bd.py [caminho-do-ficheiro.db]

Sem argumento, usa %TEMP%\\predit-check.db (o caminho usado pelo fluxo
adb exec-out run-as ... cat databases/predit.db).
"""
import datetime
import os
import sqlite3
import sys


def para_data(epoch_day):
    return (datetime.date(1970, 1, 1) + datetime.timedelta(days=epoch_day)).isoformat()


def epoch_day(ano, mes, dia):
    # LocalDate.toEpochDay(): 1970-01-01 = 0
    return datetime.date(ano, mes, dia).toordinal() - 719163


def main():
    caminho = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.environ['TEMP'], 'predit-check.db')
    if not os.path.exists(caminho):
        raise SystemExit(f'Ficheiro nao encontrado: {caminho}')

    con = sqlite3.connect(caminho)
    con.row_factory = sqlite3.Row

    print(f'Ficheiro: {caminho} ({os.path.getsize(caminho)} bytes)')
    print('user_version:', con.execute('PRAGMA user_version').fetchone()[0])

    tabelas = [
        'tipo_turno', 'rotacao', 'rotacao_slot', 'aplicacao_rotacao', 'ausencia',
        'contrato_utilizador', 'dia_real', 'planejamento_mes', 'ciclo_jornada',
        'parametros_cct', 'rubrica', 'tabela_irs'
    ]
    print('Contagens:')
    for t in tabelas:
        print(f'  {t}: {con.execute(f"SELECT COUNT(*) FROM {t}").fetchone()[0]}')

    c = con.execute('SELECT * FROM contrato_utilizador WHERE id = 1').fetchone()
    if c:
        print('Contrato id=1:')
        for k in c.keys():
            print(f'  {k} = {c[k]}')
    else:
        print('Contrato id=1: AUSENTE')

    print('ciclo_jornada:')
    linhas = con.execute(
        'SELECT id, inicio, fim, realTotalMinutos, extrasPagosMinutos, saldoFinalMinutos FROM ciclo_jornada'
    ).fetchall()
    for r in linhas:
        print(f'  {r["id"]}: {para_data(r["inicio"])} a {para_data(r["fim"])} '
              f'real={r["realTotalMinutos"]} extras={r["extrasPagosMinutos"]} saldo={r["saldoFinalMinutos"]}')
    if not linhas:
        print('  (vazia)')

    prim = con.execute('SELECT MIN(data), MAX(data) FROM dia_real').fetchone()
    if prim[0] is not None:
        print(f'dia_real intervalo: {para_data(prim[0])} a {para_data(prim[1])} '
              f'(epochDay {prim[0]}..{prim[1]})')

    print('Dias usados pelo FecharSemestreTest (2025-07-01/02):')
    for d in (epoch_day(2025, 7, 1), epoch_day(2025, 7, 2)):
        n = con.execute('SELECT COUNT(*) FROM dia_real WHERE data = ?', (d,)).fetchone()[0]
        print(f'  {para_data(d)} (epochDay {d}): {n} registo(s)')

    existentes = {r[0] for r in con.execute("SELECT name FROM sqlite_master WHERE type='table'").fetchall()}
    if 'parametros_cct' in existentes:
        print('parametros_cct:')
        for r in con.execute('SELECT * FROM parametros_cct ORDER BY validoDe').fetchall():
            print(f'  vigente desde {para_data(r["validoDe"])}: base={r["vencimentoBaseMil"]} '
                  f'alim/dia={r["subAlimentacaoDiaMil"]} transp/mes={r["subTransporteMesMil"]} '
                  f'h/semana={r["horarioSemanalReferencia"]}')
    if 'rubrica' in existentes:
        print('rubrica:')
        for r in con.execute('SELECT * FROM rubrica ORDER BY ordem').fetchall():
            print(f'  {r["ordem"]:>2}  {r["codigo"]:<13} {r["nome"]:<24} '
                  f'SS={int(r["incideSS"])} IRS={int(r["incideIRS"])} Sind={int(r["incideSindicato"])} '
                  f'{r["tipoCalculo"]:<6} ativa={int(r["ativaConferencia"])}')

    if 'tabela_irs' in existentes:
        print('tabela_irs por regiao:')
        for regiao in ('CONTINENTE', 'ACORES', 'MADEIRA'):
            n = con.execute('SELECT COUNT(*) FROM tabela_irs WHERE regiao = ?', (regiao,)).fetchone()[0]
            t = con.execute('SELECT COUNT(DISTINCT tabelaNumero) FROM tabela_irs WHERE regiao = ?', (regiao,)).fetchone()[0]
            print(f'  {regiao}: {n} linhas em {t} tabelas')
        print('amostra CONTINENTE / tabela 1 (trabalho):')
        for r in con.execute(
                'SELECT ordemEscalao, limiteAte, taxaBasisPoints, parcelaAbater, parcelaAdicionalDep, formulaComposta '
                'FROM tabela_irs WHERE regiao = "CONTINENTE" AND tabelaNumero = 1 ORDER BY ordemEscalao').fetchall():
            print(f'  ordem {r["ordemEscalao"]:>2}: limiteAte={r["limiteAte"]} taxa={r["taxaBasisPoints"]} '
                  f'parcela={r["parcelaAbater"]} adicional={r["parcelaAdicionalDep"]} composta={r["formulaComposta"]}')

    con.close()


if __name__ == '__main__':
    main()
