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
        'contrato_utilizador', 'dia_real', 'planejamento_mes', 'ciclo_jornada'
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

    con.close()


if __name__ == '__main__':
    main()
