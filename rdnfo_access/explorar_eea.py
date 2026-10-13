#!/usr/bin/env python3
import csv, io, json, zipfile, hashlib, urllib.request, os
from collections import Counter,defaultdict
from pathlib import Path

DIR=Path('eea-salidas');DIR.mkdir(exist_ok=True)
firms={};table_rows=[];summary=[];modules=[];seen_edition={};mat=[]
for ed in range(2012,2021):
    url=f'https://www.inei.gob.pe/media/DATOS_ABIERTOS/EEA/DATA/{ed}.zip'
    with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'Mozilla/5.0'}),timeout=90) as rsp:
        raw=rsp.read()
    assert zipfile.is_zipfile(io.BytesIO(raw))
    with zipfile.ZipFile(io.BytesIO(raw)) as z:
        counts=Counter();base_codes=set();master={}
        detail=Counter();modcount=0;c00module=0;badrows=0
        for info in z.infolist():
            if info.is_dir():continue
            if not info.filename.lower().endswith('.csv'):continue
            name=info.filename.split('/')[-1].lower()
            modcount+=1
            module=name.split('_c')[-1].split('_')[0] if '_c' in name else 'OTRO'
            counts[module]+=1
            if not '_c00_' in name:continue
            c00module+=1
            if info.file_size>250000000:continue
            content=z.read(info).decode('latin-1',errors='replace').lstrip('\ufeff')
            reader=csv.DictReader(io.StringIO(content),delimiter=';')
            if not reader.fieldnames or 'iruc' not in [f.strip().lower() for f in reader.fieldnames]:continue
            for row in reader:
                low={str(k).strip().lower():str(v or '').strip() for k,v in row.items() if k is not None}
                ident=low.get('iruc','')
                if not ident or ident=='0' or not ident.strip('0'):continue
                sector=low.get('codsector','')
                form=low.get('codformato','')
                estab=low.get('nroestablec','')
                flag=low.get('flagestablecimiento','')
                factor=low.get('factor_exp') or low.get('factor') or ''
                llave=(ident,sector,form)
                detail[(sector,form)]+=1
                base_codes.add(ident)
                if llave not in master:
                    master[llave]=(estab,flag,factor)
                else:
                    badrows+=1
        for (ident,sector,form),(estab,flag,factor) in master.items():
            table_rows.append([ed,ed-1,ident,sector,form,estab,flag,factor,
               'IRUC se conserva sin interpretarlo como identificador longitudinal verificado'])
        summary.append(dict(edicion=ed,anio_economico_presunto=ed-1,descarga_bytes=len(raw),
             sha256=hashlib.sha256(raw).hexdigest(),archivos_zip=len(z.infolist()),modulos_csv=modcount,
             modulos_c00=c00module,identificadores_iruc_distintos=len(base_codes),
             registros_distintos_iruc_sector_form=len(master),repeticiones_llave_en_c00=badrows,
             url=url,advertencia='aYYYY es marca en archivo; año económico sujeto a ficha tecnica'))
        firms[ed]=base_codes
        seen_edition[ed]=detail
        for module,n in counts.items(): modules.append([ed,ed-1,module,n])
        for (sector,form),n in detail.items():mat.append([ed,ed-1,sector,form,n])
    print('AUDIT',ed,'uniqueIRUC',len(base_codes),'c00',c00module,'records',len(master),'bytes',len(raw),flush=True)
overlap=[]
for a in range(2012,2021):
 for b in range(a+1,2021):
    shared=len(firms[a] & firms[b])
    overlap.append([a,b,len(firms[a]),len(firms[b]),shared,
      round(shared/max(1,len(firms[a])),4),
      'Superposición de códigos; no valida que IRUC sea estable ni que muestra sea panel'])
def write(name,header,rows):
 with open(DIR/name,'w',newline='',encoding='utf-8-sig') as f:
  w=csv.writer(f);w.writerow(header);w.writerows(rows)
write('EEA_Identificadores_Acumulados_2011_2019.csv',
      ['edicion_eea','anio_economico_presunto','iruc_anonimizado','codsector','codformato',
       'nroestablec_c00','flagestablecimiento_c00','factor_exp_c00_original','cautela'],
      table_rows)
write('EEA_Modulos_2012_2020.csv',['edicion_eea','anio_economico_presunto','modulo','cantidad_archivos_csv'],modules)
write('EEA_Cobertura_Sector_Formato.csv',['edicion_eea','anio_economico_presunto','codsector','codformato','filas_c00'],mat)
write('EEA_Cruces_IRUC_No_Validados.csv',
      ['edicion_a','edicion_b','ids_a','ids_b','identificadores_iguales','tasa_en_a','cautela'],overlap)
if summary:
 write('EEA_Resumen_Descargas_Verificado.csv',list(summary[0]),[[s[k] for k in summary[0]] for s in summary])
with open(DIR/'EEA_Archivo_y_Interpretacion.txt','w',encoding='utf-8') as f:
 f.write('Descargas oficiales INEI verificadas. a2011..a2019 refieren a marcas de los archivos, \n')
 f.write('que no prueban por si solas el periodo de referencia de cada variable.\n')
 f.write('Los IRUC anonimizados se conservan sin asumir continuidad longitudinal.\n')
 f.write('No se calculan productividad, PTF ni ATT con estos archivos C00 solamente.\n')
print('TOTAL_C00_KEYS',len(table_rows))
