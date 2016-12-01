package ni.org.ics.zip.service;

import com.mysql.jdbc.jdbc2.optional.MysqlDataSource;
import ni.org.ics.zip.utils.ExportParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Date;

/**
 * Created by FIRSTICT on 11/10/2016.
 * V1.0
 */
@Service("exportarService")
@Transactional
public class ExportarService {

    private static final String SEPARADOR = ",";
    private static final String SALTOLINEA = "\n";
    private static final String QUOTE = "\"";
    private static final String COMA = "\"";



    private static Connection getConnection() throws Exception{
        MysqlDataSource dataSource = new MysqlDataSource();
        dataSource.setUser("zikazip");
        dataSource.setPassword("jeKAQudi");
        dataSource.setServerName("localhost");
        dataSource.setPort(3306);
        dataSource.setDatabaseName("zika_zip");

        return dataSource.getConnection();
    }

    private List<String> getTableMetaData(String tableName) throws Exception{
        Connection con = getConnection();
        List<String> columns = new ArrayList<String>();
        try {
            DatabaseMetaData meta = con.getMetaData();
            ResultSet res = meta.getColumns(null, null, tableName, null);
            System.out.println("List of columns: ");
            while (res.next()) {
                //excluir estos campos
                if (!res.getString("COLUMN_NAME").equalsIgnoreCase("identificador_equipo") &&
                        !res.getString("COLUMN_NAME").equalsIgnoreCase("end") &&
                        !res.getString("COLUMN_NAME").equalsIgnoreCase("estado") &&
                        !res.getString("COLUMN_NAME").equalsIgnoreCase("id_instancia") &&
                        !res.getString("COLUMN_NAME").equalsIgnoreCase("instance_path") &&
                        !res.getString("COLUMN_NAME").equalsIgnoreCase("PASIVO") &&
                        !res.getString("COLUMN_NAME").equalsIgnoreCase("phonenumber") &&
                        !res.getString("COLUMN_NAME").equalsIgnoreCase("FECHA_REGISTRO") &&
                        !res.getString("COLUMN_NAME").equalsIgnoreCase("USUARIO_REGISTRO") &&
                        !res.getString("COLUMN_NAME").equalsIgnoreCase("simserial") &&
                        !res.getString("COLUMN_NAME").equalsIgnoreCase("start") &&
                        !res.getString("COLUMN_NAME").equalsIgnoreCase("today") &&
                        !res.getString("COLUMN_NAME").equalsIgnoreCase("prescreen_id")
                        ) {
                    if (res.getString("COLUMN_NAME").equalsIgnoreCase("record_id") && !columns.isEmpty()) {
                        //el record_id siempre debe ser el primer campo
                        String columnaTmp = columns.get(0);
                        columns.set(0,res.getString("COLUMN_NAME"));
                        columns.add(columnaTmp);
                    }
                    else {
                        columns.add(res.getString("COLUMN_NAME"));
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }finally {
            if (con !=null)
                con.close();
        }
        return columns;
    }

    public StringBuffer getZp00ExportData(ExportParameters exportParameters) throws Exception{
        StringBuffer sb = new StringBuffer();

        Connection con = getConnection();
        PreparedStatement pStatement = null;
        ResultSet res = null;
        String columnas = "";
        String valores = "";
        try {
            //recuperar los nombres de las columnas
            List<String> columns = getTableMetaData(exportParameters.getTableName());
            columnas = parseColumns(columns);

            //pasar a recuperar los datos. Setear parámetro si los hay
            StringBuilder sqlStrBuilder = new StringBuilder();
            sqlStrBuilder.append("select ").append(columnas).append(" from ").append(exportParameters.getTableName());

            if (exportParameters.thereAreValues()) sqlStrBuilder.append(" where record_id between ? and ? ");

            pStatement = con.prepareStatement(sqlStrBuilder.toString());
            if (exportParameters.thereAreValues()){
                pStatement.setString(1, exportParameters.getCodigoInicio());
                pStatement.setString(2, exportParameters.getCodigoFin());
            }

            res = pStatement.executeQuery();

            //columnas que necesita redcap y no estan en la tabla
            columnas = columnas.replaceAll("scr_prestudyna","scr_prestudyna___1");
            columnas += SEPARADOR + "zp00_screening_complete";

            sb.append(columnas);
            sb.append(SALTOLINEA);

            while(res.next()){
                for(String col : columns){
                    Object val = res.getObject(col);
                        if (val!=null){
                            if (col.equalsIgnoreCase("scr_prestudyna")){
                                if (valores.isEmpty()) valores += val.toString();
                                else valores += SEPARADOR + val.toString();
                            }else {
                                if (val instanceof String) {
                                    //si contiene uno de estos caracteres especiales escapar
                                    if (val.toString().contains(SEPARADOR) || val.toString().contains(COMA) || val.toString().contains(SALTOLINEA)){
                                        valores += SEPARADOR + QUOTE + val.toString() + QUOTE;
                                    }else {
                                        if (valores.isEmpty()) valores += val.toString();
                                        else valores += SEPARADOR + val.toString();
                                    }
                                } else if (val instanceof Integer) {
                                    if (valores.isEmpty()) valores += String.valueOf(res.getInt(col));
                                    else valores += SEPARADOR + String.valueOf(res.getInt(col));

                                } else if (val instanceof java.util.Date) {
                                    if (valores.isEmpty()) valores += DateToString(res.getDate(col), "dd/MM/yyyyy");
                                    else valores += SEPARADOR + DateToString(res.getDate(col), "dd/MM/yyyyy");

                                } else if (val instanceof Float) {
                                    if (valores.isEmpty()) valores += String.valueOf(res.getFloat(col));
                                    else valores += SEPARADOR + String.valueOf(res.getFloat(col));
                                }
                            }
                        }else{
                                valores += SEPARADOR;

                        }
                }
                //valor para zp00_screening_complete
                valores += SEPARADOR + "1";
                sb.append(valores);
                valores = "";
                sb.append(SALTOLINEA);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }finally {
            if (res !=null) res.close();
            if (pStatement !=null) pStatement.close();
            if (con !=null) con.close();
        }
        return sb;
    }

    public StringBuffer getZp01ADExportData(ExportParameters exportParameters) throws Exception{

        StringBuffer sb = new StringBuffer();
        Connection con = getConnection();
        PreparedStatement pStatement = null;
        ResultSet res = null;
        String columnas = "";
        String valores = "";

        try {
            //recuperar los nombres de las columnas
            List<String> columns = getTableMetaData(exportParameters.getTableName());
            columnas = parseColumns(columns);

            //pasar a recuperar los datos. Setear parámetro si los hay
            StringBuilder sqlStrBuilder = new StringBuilder();
            sqlStrBuilder.append("select ").append(columnas).append(" from ").append(exportParameters.getTableName());

            if (exportParameters.thereAreValues()) sqlStrBuilder.append(" where record_id between ? and ? ");

            pStatement = con.prepareStatement(sqlStrBuilder.toString());
            if (exportParameters.thereAreValues()){
                pStatement.setString(1, exportParameters.getCodigoInicio());
                pStatement.setString(2, exportParameters.getCodigoFin());
            }

            res = pStatement.executeQuery();

            //Columnas que necesita redcap y no estan en la tabla
            columnas = columnas.replaceAll("sea_lmpunknown","sea_lmpunknown___1");
            columnas = columnas.replaceAll("sea_leavena","sea_leavena___1");
            columnas += SEPARADOR + "zp01_study_entry_section_a_to_d_complete";

            sb.append(columnas);
            sb.append(SALTOLINEA);

            while(res.next()){
                for(String col : columns){
                    Object val = res.getObject(col);
                    if (val!=null){
                        if (val instanceof String) {
                            //si contiene uno de estos caracteres especiales escapar
                            if (val.toString().contains(SEPARADOR) || val.toString().contains(COMA) || val.toString().contains(SALTOLINEA)){
                                valores += SEPARADOR + QUOTE + val.toString() + QUOTE;
                            }else {
                                if (valores.isEmpty()) valores += val.toString();
                                else valores += SEPARADOR + val.toString();
                            }
                        } else if (val instanceof Integer) {
                            if (valores.isEmpty()) valores += String.valueOf(res.getInt(col));
                            else valores += SEPARADOR + String.valueOf(res.getInt(col));

                        } else if (val instanceof java.util.Date) {
                            if (valores.isEmpty()) valores += DateToString(res.getDate(col), "dd/MM/yyyyy");
                            else valores += SEPARADOR + DateToString(res.getDate(col), "dd/MM/yyyyy");

                        } else if (val instanceof Float) {
                            if (valores.isEmpty()) valores += String.valueOf(res.getFloat(col));
                            else valores += SEPARADOR + String.valueOf(res.getFloat(col));
                        }

                    }else{
                            valores += SEPARADOR;
                    }
                }
                //valor para zp01_study_entry_section_a_to_d_complete
                valores += SEPARADOR + "1";
                sb.append(valores);
                valores = "";
                sb.append(SALTOLINEA);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }finally {
            if (res !=null) res.close();
            if (pStatement !=null) pStatement.close();
            if (con !=null) con.close();
        }
        return sb;
    }

    public StringBuffer getZp01EExportData(ExportParameters exportParameters) throws Exception{

        StringBuffer sb = new StringBuffer();
        Connection con = getConnection();
        PreparedStatement pStatement = null;
        ResultSet res = null;
        String columnas = "";
        String valores = "";

        try {
            //recuperar los nombres de las columnas
            List<String> columns = getTableMetaData(exportParameters.getTableName());
            columnas = parseColumns(columns);

            //pasar a recuperar los datos. Setear parámetro si los hay
            StringBuilder sqlStrBuilder = new StringBuilder();
            sqlStrBuilder.append("select ").append(columnas).append(" from ").append(exportParameters.getTableName());

            if (exportParameters.thereAreValues()) sqlStrBuilder.append(" where record_id between ? and ? ");

            pStatement = con.prepareStatement(sqlStrBuilder.toString());
            if (exportParameters.thereAreValues()){
                pStatement.setString(1, exportParameters.getCodigoInicio());
                pStatement.setString(2, exportParameters.getCodigoFin());
            }

            res = pStatement.executeQuery();
            //Valores de campos múltiples
            String[] diseases = "1,2,3,4,5,6,7,8,9,98".split(",");
            String[] rashFirst = "1,2,3,4,5,6,7,8,9".split(",");
            String[] spreadPart = "1,2,3,4,5,6,7,8,9".split(",");
            String[] sameSymptom = "1,2,3".split(",");
            String[] specifySymptom = "1,2,3,4,5,6,7,8,9,10,11,12,13".split(",");
            String[] arm = "1,2".split(",");
            String[] leg = "1,2".split(",");
            String[] hand = "1,2".split(",");
            String[] foot = "1,2".split(",");
            String[] face = "1,2".split(",");

            //columnas que necesita redcap y no estan en la tabla
            columnas = columnas.replaceAll("sea_diseases","sea_diseases___1,sea_diseases___2,sea_diseases___3,sea_diseases___4,sea_diseases___5,sea_diseases___6,sea_diseases___7,sea_diseases___8,sea_diseases___9,sea_diseases___98");
            columnas = columnas.replaceAll("sea_rash_first","sea_rash_first___1,sea_rash_first___2,sea_rash_first___3,sea_rash_first___4,sea_rash_first___5,sea_rash_first___6,sea_rash_first___7,sea_rash_first___8,sea_rash_first___9");
            columnas = columnas.replaceAll("sea_spread_part","sea_spread_part___1,sea_spread_part___2,sea_spread_part___3,sea_spread_part___4,sea_spread_part___5,sea_spread_part___6,sea_spread_part___7,sea_spread_part___8,sea_spread_part___9");
            columnas = columnas.replaceAll("sea_same_symptom","sea_same_symptom___1,sea_same_symptom___2,sea_same_symptom___3");
            columnas = columnas.replaceAll("sea_specify_symptom","sea_specify_symptom___1,sea_specify_symptom___2,sea_specify_symptom___3,sea_specify_symptom___4,sea_specify_symptom___5,sea_specify_symptom___6,sea_specify_symptom___7,sea_specify_symptom___8,sea_specify_symptom___9,sea_specify_symptom___10,sea_specify_symptom___11,sea_specify_symptom___12,sea_specify_symptom___13");
            columnas = columnas.replaceAll("sea_tingling_arm","sea_tingling_arm___1,sea_tingling_arm___2");
            columnas = columnas.replaceAll("sea_tingling_leg","sea_tingling_leg___1,sea_tingling_leg___2");
            columnas = columnas.replaceAll("sea_tingling_hand","sea_tingling_hand___1,sea_tingling_hand___2");
            columnas = columnas.replaceAll("sea_tingling_foot","sea_tingling_foot___1,sea_tingling_foot___2");
            columnas = columnas.replaceAll("sea_tingling_face","sea_tingling_face___1,sea_tingling_face___2");
            columnas = columnas.replaceAll("sea_numb_arm","sea_numb_arm___1,sea_numb_arm___2");
            columnas = columnas.replaceAll("sea_numb_leg","sea_numb_leg___1,sea_numb_leg___2");
            columnas = columnas.replaceAll("sea_numb_hand","sea_numb_hand___1,sea_numb_hand___2");
            columnas = columnas.replaceAll("sea_numb_foot","sea_numb_foot___1,sea_numb_foot___2");
            columnas = columnas.replaceAll("sea_numb_face","sea_numb_face___1,sea_numb_face___2");
            columnas = columnas.replaceAll("sea_para_arm","sea_para_arm___1,sea_para_arm___2");
            columnas = columnas.replaceAll("sea_para_leg","sea_para_leg___1,sea_para_leg___2");
            columnas = columnas.replaceAll("sea_para_hand","sea_para_hand___1,sea_para_hand___2");
            columnas = columnas.replaceAll("sea_para_foot","sea_para_foot___1,sea_para_foot___2");
            columnas = columnas.replaceAll("sea_para_face","sea_para_face___1,sea_para_face___2");
            columnas = columnas.replaceAll("sea_tempunknown","sea_tempunknown___1");
            columnas += SEPARADOR + "zp01_study_entry_section_e_complete";

            sb.append(columnas);
            sb.append(SALTOLINEA);

            while(res.next()){
                for(String col : columns){
                    Object val = res.getObject(col);
                    if (val!=null){
                        if (col.equalsIgnoreCase("sea_diseases")){
                            valores += setValuesMultipleField(val.toString(), diseases);

                        }else if (col.equalsIgnoreCase("sea_rash_first")) {
                            valores += setValuesMultipleField(val.toString(), rashFirst);

                        }else if (col.equalsIgnoreCase("sea_spread_part")) {
                            valores += setValuesMultipleField(val.toString(), spreadPart);

                        }else if (col.equalsIgnoreCase("sea_same_symptom")) {
                            valores += setValuesMultipleField(val.toString(), sameSymptom);

                        }else if (col.equalsIgnoreCase("sea_specify_symptom")) {
                            valores += setValuesMultipleField(val.toString(), specifySymptom);

                        }else if (col.equalsIgnoreCase("sea_tingling_arm") || col.equalsIgnoreCase("sea_numb_arm") || col.equalsIgnoreCase("sea_para_arm")){
                            valores += setValuesMultipleField(val.toString(), arm);

                        }else if (col.equalsIgnoreCase("sea_tingling_leg") || col.equalsIgnoreCase("sea_numb_leg") || col.equalsIgnoreCase("sea_para_leg")){
                            valores += setValuesMultipleField(val.toString(), leg);

                        }else if (col.equalsIgnoreCase("sea_tingling_hand") || col.equalsIgnoreCase("sea_numb_hand") || col.equalsIgnoreCase("sea_para_hand")){
                            valores += setValuesMultipleField(val.toString(), hand);

                        }else if (col.equalsIgnoreCase("sea_tingling_foot") || col.equalsIgnoreCase("sea_numb_foot") || col.equalsIgnoreCase("sea_para_foot")){
                            valores += setValuesMultipleField(val.toString(), foot);

                        }else if (col.equalsIgnoreCase("sea_tingling_face") || col.equalsIgnoreCase("sea_numb_face") || col.equalsIgnoreCase("sea_para_face")) {
                            valores += setValuesMultipleField(val.toString(), face);

                        }else {
                            if (val instanceof String) {
                                //si contiene uno de estos caracteres especiales escapar
                                if (val.toString().contains(SEPARADOR) || val.toString().contains(COMA) || val.toString().contains(SALTOLINEA)){
                                    valores += SEPARADOR + QUOTE + val.toString() + QUOTE;
                                }else {
                                    if (valores.isEmpty()) valores += val.toString();
                                    else valores += SEPARADOR + val.toString();
                                }
                            } else if (val instanceof Integer) {
                                if (valores.isEmpty()) valores += String.valueOf(res.getInt(col));
                                else valores += SEPARADOR + String.valueOf(res.getInt(col));

                            } else if (val instanceof java.util.Date) {
                                if (valores.isEmpty()) valores += DateToString(res.getDate(col), "dd/MM/yyyyy");
                                else valores += SEPARADOR + DateToString(res.getDate(col), "dd/MM/yyyyy");

                            } else if (val instanceof Float) {
                                if (valores.isEmpty()) valores += String.valueOf(res.getFloat(col));
                                else valores += SEPARADOR + String.valueOf(res.getFloat(col));
                            }
                        }
                    }else{
                        if (col.equalsIgnoreCase("sea_diseases")){
                            for(int i = 0 ; i< diseases.length; i++){
                                valores += SEPARADOR;
                            }
                        }else if (col.equalsIgnoreCase("sea_rash_first")){
                            for(int i = 0 ; i< rashFirst.length; i++){
                                valores += SEPARADOR;
                            }
                        }else if (col.equalsIgnoreCase("sea_spread_part")){
                            for(int i = 0 ; i< spreadPart.length; i++){
                                valores += SEPARADOR;
                            }
                        }else if (col.equalsIgnoreCase("sea_same_symptom")){
                            for(int i = 0 ; i< sameSymptom.length; i++){
                                valores += SEPARADOR;
                            }
                        }else if (col.equalsIgnoreCase("sea_specify_symptom")){
                            for(int i = 0 ; i< specifySymptom.length; i++){
                                valores += SEPARADOR;
                            }
                        } else if (col.equalsIgnoreCase("sea_tingling_arm") || col.equalsIgnoreCase("sea_numb_arm") || col.equalsIgnoreCase("sea_para_arm") ||
                                col.equalsIgnoreCase("sea_tingling_leg") || col.equalsIgnoreCase("sea_numb_leg") || col.equalsIgnoreCase("sea_para_leg") ||
                                col.equalsIgnoreCase("sea_tingling_hand") || col.equalsIgnoreCase("sea_numb_hand") || col.equalsIgnoreCase("sea_para_hand") ||
                                col.equalsIgnoreCase("sea_tingling_foot") || col.equalsIgnoreCase("sea_numb_foot") || col.equalsIgnoreCase("sea_para_foot") ||
                                col.equalsIgnoreCase("sea_tingling_face") || col.equalsIgnoreCase("sea_numb_face") || col.equalsIgnoreCase("sea_para_face")){
                            valores += SEPARADOR + SEPARADOR;
                        }
                        else {
                            valores += SEPARADOR;
                        }
                    }
                }
                //valor para zp01_study_entry_section_e_complete
                valores += SEPARADOR + "1";
                sb.append(valores);
                valores = "";
                sb.append(SALTOLINEA);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }finally {
            if (res !=null) res.close();
            if (pStatement !=null) pStatement.close();
            if (con !=null) con.close();
        }
        return sb;
    }

    public StringBuffer getZp01FKExportData(ExportParameters exportParameters) throws Exception{

        StringBuffer sb = new StringBuffer();
        Connection con = getConnection();
        PreparedStatement pStatement = null;
        ResultSet res = null;
        String columnas = "";
        String valores = "";

        try {
            //recuperar los nombres de las columnas
            List<String> columns = getTableMetaData(exportParameters.getTableName());
            columnas = parseColumns(columns);

            //pasar a recuperar los datos. Setear parámetro si los hay
            StringBuilder sqlStrBuilder = new StringBuilder();
            sqlStrBuilder.append("select ").append(columnas).append(" from ").append(exportParameters.getTableName());

            if (exportParameters.thereAreValues()) sqlStrBuilder.append(" where record_id between ? and ? ");

            pStatement = con.prepareStatement(sqlStrBuilder.toString());
            if (exportParameters.thereAreValues()){
                pStatement.setString(1, exportParameters.getCodigoInicio());
                pStatement.setString(2, exportParameters.getCodigoFin());
            }

            res = pStatement.executeQuery();
            //Valores de campos múltiples
            String[] characterDisch = "1,2,3,4,5,6".split(",");

            //Columnas que necesita redcap y no estan en la tabla
            columnas = columnas.replaceAll("sea_character_disch","sea_character_disch___1,sea_character_disch___2,sea_character_disch___3,sea_character_disch___4,sea_character_disch___5,sea_character_disch___6");
            columnas += SEPARADOR + "zp01_study_entry_section_f_to_k_complete";

            sb.append(columnas);
            sb.append(SALTOLINEA);

            while(res.next()){
                for(String col : columns){
                    Object val = res.getObject(col);
                    if (val!=null){
                        if (col.equalsIgnoreCase("sea_character_disch")){
                            valores += setValuesMultipleField(val.toString(), characterDisch);

                        }else if (col.equalsIgnoreCase("sea_oneweek_time") || col.equalsIgnoreCase("sea_next_time")){ //campos tipo hora HH:mm
                            if (valores.isEmpty()) valores += val.toString().substring(0,5);
                            else valores += SEPARADOR + val.toString().substring(0,5);
                        } else {
                            if (val instanceof String) {
                                //si contiene uno de estos caracteres especiales escapar
                                if (val.toString().contains(SEPARADOR) || val.toString().contains(COMA) || val.toString().contains(SALTOLINEA)) {
                                    valores += SEPARADOR + QUOTE + val.toString() + QUOTE;
                                } else {
                                    if (valores.isEmpty()) valores += val.toString();
                                    else valores += SEPARADOR + val.toString();
                                }
                            } else if (val instanceof Integer) {
                                if (valores.isEmpty()) valores += String.valueOf(res.getInt(col));
                                else valores += SEPARADOR + String.valueOf(res.getInt(col));

                            } else if (val instanceof java.util.Date) {
                                if (valores.isEmpty()) valores += DateToString(res.getDate(col), "dd/MM/yyyyy");
                                else valores += SEPARADOR + DateToString(res.getDate(col), "dd/MM/yyyyy");

                            } else if (val instanceof Float) {
                                if (valores.isEmpty()) valores += String.valueOf(res.getFloat(col));
                                else valores += SEPARADOR + String.valueOf(res.getFloat(col));
                            }
                        }
                    }else{
                        if (col.equalsIgnoreCase("sea_character_disch")){
                            for(int i = 0 ; i< characterDisch.length; i++){
                                valores += SEPARADOR;
                            }
                        }else {
                            valores += SEPARADOR;
                        }
                    }
                }
                //valor para zp01_study_entry_section_f_to_k_complete
                valores += SEPARADOR + "1";
                sb.append(valores);
                valores = "";
                sb.append(SALTOLINEA);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }finally {
            if (res !=null) res.close();
            if (pStatement !=null) pStatement.close();
            if (con !=null) con.close();
        }
        return sb;
    }

    public StringBuffer getZp02ExportData(ExportParameters exportParameters) throws Exception{

        StringBuffer sb = new StringBuffer();
        Connection con = getConnection();
        PreparedStatement pStatement = null;
        ResultSet res = null;
        String columnas = "";
        String valores = "";

        try {
            //recuperar los nombres de las columnas
            List<String> columns = getTableMetaData(exportParameters.getTableName());
            columnas = parseColumns(columns);

            //pasar a recuperar los datos. Setear parámetro si los hay
            StringBuilder sqlStrBuilder = new StringBuilder();
            sqlStrBuilder.append("select ").append(columnas).append(" from ").append(exportParameters.getTableName());

            if (exportParameters.thereAreValues()) sqlStrBuilder.append(" where record_id between ? and ? ");

            pStatement = con.prepareStatement(sqlStrBuilder.toString());
            if (exportParameters.thereAreValues()){
                pStatement.setString(1, exportParameters.getCodigoInicio());
                pStatement.setString(2, exportParameters.getCodigoFin());
            }

            res = pStatement.executeQuery();

            //Valores de campos múltiples
            String[] bscMatOtherType = "1,4".split(",");

            //Columnas que necesita redcap y no estan en la tabla
            columnas = columnas.replaceFirst("bsc_mat_other_type","bsc_mat_other_type___1,bsc_mat_other_type___4");
            columnas += SEPARADOR + "zp02_biospecimen_collection_complete";

            sb.append(columnas);
            sb.append(SALTOLINEA);

            while(res.next()){
                for(String col : columns){
                    Object val = res.getObject(col);
                    if (val!=null){
                        if (col.equalsIgnoreCase("bsc_mat_other_type")){
                            valores += setValuesMultipleField(val.toString(), bscMatOtherType);
                        //campos tipo hora HH:mm
                        }else if (col.equalsIgnoreCase("bsc_mat_bld_time") || col.equalsIgnoreCase("bsc_mat_slva_time") || col.equalsIgnoreCase("bsc_mat_vag_time") || col.equalsIgnoreCase("bsc_mat_vst_urn_time") || col.equalsIgnoreCase("bsc_mat_hom_urn_time1")
                                || col.equalsIgnoreCase("bsc_mat_hom_urn_time2") || col.equalsIgnoreCase("bsc_mat_hom_urn_time3") || col.equalsIgnoreCase("bsc_mat_hom_urn_time4") || col.equalsIgnoreCase("bsc_mat_amf_time") || col.equalsIgnoreCase("bsc_mat_cord_time")
                                || col.equalsIgnoreCase("bsc_mat_placen_time") || col.equalsIgnoreCase("bsc_mat_breastm_time") || col.equalsIgnoreCase("bsc_mat_fetalt_time") || col.equalsIgnoreCase("bsc_matd_breastm_time")){
                            if (valores.isEmpty()) valores += val.toString().substring(0,5);
                            else valores += SEPARADOR + val.toString().substring(0,5);

                        } else {
                            if (val instanceof String) {
                                //si contiene uno de estos caracteres especiales escapar
                                if (val.toString().contains(SEPARADOR) || val.toString().contains(COMA) || val.toString().contains(SALTOLINEA)) {
                                    valores += SEPARADOR + QUOTE + val.toString() + QUOTE;
                                } else {
                                    if (valores.isEmpty()) valores += val.toString();
                                    else valores += SEPARADOR + val.toString();
                                }
                            } else if (val instanceof Integer) {
                                if (valores.isEmpty()) valores += String.valueOf(res.getInt(col));
                                else valores += SEPARADOR + String.valueOf(res.getInt(col));

                            } else if (val instanceof java.util.Date) {
                                if (valores.isEmpty()) valores += DateToString(res.getDate(col), "dd/MM/yyyyy");
                                else valores += SEPARADOR + DateToString(res.getDate(col), "dd/MM/yyyyy");

                            } else if (val instanceof Float) {
                                if (valores.isEmpty()) valores += String.valueOf(res.getFloat(col));
                                else valores += SEPARADOR + String.valueOf(res.getFloat(col));
                            }
                        }
                    }else{
                        if (col.equalsIgnoreCase("bsc_mat_other_type")){
                            for(int i = 0 ; i< bscMatOtherType.length; i++){
                                valores += SEPARADOR;
                            }
                        }else {
                            valores += SEPARADOR;
                        }
                    }
                }
                //valor para zp02_biospecimen_collection_complete
                valores += SEPARADOR + "1";
                sb.append(valores);
                valores = "";
                sb.append(SALTOLINEA);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }finally {
            if (res !=null) res.close();
            if (pStatement !=null) pStatement.close();
            if (con !=null) con.close();
        }
        return sb;
    }

    public StringBuffer getZp03ExportData(ExportParameters exportParameters) throws Exception{

        StringBuffer sb = new StringBuffer();
        Connection con = getConnection();
        PreparedStatement pStatement = null;
        ResultSet res = null;
        String columnas = "";
        String valores = "";

        try {
            //recuperar los nombres de las columnas
            List<String> columns = getTableMetaData(exportParameters.getTableName());
            columnas = parseColumns(columns);

            //pasar a recuperar los datos. Setear parámetro si los hay
            StringBuilder sqlStrBuilder = new StringBuilder();
            sqlStrBuilder.append("select ").append(columnas).append(" from ").append(exportParameters.getTableName());

            if (exportParameters.thereAreValues()) sqlStrBuilder.append(" where record_id between ? and ? ");

            pStatement = con.prepareStatement(sqlStrBuilder.toString());
            if (exportParameters.thereAreValues()){
                pStatement.setString(1, exportParameters.getCodigoInicio());
                pStatement.setString(2, exportParameters.getCodigoFin());
            }

            res = pStatement.executeQuery();

            //Valores de campos múltiples
            String[] characterDisch = "1,2,3,4,5,6".split(",");
            String[] rashFirst = "1,2,3,4,5,6,7,8,9".split(",");
            String[] spreadPart = "1,2,3,4,5,6,7,8,9".split(",");
            String[] specifySymptom = "1,2,3,4,5,6,7,8,9,10,11,12,13".split(",");
            String[] arm = "1,2".split(",");
            String[] leg = "1,2".split(",");
            String[] hand = "1,2".split(",");
            String[] foot = "1,2".split(",");
            String[] face = "1,2".split(",");

            //columnas que necesita redcap y no estan en la tabla
            columnas = columnas.replaceAll("mon_charac_discharge","mon_character_disch___1,mon_character_disch___2,mon_character_disch___3,mon_character_disch___4,mon_character_disch___5,mon_character_disch___6");
            columnas = columnas.replaceAll("mon_rash_first","mon_rash_first___1,mon_rash_first___2,mon_rash_first___3,mon_rash_first___4,mon_rash_first___5,mon_rash_first___6,mon_rash_first___7,mon_rash_first___8,mon_rash_first___9");
            columnas = columnas.replaceAll("mon_spread_part","mon_spread_part___1,mon_spread_part___2,mon_spread_part___3,mon_spread_part___4,mon_spread_part___5,mon_spread_part___6,mon_spread_part___7,mon_spread_part___8,mon_spread_part___9");
            columnas = columnas.replaceAll("mon_specify_symptom","mon_specify_symptom___1,mon_specify_symptom___2,mon_specify_symptom___3,mon_specify_symptom___4,mon_specify_symptom___5,mon_specify_symptom___6,mon_specify_symptom___7,mon_specify_symptom___8,mon_specify_symptom___9,mon_specify_symptom___10,mon_specify_symptom___11,mon_specify_symptom___12,mon_specify_symptom___13");
            columnas = columnas.replaceAll("mon_tingling_arm","mon_tingling_arm___1,mon_tingling_arm___2");
            columnas = columnas.replaceAll("mon_tingling_leg","mon_tingling_leg___1,mon_tingling_leg___2");
            columnas = columnas.replaceAll("mon_tingling_hand","mon_tingling_hand___1,mon_tingling_hand___2");
            columnas = columnas.replaceAll("mon_tingling_foot","mon_tingling_foot___1,mon_tingling_foot___2");
            columnas = columnas.replaceAll("mon_tingling_face","mon_tingling_face___1,mon_tingling_face___2");
            columnas = columnas.replaceAll("mon_numb_arm","mon_numb_arm___1,mon_numb_arm___2");
            columnas = columnas.replaceAll("mon_numb_leg","mon_numb_leg___1,mon_numb_leg___2");
            columnas = columnas.replaceAll("mon_numb_hand","mon_numb_hand___1,mon_numb_hand___2");
            columnas = columnas.replaceAll("mon_numb_foot","mon_numb_foot___1,mon_numb_foot___2");
            columnas = columnas.replaceAll("mon_numb_face","mon_numb_face___1,mon_numb_face___2");
            columnas = columnas.replaceAll("mon_para_arm","mon_para_arm___1,mon_para_arm___2");
            columnas = columnas.replaceAll("mon_para_leg","mon_para_leg___1,mon_para_leg___2");
            columnas = columnas.replaceAll("mon_para_hand","mon_para_hand___1,mon_para_hand___2");
            columnas = columnas.replaceAll("mon_para_foot","mon_para_foot___1,mon_para_foot___2");
            columnas = columnas.replaceAll("mon_para_face","mon_para_face___1,mon_para_face___2");
            columnas = columnas.replaceAll("mon_tempunknown","mon_tempunknown___1");
            columnas += SEPARADOR + "zp03_monthly_visit_complete";

            sb.append(columnas);
            sb.append(SALTOLINEA);

            while(res.next()){
                for(String col : columns){
                    Object val = res.getObject(col);
                    if (val!=null){
                        if (col.equalsIgnoreCase("mon_character_disch")){
                            valores += setValuesMultipleField(val.toString(), characterDisch);

                        }else if (col.equalsIgnoreCase("mon_rash_first")) {
                            valores += setValuesMultipleField(val.toString(), rashFirst);

                        }else if (col.equalsIgnoreCase("mon_spread_part")) {
                            valores += setValuesMultipleField(val.toString(), spreadPart);

                        }else if (col.equalsIgnoreCase("mon_specify_symptom")) {
                            valores += setValuesMultipleField(val.toString(), specifySymptom);

                        }else if (col.equalsIgnoreCase("mon_tingling_arm") || col.equalsIgnoreCase("mon_numb_arm") || col.equalsIgnoreCase("mon_para_arm")){
                            valores += setValuesMultipleField(val.toString(), arm);

                        }else if (col.equalsIgnoreCase("mon_tingling_leg") || col.equalsIgnoreCase("mon_numb_leg") || col.equalsIgnoreCase("mon_para_leg")){
                            valores += setValuesMultipleField(val.toString(), leg);

                        }else if (col.equalsIgnoreCase("mon_tingling_hand") || col.equalsIgnoreCase("mon_numb_hand") || col.equalsIgnoreCase("mon_para_hand")){
                            valores += setValuesMultipleField(val.toString(), hand);

                        }else if (col.equalsIgnoreCase("mon_tingling_foot") || col.equalsIgnoreCase("mon_numb_foot") || col.equalsIgnoreCase("mon_para_foot")){
                            valores += setValuesMultipleField(val.toString(), foot);

                        }else if (col.equalsIgnoreCase("mon_tingling_face") || col.equalsIgnoreCase("mon_numb_face") || col.equalsIgnoreCase("mon_para_face")) {
                            valores += setValuesMultipleField(val.toString(), face);

                        }else if (col.equalsIgnoreCase("mon_oneweek_time") || col.equalsIgnoreCase("mon_next_time")){ //campos tipo hora HH:mm
                            if (valores.isEmpty()) valores += val.toString().substring(0,5);
                            else valores += SEPARADOR + val.toString().substring(0,5);
                        } else {
                            if (val instanceof String) {
                                //si contiene uno de estos caracteres especiales escapar
                                if (val.toString().contains(SEPARADOR) || val.toString().contains(COMA) || val.toString().contains(SALTOLINEA)){
                                    valores += SEPARADOR + QUOTE + val.toString() + QUOTE;
                                }else {
                                    if (valores.isEmpty()) valores += val.toString();
                                    else valores += SEPARADOR + val.toString();
                                }
                            } else if (val instanceof Integer) {
                                if (valores.isEmpty()) valores += String.valueOf(res.getInt(col));
                                else valores += SEPARADOR + String.valueOf(res.getInt(col));

                            } else if (val instanceof java.util.Date) {
                                if (valores.isEmpty()) valores += DateToString(res.getDate(col), "dd/MM/yyyyy");
                                else valores += SEPARADOR + DateToString(res.getDate(col), "dd/MM/yyyyy");

                            } else if (val instanceof Float) {
                                if (valores.isEmpty()) valores += String.valueOf(res.getFloat(col));
                                else valores += SEPARADOR + String.valueOf(res.getFloat(col));
                            }
                        }
                    }else{
                        if (col.equalsIgnoreCase("mon_character_disch")){
                            for(int i = 0 ; i< characterDisch.length; i++){
                                valores += SEPARADOR;
                            }
                        }else if (col.equalsIgnoreCase("mon_rash_first")){
                            for(int i = 0 ; i< rashFirst.length; i++){
                                valores += SEPARADOR;
                            }
                        }else if (col.equalsIgnoreCase("mon_spread_part")){
                            for(int i = 0 ; i< spreadPart.length; i++){
                                valores += SEPARADOR;
                            }
                        }else if (col.equalsIgnoreCase("mon_specify_symptom")){
                            for(int i = 0 ; i< specifySymptom.length; i++){
                                valores += SEPARADOR;
                            }
                        } else if (col.equalsIgnoreCase("mon_tingling_arm") || col.equalsIgnoreCase("mon_numb_arm") || col.equalsIgnoreCase("mon_para_arm") ||
                                col.equalsIgnoreCase("mon_tingling_leg") || col.equalsIgnoreCase("mon_numb_leg") || col.equalsIgnoreCase("mon_para_leg") ||
                                col.equalsIgnoreCase("mon_tingling_hand") || col.equalsIgnoreCase("mon_numb_hand") || col.equalsIgnoreCase("mon_para_hand") ||
                                col.equalsIgnoreCase("mon_tingling_foot") || col.equalsIgnoreCase("mon_numb_foot") || col.equalsIgnoreCase("mon_para_foot") ||
                                col.equalsIgnoreCase("mon_tingling_face") || col.equalsIgnoreCase("mon_numb_face") || col.equalsIgnoreCase("mon_para_face")){
                            valores += SEPARADOR + SEPARADOR;
                        }
                        else {
                            valores += SEPARADOR;
                        }
                    }
                }
                //valor para zp03_monthly_visit_complete
                valores += SEPARADOR + "1";
                sb.append(valores);
                valores = "";
                sb.append(SALTOLINEA);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }finally {
            if (res !=null) res.close();
            if (pStatement !=null) pStatement.close();
            if (con !=null) con.close();
        }
        return sb;
    }

    public StringBuffer getZp04ADExportData(ExportParameters exportParameters) throws Exception{

        StringBuffer sb = new StringBuffer();
        Connection con = getConnection();
        PreparedStatement pStatement = null;
        ResultSet res = null;
        String columnas = "";
        String valores = "";

        try {
            //recuperar los nombres de las columnas
            List<String> columns = getTableMetaData(exportParameters.getTableName());
            columnas = parseColumns(columns);

            //pasar a recuperar los datos. Setear parámetro si los hay
            StringBuilder sqlStrBuilder = new StringBuilder();
            sqlStrBuilder.append("select ").append(columnas).append(" from ").append(exportParameters.getTableName());

            if (exportParameters.thereAreValues()) sqlStrBuilder.append(" where record_id between ? and ? ");

            pStatement = con.prepareStatement(sqlStrBuilder.toString());
            if (exportParameters.thereAreValues()){
                pStatement.setString(1, exportParameters.getCodigoInicio());
                pStatement.setString(2, exportParameters.getCodigoFin());
            }

            res = pStatement.executeQuery();

            String[] houseAmenities = "1,2,3,4,5,6,7,8".split(",");
            String[] transAccess = "1,2,3,4,5,6".split(",");
            String[] animalTyp = "1,2,3,4,5".split(",");

            //Columnas que necesita redcap y no estan en la tabla
            columnas = columnas.replaceAll("tri_prim_job_title_ref","tri_prim_job_title_ref___1");
            columnas = columnas.replaceAll("tri_prev_job_title_ref","tri_prev_job_title_ref___1");
            columnas = columnas.replaceAll("tri_husb_job_title_ref","tri_husb_job_title_ref___1");
            columnas = columnas.replaceAll("tri_resid_ref","tri_resid_ref___1");
            columnas = columnas.replaceAll("tri_house_amenities","tri_house_amenities___1,tri_house_amenities___2,tri_house_amenities___3,tri_house_amenities___4,tri_house_amenities___5,tri_house_amenities___6,tri_house_amenities___7,tri_house_amenities___8");
            columnas = columnas.replaceAll("tri_trans_access","tri_trans_access___1,tri_trans_access___2,tri_trans_access___3,tri_trans_access___4,tri_trans_access___5,tri_trans_access___6");
            columnas = columnas.replaceAll("tri_animal_typ","tri_animal_typ___1,tri_animal_typ___2,tri_animal_typ___3,tri_animal_typ___4,tri_animal_typ___5");
            columnas += SEPARADOR + "zp04_trimester_visit_section_a_to_d_complete";

            sb.append(columnas);
            sb.append(SALTOLINEA);

            while(res.next()){
                for(String col : columns){
                    Object val = res.getObject(col);
                    if (val!=null){
                        if (col.equalsIgnoreCase("tri_house_amenities")){
                            valores += setValuesMultipleField(val.toString(),houseAmenities);
                        }else if (col.equalsIgnoreCase("tri_trans_access")){
                            valores += setValuesMultipleField(val.toString(),transAccess);
                        }else if (col.equalsIgnoreCase("tri_animal_typ")){
                            valores += setValuesMultipleField(val.toString(),animalTyp);
                        }else {
                            if (val instanceof String) {
                                //si contiene uno de estos caracteres especiales escapar
                                if (val.toString().contains(SEPARADOR) || val.toString().contains(COMA) || val.toString().contains(SALTOLINEA)) {
                                    valores += SEPARADOR + QUOTE + val.toString() + QUOTE;
                                } else {
                                    if (valores.isEmpty()) valores += val.toString();
                                    else valores += SEPARADOR + val.toString();
                                }
                            } else if (val instanceof Integer) {
                                if (valores.isEmpty()) valores += String.valueOf(res.getInt(col));
                                else valores += SEPARADOR + String.valueOf(res.getInt(col));

                            } else if (val instanceof java.util.Date) {
                                if (valores.isEmpty()) valores += DateToString(res.getDate(col), "dd/MM/yyyyy");
                                else valores += SEPARADOR + DateToString(res.getDate(col), "dd/MM/yyyyy");

                            } else if (val instanceof Float) {
                                if (valores.isEmpty()) valores += String.valueOf(res.getFloat(col));
                                else valores += SEPARADOR + String.valueOf(res.getFloat(col));
                            }
                        }

                    }else{
                        if (col.equalsIgnoreCase("tri_house_amenities")){
                            for(int i=0;i< houseAmenities.length;i++){
                                valores += SEPARADOR;
                            }
                        }else if (col.equalsIgnoreCase("tri_trans_access")){
                            for(int i=0;i< transAccess.length;i++){
                                valores += SEPARADOR;
                            }
                        }else if (col.equalsIgnoreCase("tri_animal_typ")){
                            for(int i=0;i< animalTyp.length;i++){
                                valores += SEPARADOR;
                            }
                        }else {
                            valores += SEPARADOR;
                        }
                    }
                }
                //valor para zp04_trimester_visit_section_a_to_d_complete
                valores += SEPARADOR + "1";
                sb.append(valores);
                valores = "";
                sb.append(SALTOLINEA);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }finally {
            if (res !=null) res.close();
            if (pStatement !=null) pStatement.close();
            if (con !=null) con.close();
        }
        return sb;
    }

    public StringBuffer getZp04EExportData(ExportParameters exportParameters) throws Exception{

        StringBuffer sb = new StringBuffer();
        Connection con = getConnection();
        PreparedStatement pStatement = null;
        ResultSet res = null;
        String columnas = "";
        String valores = "";

        try {
            //recuperar los nombres de las columnas
            List<String> columns = getTableMetaData(exportParameters.getTableName());
            columnas = parseColumns(columns);

            //pasar a recuperar los datos. Setear parámetro si los hay
            StringBuilder sqlStrBuilder = new StringBuilder();
            sqlStrBuilder.append("select ").append(columnas).append(" from ").append(exportParameters.getTableName());

            if (exportParameters.thereAreValues()) sqlStrBuilder.append(" where record_id between ? and ? ");

            pStatement = con.prepareStatement(sqlStrBuilder.toString());
            if (exportParameters.thereAreValues()){
                pStatement.setString(1, exportParameters.getCodigoInicio());
                pStatement.setString(2, exportParameters.getCodigoFin());
            }

            res = pStatement.executeQuery();

            //Columnas que necesita redcap y no estan en la tabla
            columnas += SEPARADOR + "zp04_trimester_visit_section_e_complete";

            sb.append(columnas);
            sb.append(SALTOLINEA);

            while(res.next()){
                for(String col : columns){
                    Object val = res.getObject(col);
                    if (val!=null){
                        if (val instanceof String) {
                            //si contiene uno de estos caracteres especiales escapar
                            if (val.toString().contains(SEPARADOR) || val.toString().contains(COMA) || val.toString().contains(SALTOLINEA)) {
                                valores += SEPARADOR + QUOTE + val.toString() + QUOTE;
                            } else {
                                if (valores.isEmpty()) valores += val.toString();
                                else valores += SEPARADOR + val.toString();
                            }
                        } else if (val instanceof Integer) {
                            if (valores.isEmpty()) valores += String.valueOf(res.getInt(col));
                            else valores += SEPARADOR + String.valueOf(res.getInt(col));

                        } else if (val instanceof java.util.Date) {
                            if (valores.isEmpty()) valores += DateToString(res.getDate(col), "dd/MM/yyyyy");
                            else valores += SEPARADOR + DateToString(res.getDate(col), "dd/MM/yyyyy");

                        } else if (val instanceof Float) {
                            if (valores.isEmpty()) valores += String.valueOf(res.getFloat(col));
                            else valores += SEPARADOR + String.valueOf(res.getFloat(col));
                        }
                    }else{
                        valores += SEPARADOR;
                    }
                }
                //valor para zp04_trimester_visit_section_e_complete
                valores += SEPARADOR + "1";
                sb.append(valores);
                valores = "";
                sb.append(SALTOLINEA);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }finally {
            if (res !=null) res.close();
            if (pStatement !=null) pStatement.close();
            if (con !=null) con.close();
        }
        return sb;
    }

    public StringBuffer getZp04FHExportData(ExportParameters exportParameters) throws Exception{

        StringBuffer sb = new StringBuffer();
        Connection con = getConnection();
        PreparedStatement pStatement = null;
        ResultSet res = null;
        String columnas = "";
        String valores = "";

        try {
            //recuperar los nombres de las columnas
            List<String> columns = getTableMetaData(exportParameters.getTableName());
            columnas = parseColumns(columns);

            //valores campos multiples
            String [] mosqRepTyp = "1,2,3,4,5".split(",");

            //pasar a recuperar los datos. Setear parámetro si los hay
            StringBuilder sqlStrBuilder = new StringBuilder();
            sqlStrBuilder.append("select ").append(columnas).append(" from ").append(exportParameters.getTableName());

            if (exportParameters.thereAreValues()) sqlStrBuilder.append(" where record_id between ? and ? ");

            pStatement = con.prepareStatement(sqlStrBuilder.toString());
            if (exportParameters.thereAreValues()){
                pStatement.setString(1, exportParameters.getCodigoInicio());
                pStatement.setString(2, exportParameters.getCodigoFin());
            }

            res = pStatement.executeQuery();

            //Columnas que necesita redcap y no estan en la tabla
            columnas = columnas.replaceAll("tri_mosq_rep_dk_spray","tri_mosq_rep_dk_spray___1");
            columnas = columnas.replaceAll("tri_mosq_rep_dk_lotion","tri_mosq_rep_dk_lotion___1");
            columnas = columnas.replaceAll("tri_mosq_rep_dk_spiral","tri_mosq_rep_dk_spiral___1");
            columnas = columnas.replaceAll("tri_mosq_rep_dk_plugin","tri_mosq_rep_dk_plugin___1");
            columnas = columnas.replaceAll("tri_mosq_rep_dk_other","tri_mosq_rep_dk_other___1");
            columnas = columnas.replaceAll("tri_mosq_rep_typ","tri_mosq_rep_typ___1,tri_mosq_rep_typ___2,tri_mosq_rep_typ___3,tri_mosq_rep_typ___4,tri_mosq_rep_typ___5");
            columnas += SEPARADOR + "zp04_trimester_visit_section_f_to_h_complete";

            sb.append(columnas);
            sb.append(SALTOLINEA);

            while(res.next()){
                for(String col : columns){
                    Object val = res.getObject(col);
                    if (val!=null){
                        if (col.equalsIgnoreCase("tri_mosq_rep_typ")){
                           valores += setValuesMultipleField(val.toString(),mosqRepTyp);
                        }else if (col.equalsIgnoreCase("tri_next_visit_time")){ //campo tipo hora HH:mm
                            if (valores.isEmpty()) valores += val.toString().substring(0,5);
                            else valores += SEPARADOR + val.toString().substring(0,5);
                        }else {
                            if (val instanceof String) {
                                //si contiene uno de estos caracteres especiales escapar
                                if (val.toString().contains(SEPARADOR) || val.toString().contains(COMA) || val.toString().contains(SALTOLINEA)) {
                                    valores += SEPARADOR + QUOTE + val.toString() + QUOTE;
                                } else {
                                    if (valores.isEmpty()) valores += val.toString();
                                    else valores += SEPARADOR + val.toString();
                                }
                            } else if (val instanceof Integer) {
                                if (valores.isEmpty()) valores += String.valueOf(res.getInt(col));
                                else valores += SEPARADOR + String.valueOf(res.getInt(col));

                            } else if (val instanceof java.util.Date) {
                                if (valores.isEmpty()) valores += DateToString(res.getDate(col), "dd/MM/yyyyy");
                                else valores += SEPARADOR + DateToString(res.getDate(col), "dd/MM/yyyyy");

                            } else if (val instanceof Float) {
                                if (valores.isEmpty()) valores += String.valueOf(res.getFloat(col));
                                else valores += SEPARADOR + String.valueOf(res.getFloat(col));
                            }
                        }
                    }else{
                        if (col.equalsIgnoreCase("tri_mosq_rep_typ")){
                            for(int i=0;i<mosqRepTyp.length;i++){
                                valores += SEPARADOR;
                            }
                        }else
                            valores += SEPARADOR;
                    }
                }
                //valor para zp04_trimester_visit_section_f_to_h_complete
                valores += SEPARADOR + "1";
                sb.append(valores);
                valores = "";
                sb.append(SALTOLINEA);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }finally {
            if (res !=null) res.close();
            if (pStatement !=null) pStatement.close();
            if (con !=null) con.close();
        }
        return sb;
    }

    public StringBuffer getZp05ExportData(ExportParameters exportParameters) throws Exception{

        StringBuffer sb = new StringBuffer();
        Connection con = getConnection();
        PreparedStatement pStatement = null;
        ResultSet res = null;
        String columnas = "";
        String valores = "";

        try {
            //recuperar los nombres de las columnas
            List<String> columns = getTableMetaData(exportParameters.getTableName());
            columnas = parseColumns(columns);

            //valores campos multiples
            String [] fyesSpecify1 = "1,2,3,4,5".split(",");
            String [] sSpecify1 = "1,2,3,4,5".split(",");

            //pasar a recuperar los datos. Setear parámetro si los hay
            StringBuilder sqlStrBuilder = new StringBuilder();
            sqlStrBuilder.append("select ").append(columnas).append(" from ").append(exportParameters.getTableName());

            if (exportParameters.thereAreValues()) sqlStrBuilder.append(" where record_id between ? and ? ");

            pStatement = con.prepareStatement(sqlStrBuilder.toString());
            if (exportParameters.thereAreValues()){
                pStatement.setString(1, exportParameters.getCodigoInicio());
                pStatement.setString(2, exportParameters.getCodigoFin());
            }

            res = pStatement.executeQuery();

            //Columnas que necesita redcap y no estan en la tabla
            columnas = columnas.replaceAll("ult_id_na","ult_id_na___1");
            columnas = columnas.replaceAll("ult_fcrl_na1","ult_fcrl_na1___1");
            columnas = columnas.replaceAll("ult_fcrl_na2","ult_fcrl_na2___1");
            columnas = columnas.replaceAll("ult_fcrl_na3","ult_fcrl_na3___1");
            columnas = columnas.replaceAll("ult_fyes_specify1","ult_fyes_specify1___1,ult_fyes_specify1___2,ult_fyes_specify1___3,ult_fyes_specify1___4,ult_fyes_specify1___5");
            columnas = columnas.replaceAll("ult_sspecify1","ult_sspecify1___1,ult_sspecify1___2,ult_sspecify1___3,ult_sspecify1___4,ult_sspecify1___5");
            columnas += SEPARADOR + "zp05_ultrasound_exam_complete";

            sb.append(columnas);
            sb.append(SALTOLINEA);

            while(res.next()){
                for(String col : columns){
                    Object val = res.getObject(col);
                    if (val!=null){
                        if (col.equalsIgnoreCase("ult_fyes_specify1")){
                            valores += setValuesMultipleField(val.toString(), fyesSpecify1);
                        }else  if (col.equalsIgnoreCase("ult_sspecify1")){
                            valores += setValuesMultipleField(val.toString(), sSpecify1);
                        }else {
                            if (val instanceof String) {
                                //si contiene uno de estos caracteres especiales escapar
                                if (val.toString().contains(SEPARADOR) || val.toString().contains(COMA) || val.toString().contains(SALTOLINEA)) {
                                    valores += SEPARADOR + QUOTE + val.toString() + QUOTE;
                                } else {
                                    if (valores.isEmpty()) valores += val.toString();
                                    else valores += SEPARADOR + val.toString();
                                }
                            } else if (val instanceof Integer) {
                                if (valores.isEmpty()) valores += String.valueOf(res.getInt(col));
                                else valores += SEPARADOR + String.valueOf(res.getInt(col));

                            } else if (val instanceof java.util.Date) {
                                if (valores.isEmpty()) valores += DateToString(res.getDate(col), "dd/MM/yyyyy");
                                else valores += SEPARADOR + DateToString(res.getDate(col), "dd/MM/yyyyy");

                            } else if (val instanceof Float) {
                                if (valores.isEmpty()) valores += String.valueOf(res.getFloat(col));
                                else valores += SEPARADOR + String.valueOf(res.getFloat(col));
                            }
                        }
                    }else{
                        if (col.equalsIgnoreCase("ult_fyes_specify1")){
                            for(int i=0;i<fyesSpecify1.length;i++){
                                valores += SEPARADOR;
                            }
                        }else  if (col.equalsIgnoreCase("ult_sspecify1")){
                            for(int i=0;i<sSpecify1.length;i++){
                                valores += SEPARADOR;
                            }
                        }else
                            valores += SEPARADOR;
                    }
                }
                //valor para zp05_ultrasound_exam_complete
                valores += SEPARADOR + "1";
                sb.append(valores);
                valores = "";
                sb.append(SALTOLINEA);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }finally {
            if (res !=null) res.close();
            if (pStatement !=null) pStatement.close();
            if (con !=null) con.close();
        }
        return sb;
    }

    public StringBuffer getZp06ExportData(ExportParameters exportParameters) throws Exception{

        StringBuffer sb = new StringBuffer();
        Connection con = getConnection();
        PreparedStatement pStatement = null;
        ResultSet res = null;
        String columnas = "";
        String valores = "";

        try {
            //recuperar los nombres de las columnas
            List<String> columns = getTableMetaData(exportParameters.getTableName());
            columnas = parseColumns(columns);

            //pasar a recuperar los datos. Setear parámetro si los hay
            StringBuilder sqlStrBuilder = new StringBuilder();
            sqlStrBuilder.append("select ").append(columnas).append(" from ").append(exportParameters.getTableName());

            if (exportParameters.thereAreValues()) sqlStrBuilder.append(" where record_id between ? and ? ");

            pStatement = con.prepareStatement(sqlStrBuilder.toString());
            if (exportParameters.thereAreValues()){
                pStatement.setString(1, exportParameters.getCodigoInicio());
                pStatement.setString(2, exportParameters.getCodigoFin());
            }

            res = pStatement.executeQuery();
            //Valores de campos múltiples
            String[] rashFirst = "1,2,3,4,5,6,7,8,9".split(",");
            String[] spreadPart = "1,2,3,4,5,6,7,8,9".split(",");
            String[] specifySymptom = "1,2,3,4,5,6,7,8,9,10,11,12,13".split(",");
            String[] arm = "1,2".split(",");
            String[] leg = "1,2".split(",");
            String[] hand = "1,2".split(",");
            String[] foot = "1,2".split(",");
            String[] face = "1,2".split(",");

            //columnas que necesita redcap y no estan en la tabla
            columnas = columnas.replaceAll("deli_rash_first","deli_rash_first___1,deli_rash_first___2,deli_rash_first___3,deli_rash_first___4,deli_rash_first___5,deli_rash_first___6,deli_rash_first___7,deli_rash_first___8,deli_rash_first___9");
            columnas = columnas.replaceAll("deli_spread_part","deli_spread_part___1,deli_spread_part___2,deli_spread_part___3,deli_spread_part___4,deli_spread_part___5,deli_spread_part___6,deli_spread_part___7,deli_spread_part___8,deli_spread_part___9");
            columnas = columnas.replaceAll("deli_specify_symptom","deli_specify_symptom___1,deli_specify_symptom___2,deli_specify_symptom___3,deli_specify_symptom___4,deli_specify_symptom___5,deli_specify_symptom___6,deli_specify_symptom___7,deli_specify_symptom___8,deli_specify_symptom___9,deli_specify_symptom___10,deli_specify_symptom___11,deli_specify_symptom___12,deli_specify_symptom___13");
            columnas = columnas.replaceAll("deli_tingling_arm","deli_tingling_arm___1,deli_tingling_arm___2");
            columnas = columnas.replaceAll("deli_tingling_leg","deli_tingling_leg___1,deli_tingling_leg___2");
            columnas = columnas.replaceAll("deli_tingling_hand","deli_tingling_hand___1,deli_tingling_hand___2");
            columnas = columnas.replaceAll("deli_tingling_foot","deli_tingling_foot___1,deli_tingling_foot___2");
            columnas = columnas.replaceAll("deli_tingling_face","deli_tingling_face___1,deli_tingling_face___2");
            columnas = columnas.replaceAll("deli_numb_arm","deli_numb_arm___1,deli_numb_arm___2");
            columnas = columnas.replaceAll("deli_numb_leg","deli_numb_leg___1,deli_numb_leg___2");
            columnas = columnas.replaceAll("deli_numb_hand","deli_numb_hand___1,deli_numb_hand___2");
            columnas = columnas.replaceAll("deli_numb_foot","deli_numb_foot___1,deli_numb_foot___2");
            columnas = columnas.replaceAll("deli_numb_face","deli_numb_face___1,deli_numb_face___2");
            columnas = columnas.replaceAll("deli_para_arm","deli_para_arm___1,deli_para_arm___2");
            columnas = columnas.replaceAll("deli_para_leg","deli_para_leg___1,deli_para_leg___2");
            columnas = columnas.replaceAll("deli_para_hand","deli_para_hand___1,deli_para_hand___2");
            columnas = columnas.replaceAll("deli_para_foot","deli_para_foot___1,deli_para_foot___2");
            columnas = columnas.replaceAll("deli_para_face","deli_para_face___1,deli_para_face___2");
            columnas = columnas.replaceAll("deli_tempunknown","deli_tempunknown___1");
            columnas += SEPARADOR + "zp06_delivery_and_6week_visit_complete";

            sb.append(columnas);
            sb.append(SALTOLINEA);

            while(res.next()){
                for(String col : columns){
                    Object val = res.getObject(col);
                    if (val!=null){
                        if (col.equalsIgnoreCase("deli_rash_first")) {
                            valores += setValuesMultipleField(val.toString(), rashFirst);

                        }else if (col.equalsIgnoreCase("deli_spread_part")) {
                            valores += setValuesMultipleField(val.toString(), spreadPart);

                        }else if (col.equalsIgnoreCase("deli_specify_symptom")) {
                            valores += setValuesMultipleField(val.toString(), specifySymptom);

                        }else if (col.equalsIgnoreCase("deli_tingling_arm") || col.equalsIgnoreCase("deli_numb_arm") || col.equalsIgnoreCase("deli_para_arm")){
                            valores += setValuesMultipleField(val.toString(), arm);

                        }else if (col.equalsIgnoreCase("deli_tingling_leg") || col.equalsIgnoreCase("deli_numb_leg") || col.equalsIgnoreCase("deli_para_leg")){
                            valores += setValuesMultipleField(val.toString(), leg);

                        }else if (col.equalsIgnoreCase("deli_tingling_hand") || col.equalsIgnoreCase("deli_numb_hand") || col.equalsIgnoreCase("deli_para_hand")){
                            valores += setValuesMultipleField(val.toString(), hand);

                        }else if (col.equalsIgnoreCase("deli_tingling_foot") || col.equalsIgnoreCase("deli_numb_foot") || col.equalsIgnoreCase("deli_para_foot")){
                            valores += setValuesMultipleField(val.toString(), foot);

                        }else if (col.equalsIgnoreCase("deli_tingling_face") || col.equalsIgnoreCase("deli_numb_face") || col.equalsIgnoreCase("deli_para_face")) {
                            valores += setValuesMultipleField(val.toString(), face);

                        }else {
                            if (val instanceof String) {
                                //si contiene uno de estos caracteres especiales escapar
                                if (val.toString().contains(SEPARADOR) || val.toString().contains(COMA) || val.toString().contains(SALTOLINEA)){
                                    valores += SEPARADOR + QUOTE + val.toString() + QUOTE;
                                }else {
                                    if (valores.isEmpty()) valores += val.toString();
                                    else valores += SEPARADOR + val.toString();
                                }
                            } else if (val instanceof Integer) {
                                if (valores.isEmpty()) valores += String.valueOf(res.getInt(col));
                                else valores += SEPARADOR + String.valueOf(res.getInt(col));

                            } else if (val instanceof java.util.Date) {
                                if (valores.isEmpty()) valores += DateToString(res.getDate(col), "dd/MM/yyyyy");
                                else valores += SEPARADOR + DateToString(res.getDate(col), "dd/MM/yyyyy");

                            } else if (val instanceof Float) {
                                if (valores.isEmpty()) valores += String.valueOf(res.getFloat(col));
                                else valores += SEPARADOR + String.valueOf(res.getFloat(col));
                            }
                        }
                    }else{
                        if (col.equalsIgnoreCase("deli_rash_first")){
                            for(int i = 0 ; i< rashFirst.length; i++){
                                valores += SEPARADOR;
                            }
                        }else if (col.equalsIgnoreCase("deli_spread_part")){
                            for(int i = 0 ; i< spreadPart.length; i++){
                                valores += SEPARADOR;
                            }
                        }else if (col.equalsIgnoreCase("deli_specify_symptom")){
                            for(int i = 0 ; i< specifySymptom.length; i++){
                                valores += SEPARADOR;
                            }
                        } else if (col.equalsIgnoreCase("deli_tingling_arm") || col.equalsIgnoreCase("deli_numb_arm") || col.equalsIgnoreCase("deli_para_arm") ||
                                col.equalsIgnoreCase("deli_tingling_leg") || col.equalsIgnoreCase("deli_numb_leg") || col.equalsIgnoreCase("deli_para_leg") ||
                                col.equalsIgnoreCase("deli_tingling_hand") || col.equalsIgnoreCase("deli_numb_hand") || col.equalsIgnoreCase("deli_para_hand") ||
                                col.equalsIgnoreCase("deli_tingling_foot") || col.equalsIgnoreCase("deli_numb_foot") || col.equalsIgnoreCase("deli_para_foot") ||
                                col.equalsIgnoreCase("deli_tingling_face") || col.equalsIgnoreCase("deli_numb_face") || col.equalsIgnoreCase("deli_para_face")){
                            valores += SEPARADOR + SEPARADOR;
                        }
                        else {
                            valores += SEPARADOR;
                        }
                    }
                }
                //valor para zp06_delivery_and_6week_visit_complete
                valores += SEPARADOR + "1";
                sb.append(valores);
                valores = "";
                sb.append(SALTOLINEA);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }finally {
            if (res !=null) res.close();
            if (pStatement !=null) pStatement.close();
            if (con !=null) con.close();
        }
        return sb;
    }

    public StringBuffer getZp08ExportData(ExportParameters exportParameters) throws Exception{

        StringBuffer sb = new StringBuffer();
        Connection con = getConnection();
        PreparedStatement pStatement = null;
        ResultSet res = null;
        String columnas = "";
        String valores = "";

        try {
            //recuperar los nombres de las columnas
            List<String> columns = getTableMetaData(exportParameters.getTableName());
            columnas = parseColumns(columns);

            //pasar a recuperar los datos. Setear parámetro si los hay
            StringBuilder sqlStrBuilder = new StringBuilder();
            sqlStrBuilder.append("select ").append(columnas).append(" from ").append(exportParameters.getTableName());

            if (exportParameters.thereAreValues()) sqlStrBuilder.append(" where record_id between ? and ? ");

            pStatement = con.prepareStatement(sqlStrBuilder.toString());
            if (exportParameters.thereAreValues()){
                pStatement.setString(1, exportParameters.getCodigoInicio());
                pStatement.setString(2, exportParameters.getCodigoFin());
            }

            res = pStatement.executeQuery();

            //columnas que necesita redcap y no estan en la tabla
            columnas += SEPARADOR + "zp08_study_exit_complete";

            sb.append(columnas);
            sb.append(SALTOLINEA);

            while(res.next()){
                for(String col : columns){
                    Object val = res.getObject(col);
                    if (val!=null){
                        if (val instanceof String) {
                            //si contiene uno de estos caracteres especiales escapar
                            if (val.toString().contains(SEPARADOR) || val.toString().contains(COMA) || val.toString().contains(SALTOLINEA)){
                                valores += SEPARADOR + QUOTE + val.toString() + QUOTE;
                            }else {
                                if (valores.isEmpty()) valores += val.toString();
                                else valores += SEPARADOR + val.toString();
                            }
                        } else if (val instanceof Integer) {
                            if (valores.isEmpty()) valores += String.valueOf(res.getInt(col));
                            else valores += SEPARADOR + String.valueOf(res.getInt(col));

                        } else if (val instanceof java.util.Date) {
                            if (valores.isEmpty()) valores += DateToString(res.getDate(col), "dd/MM/yyyyy");
                            else valores += SEPARADOR + DateToString(res.getDate(col), "dd/MM/yyyyy");

                        } else if (val instanceof Float) {
                            if (valores.isEmpty()) valores += String.valueOf(res.getFloat(col));
                            else valores += SEPARADOR + String.valueOf(res.getFloat(col));
                        }
                    }else{
                        valores += SEPARADOR;

                    }
                }
                //valor para zp08_study_exit_complete
                valores += SEPARADOR + "1";
                sb.append(valores);
                valores = "";
                sb.append(SALTOLINEA);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }finally {
            if (res !=null) res.close();
            if (pStatement !=null) pStatement.close();
            if (con !=null) con.close();
        }
        return sb;
    }

    private static String parseColumns(List<String> columns){
        String columnas = "";
        for(String col : columns){
            if (columnas.isEmpty()) //es primer columna
                columnas+=col;
            else
                columnas += SEPARADOR + col;
        }
        return columnas;
    }

    private static String setValuesMultipleField(String val, String[] valuesField ){
        String[] detalle = val.split(" ");
        String valuesResult = "";
        for(String item : valuesField) {
            String si = "";
            for (String det : detalle) {
                if (item.equalsIgnoreCase(det)) {
                    si = "1";
                    break;
                }
            }
            valuesResult += SEPARADOR + si;
        }
        return valuesResult;
    }

    public static String DateToString(Date dtFecha, String format)  {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(format);
        if(dtFecha!=null)
            return simpleDateFormat.format(dtFecha);
        else
            return null;
    }
}
