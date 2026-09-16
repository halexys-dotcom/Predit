"""
Leitor de XLSX com a biblioteca-padrao (zipfile + xml) — sem openpyxl.

Uso:
    python tools/inspecionar-tabelas-irs.py            # estrutura das 3 regioes
    python tools/inspecionar-tabelas-irs.py --tudo     # todas as linhas de todas as folhas
"""
import os
import re
import sys
import zipfile
import xml.etree.ElementTree as ET

FICHEIROS = {
    'CONTINENTE': r'C:\Users\hah_c\Desktop\Cópia de Tabelas_RF_Continente_2026.xlsx',
    'ACORES': r'C:\Users\hah_c\Desktop\Cópia de Tabelas_RF_RA_Acores_2026.xlsx',
    'MADEIRA': r'C:\Users\hah_c\Desktop\Cópia de Tabelas_RF_2026_RAM.xlsx',
}

NS = '{http://schemas.openxmlformats.org/spreadsheetml/2006/main}'


def ler_shared_strings(z):
    try:
        raiz = ET.fromstring(z.read('xl/sharedStrings.xml'))
    except KeyError:
        return []
    textos = []
    for si in raiz.findall(f'{NS}si'):
        partes = [t.text or '' for t in si.iter(f'{NS}t')]
        textos.append(''.join(partes))
    return textos


def nome_folhas(z):
    raiz = ET.fromstring(z.read('xl/workbook.xml'))
    return [s.get('name') for s in raiz.iter(f'{NS}sheet')]


def coluna(ref):
    letras = re.match(r'([A-Z]+)', ref).group(1)
    n = 0
    for c in letras:
        n = n * 26 + (ord(c) - 64)
    return n


def ler_folha(z, indice, partilhados):
    nome = f'xl/worksheets/sheet{indice}.xml'
    raiz = ET.fromstring(z.read(nome))
    linhas = []
    for row in raiz.iter(f'{NS}row'):
        celulas = {}
        for c in row.findall(f'{NS}c'):
            ref = c.get('r') or ''
            tipo = c.get('t')
            v = c.find(f'{NS}v')
            if tipo == 's' and v is not None:
                valor = partilhados[int(v.text)]
            elif tipo == 'inlineStr':
                valor = ''.join(t.text or '' for t in c.iter(f'{NS}t'))
            elif v is not None:
                valor = v.text
            else:
                valor = ''
            if valor not in ('', None):
                celulas[coluna(ref)] = valor
        if celulas:
            maximo = max(celulas)
            linhas.append([celulas.get(i, '') for i in range(1, maximo + 1)])
    return linhas


def main():
    tudo = '--tudo' in sys.argv
    limite = 1000 if tudo else 8
    for regiao, caminho in FICHEIROS.items():
        print('=' * 100)
        print(f'{regiao}: {caminho}')
        if not os.path.exists(caminho):
            print('  NAO EXISTE')
            continue
        with zipfile.ZipFile(caminho) as z:
            partilhados = ler_shared_strings(z)
            folhas = nome_folhas(z)
            print(f'  folhas ({len(folhas)}): {folhas}')
            for i, folha in enumerate(folhas, start=1):
                linhas = ler_folha(z, i, partilhados)
                print(f'  --- sheet{i} ({folha}): {len(linhas)} linhas com conteudo ---')
                for linha in linhas[:limite]:
                    print('     ', ' | '.join(str(x) for x in linha))


if __name__ == '__main__':
    main()
