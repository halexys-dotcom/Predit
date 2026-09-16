"""
Extrai as tabelas de retencao na fonte 2026 dos 3 XLSX para JSON normalizado.

Regras (do comando da Fase 8.2a):
  limiteAte            = coluna "Remuneracao mensal (e)" x 10000
  taxaBasisPoints      = coluna "Taxa marginal maxima" x 100000
  parcelaAbater        = coluna "Parcela a abater (e)" x 10000
  parcelaAdicionalDep  = coluna "... por dependente (e)" x 10000 (0 se nao existir)
  formulaComposta      = true quando a parcela a abater vem como taxa x k x (X - R)
"""
import json
import os
import re
import unicodedata
import zipfile
import xml.etree.ElementTree as ET

FICHEIROS = {
    'CONTINENTE': r'C:\Users\hah_c\Desktop\Cópia de Tabelas_RF_Continente_2026.xlsx',
    'ACORES': r'C:\Users\hah_c\Desktop\Cópia de Tabelas_RF_RA_Acores_2026.xlsx',
    'MADEIRA': r'C:\Users\hah_c\Desktop\Cópia de Tabelas_RF_2026_RAM.xlsx',
}

NS = '{http://schemas.openxmlformats.org/spreadsheetml/2006/main}'
ROMANOS = ['I', 'II', 'III', 'IV', 'V', 'VI', 'VII', 'VIII', 'IX', 'X', 'XI']
LIMITE_ABERTO = 9223372036854775807     # Long.MAX_VALUE: escalao "Superior a ..."


def sem_acentos(texto):
    return ''.join(c for c in unicodedata.normalize('NFKD', str(texto))
                   if not unicodedata.combining(c))


def ler_shared_strings(z):
    try:
        raiz = ET.fromstring(z.read('xl/sharedStrings.xml'))
    except KeyError:
        return []
    return [''.join(t.text or '' for t in si.iter(f'{NS}t')) for si in raiz.findall(f'{NS}si')]


def coluna(ref):
    n = 0
    for c in re.match(r'([A-Z]+)', ref).group(1):
        n = n * 26 + (ord(c) - 64)
    return n


def ler_folha(z, indice, partilhados):
    raiz = ET.fromstring(z.read(f'xl/worksheets/sheet{indice}.xml'))
    linhas = []
    for row in raiz.iter(f'{NS}row'):
        celulas = {}
        for c in row.findall(f'{NS}c'):
            tipo, v = c.get('t'), c.find(f'{NS}v')
            if tipo == 's' and v is not None:
                valor = partilhados[int(v.text)]
            elif tipo == 'inlineStr':
                valor = ''.join(t.text or '' for t in c.iter(f'{NS}t'))
            elif v is not None:
                valor = v.text
            else:
                valor = ''
            if valor not in ('', None):
                celulas[coluna(c.get('r') or 'A1')] = valor
        if celulas:
            linhas.append([celulas.get(i, '') for i in range(1, max(celulas) + 1)])
    return linhas


def numero(texto):
    try:
        return float(str(texto).replace(',', '.'))
    except ValueError:
        return None


def em_milhoes(valor):
    """euros -> unidades de 1/10000, arredondado ao meio para cima."""
    return int(round(valor * 10000))


def cabecalho_indices(linha):
    """Devolve (col_parcela, col_adicional) a partir da linha de cabecalho."""
    col_parcela = col_adicional = None
    for i, c in enumerate(linha):
        texto = sem_acentos(c).lower()
        if 'parcela a abater' in texto:
            col_parcela = i
        if 'por dependente' in texto:
            col_adicional = i
    return col_parcela, col_adicional


def coluna_ancora(linha):
    """(indice da ancora, tipo) — 'Até' ou 'Superior a'. A folha comeca com celula vazia."""
    for k, c in enumerate(linha):
        if str(c).strip():
            texto = sem_acentos(c).strip().lower()
            if texto == 'ate':
                return k, 'ate'
            if texto.startswith('superior'):
                return k, 'superior'
            return None, None
    return None, None


def extrair(linhas, regiao, categoria):
    tabelas = []
    atual = None
    for i, linha in enumerate(linhas):
        texto = ' '.join(str(c) for c in linha)
        achado = re.search(r'Tabela\s+([IVX]+)\b', texto)
        if achado and achado.group(1) in ROMANOS:
            if atual:
                tabelas.append(atual)
            perfil = ''
            for j in range(i + 1, min(i + 4, len(linhas))):
                junta = ' '.join(str(c) for c in linhas[j]).strip()
                if junta and 'remuneracao' not in sem_acentos(junta).lower():
                    perfil = junta
                    break
            atual = {
                'regiao': regiao,
                'categoria': categoria,
                'tabelaNumero': ROMANOS.index(achado.group(1)) + 1,
                'perfil': perfil,
                'colParcela': None,
                'colAdicional': None,
                'escaloes': []
            }
            continue
        if atual is None or not linha:
            continue

        if 'por dependente' in sem_acentos(texto).lower():
            col_parcela, col_adicional = cabecalho_indices(linha)
            atual['colParcela'] = col_parcela
            atual['colAdicional'] = col_adicional
            continue

        col_ate, tipo = coluna_ancora(linha)
        if col_ate is None or len(linha) < col_ate + 3:
            continue
        taxa = numero(linha[col_ate + 2])
        if taxa is None:
            continue
        if tipo == 'ate':
            bruto = numero(linha[col_ate + 1])
            if bruto is None:
                continue
            limite = em_milhoes(bruto)
        else:
            limite = LIMITE_ABERTO     # "Superior a ...": escalao sem fim

        inicio = col_ate + 3
        fim = atual['colAdicional'] if atual['colAdicional'] is not None else len(linha)
        composta = any('- R' in str(c) for c in linha[inicio:fim])

        parcela = 0.0
        col_parcela = atual['colParcela']
        if not composta and col_parcela is not None and col_parcela < len(linha):
            valor = numero(linha[col_parcela])
            if valor is not None:
                parcela = valor
        adicional = 0.0
        col = atual['colAdicional']
        if col is not None and col < len(linha):
            valor = numero(linha[col])
            if valor is not None:
                adicional = valor

        atual['escaloes'].append({
            'ordemEscalao': len(atual['escaloes']),
            'limiteAte': limite,
            'taxaBasisPoints': int(round(taxa * 100000)),
            'parcelaAbater': em_milhoes(parcela),
            'parcelaAdicionalDep': em_milhoes(adicional),
            'formulaComposta': bool(composta)
        })
    if atual:
        tabelas.append(atual)
    return tabelas


def gerar_kotlin(todas):
    linhas = [
        'package pt.haconnect.predit.data.local',
        '',
        '/**',
        ' * Tabelas de retenção na fonte 2026 (Continente, Açores e Madeira), extraídas dos',
        ' * três XLSX oficiais. NÃO EDITAR À MÃO — regenerar com:',
        ' *   python tools/extrair-tabelas-irs.py',
        ' *',
        ' * Argumentos por ordem: id (0 = autoGenerate), ano, regiao, categoria, tabelaNumero,',
        ' * ordemEscalao, limiteAte, taxaBasisPoints, parcelaAbater, parcelaAdicionalDep,',
        ' * formulaComposta. Unidades: 1/10000 € (ver domain/model/Dinheiro.kt).',
        ' *',
        ' * Perfis (dos subtítulos dos XLSX): I não casado sem dependentes ou casado 2 titulares;',
        ' * II não casado com dependentes; III casado único titular; IV a VII os mesmos com',
        ' * pessoa com deficiência; VIII a XI pensões.',
        ' */',
        'val TABELAS_IRS_2026: List<TabelaIRSEntity> = listOf(',
    ]
    for t in todas:
        for e in t['escaloes']:
            limite = 'Long.MAX_VALUE' if e['limiteAte'] == LIMITE_ABERTO else f"{e['limiteAte']}L"
            linhas.append(
                '    TabelaIRSEntity(0L, 2026, "{regiao}", "{categoria}", {tab}, {ordem}, '
                '{limite}, {taxa}, {parcela}L, {adicional}L, {composta}),'.format(
                    regiao=t['regiao'], categoria=t['categoria'], tab=t['tabelaNumero'],
                    ordem=e['ordemEscalao'], limite=limite, taxa=e['taxaBasisPoints'],
                    parcela=e['parcelaAbater'], adicional=e['parcelaAdicionalDep'],
                    composta='true' if e['formulaComposta'] else 'false'))
    linhas.append(')')

    raiz = os.path.dirname(os.path.abspath(__file__))
    caminho = os.path.normpath(os.path.join(
        raiz, '..', 'app', 'src', 'main', 'java', 'pt', 'haconnect', 'predit', 'data', 'local',
        'TabelasIRSIniciais.kt'))
    with open(caminho, 'w', encoding='utf-8') as f:
        f.write('\n'.join(linhas) + '\n')
    print(f'Kotlin gerado: {caminho}')
    for regiao in FICHEIROS:
        tabelas = [t for t in todas if t['regiao'] == regiao]
        print(f"  {regiao}: {len(tabelas)} tabelas, "
              f"{sum(len(t['escaloes']) for t in tabelas)} escaloes")
    print(f"  TOTAL: {sum(len(t['escaloes']) for t in todas)} linhas")


def main():
    todas = []
    for regiao, caminho in FICHEIROS.items():
        with zipfile.ZipFile(caminho) as z:
            partilhados = ler_shared_strings(z)
            for indice, categoria in ((1, 'TRABALHO'), (2, 'PENSOES')):
                for tabela in extrair(ler_folha(z, indice, partilhados), regiao, categoria):
                    todas.append(tabela)

    destino = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'tabelas-irs-2026.json')
    with open(destino, 'w', encoding='utf-8') as f:
        json.dump(todas, f, ensure_ascii=False, indent=1)

    print(f'{len(todas)} tabelas escritas em {destino}')
    gerar_kotlin(todas)
    for t in todas:
        print("  {regiao:<10} {categoria:<9} T{tabelaNumero:<2} {n:>2} escaloes | {perfil}".format(
            regiao=t['regiao'], categoria=t['categoria'], tabelaNumero=t['tabelaNumero'],
            n=len(t['escaloes']), perfil=t['perfil'][:58]))

    for t in todas:
        if t['regiao'] == 'CONTINENTE' and t['categoria'] == 'TRABALHO' and t['tabelaNumero'] == 1:
            print('\nAmostra exigida pelo comando (CONTINENTE / tabela 1, trabalho):')
            for e in t['escaloes']:
                print('  ordem {ordemEscalao}: limiteAte={limiteAte} taxa={taxaBasisPoints} '
                      'parcela={parcelaAbater} adicional={parcelaAdicionalDep} '
                      'composta={formulaComposta}'.format(**e))


if __name__ == '__main__':
    main()

