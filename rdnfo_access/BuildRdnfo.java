import io.github.spannm.jackcess.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * RDNFO - Seminario de Investigación I.
 * Crea un archivo Access ACE 2010 (.accdb) REAL con las 12 tablas públicas auditadas.
 * Fuentes y advertencias se conservan literalmente; no hay observaciones EEA inventadas.
 */
public final class BuildRdnfo {
  static final String[] FILES = {
    "01_InternetFijo_75_departamento_anio_ACCESS.csv",
    "02_InternetMovil_MTC_2017_ACCESS.csv",
    "03_Series_Nacionales_MTC_vintages_ACCESS.csv",
    "04_2018_Cortes_Trimestrales_ACCESS.csv",
    "05_OSIPTEL_Serie_Grafico_Aproximada.csv",
    "06_ERESTEL_Indicadores_Hogares.csv",
    "07_Despliegue_RDNFO_6_Etapas_ACCESS.csv",
    "08_Controles_Calidad_Conciliados.csv",
    "09_Descriptivo_2017_2019_Comparabilidad.csv",
    "10_Descriptivo_2019_2020_COVID.csv",
    "11_Catalogo_196_Provincias_UBIGEO_Referencia_ACCESS.csv",
    "12_Provincias_por_Departamento_ACCESS.csv"
  };
  static final String[] TABLES = {
    "T01_InternetFijo", "T02_InternetMovil", "T03_MTC_Nacional",
    "T04_Cortes2018", "T05_OSIPTEL_Aprox", "T06_ERESTEL",
    "T07_DespliegueRDNFO", "T08_ControlesCalidad", "T09_Comparacion1719",
    "T10_Comparacion1920", "T11_Provincias", "T12_ProvDepartamento"
  };
  static final Set<String> INTEGERS = new HashSet<>(Arrays.asList(
    "anio","conexiones","internet_fijo","internet_residencial","internet_comercial",
    "entrega_id","capitales_provinciales","nodos_totales","kilometros_fibra",
    "fijo_2017","fijo_2019","fijo_2020","diferencia_abs","provincias"
  ));
  static final Set<String> FLOATS = new HashSet<>(Arrays.asList(
    "conexiones_en_miles","valor","diferencia_pct",
    "idh_2019_compilado_aprox","pobreza_publicada_compilada_pct"
  ));
  static List<String> parseCsvLine(String src) {
    ArrayList<String> out=new ArrayList<>(); StringBuilder field=new StringBuilder();
    boolean quotes=false;
    for(int i=0;i<src.length();i++) {
      char c=src.charAt(i);
      if(c=='"') {
        if(quotes && i+1<src.length() && src.charAt(i+1)=='"') {
          field.append('"'); i++;
        } else quotes=!quotes;
      } else if(c==',' && !quotes) {
        out.add(field.toString()); field.setLength(0);
      } else field.append(c);
    }
    out.add(field.toString());return out;
  }
  static DataType colType(String key) {
    if(INTEGERS.contains(key)) return DataType.LONG;
    if(FLOATS.contains(key)) return DataType.DOUBLE;
    return DataType.TEXT;
  }
  static Object value(String key,String raw) {
    if(raw==null || raw.trim().isEmpty()) return null;
    if(INTEGERS.contains(key)) return Integer.valueOf(raw.trim());
    if(FLOATS.contains(key)) return Double.valueOf(raw.trim());
    return raw;
  }
  public static void main(String[] args) throws Exception {
    Path out=Paths.get("RDNFO_Seminario_Investigacion_Diaz_Rodas.accdb");
    Files.deleteIfExists(out);
    int total=0;
    try(Database db=DatabaseBuilder.newDatabase(out.toFile())
        .withFileFormat(Database.FileFormat.V2010).withAutoSync(false).create()) {
      for(int idx=0;idx<FILES.length;idx++) {
        List<String> lines=Files.readAllLines(Paths.get("rdnfo_data",FILES[idx]),StandardCharsets.UTF_8);
        List<String> cols=parseCsvLine(lines.get(0).replace("\uFEFF",""));
        TableBuilder builder=new TableBuilder(TABLES[idx]);
        for(String c:cols) {
          ColumnBuilder cb=new ColumnBuilder(c).setType(colType(c));
          builder.addColumn(cb);
        }
        Table table=builder.toTable(db);
        int count=0;
        for(int n=1;n<lines.size();n++) {
          if(lines.get(n).trim().isEmpty()) continue;
          List<String> values=parseCsvLine(lines.get(n));
          if(values.size()!=cols.size()) throw new IllegalStateException(FILES[idx]+":"+(n+1)+" has "+values.size()+" vs "+cols.size());
          Object[] row=new Object[cols.size()];
          for(int c=0;c<row.length;c++)row[c]=value(cols.get(c),values.get(c));
          table.addRow(row); count++;
        }
        total+=count;
        System.out.println("CREADA "+TABLES[idx]+" filas="+count);
      }
      db.flush();
    }
    if(total!=423) throw new IllegalStateException("Numero de registros inesperado "+total);
    int verified=0;
    try(Database db=DatabaseBuilder.open(out.toFile())) {
      for(int i=0;i<TABLES.length;i++) {
        Table table=db.getTable(TABLES[i]);
        if(table==null) throw new IllegalStateException("Tabla faltante "+TABLES[i]);
        int n=0;for(Row ignored:table) n++;
        verified+=n;
        System.out.println("LECTURA "+TABLES[i]+"="+n);
      }
    }
    if(verified!=total)throw new IllegalStateException("Relectura mismatch "+total+" "+verified);
    System.out.println("ACCDB_VALIDADO tables="+TABLES.length+" rows="+verified+" size="+Files.size(out));
  }
}
