package ni.org.ics.zikapositivas.service;

import ni.org.ics.zikapositivas.utils.ConnectionUtil;
import ni.org.ics.zikapositivas.utils.Constants;
import ni.org.ics.zikapositivas.utils.ExportParameters;

import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
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
    private static final String ENTER = "\r\n";
    private static final String QUOTE = "\"";
    private static final String COMILLA = "\"";
    private static final String ESPACIO = " ";

    @Resource(name="sessionFactory")
    private SessionFactory sessionFactory;

    private List<String> getTableMetaData(String tableName) throws Exception{
        Connection con = ConnectionUtil.getConnection();
        List<String> columns = new ArrayList<String>();
        try {
            DatabaseMetaData meta = con.getMetaData();
            ResultSet res = meta.getColumns(null, null, tableName, null);
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
                        !res.getString("COLUMN_NAME").equalsIgnoreCase("prescreen_id") &&
                        !res.getString("COLUMN_NAME").contains("_addt_") //_addt_ se usa para campos adiciones que no son de redacp
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

        Connection con = ConnectionUtil.getConnection();
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
            sqlStrBuilder.append("select ").append(columnas).append(" from ").append(exportParameters.getTableName()).append(" where 1=1 ");

            if (exportParameters.thereAreCodes()) sqlStrBuilder.append(" and record_id between ? and ? ");
            if (!exportParameters.getEvent().equalsIgnoreCase("all")) sqlStrBuilder.append(" and redcap_event_name = ?");

            pStatement = con.prepareStatement(sqlStrBuilder.toString());
            if (exportParameters.thereAreCodes()){
                pStatement.setString(1, exportParameters.getCodigoInicio());
                pStatement.setString(2, exportParameters.getCodigoFin());
            }
            if (!exportParameters.getEvent().equalsIgnoreCase("all")) pStatement.setString(exportParameters.thereAreCodes()?3:1,exportParameters.getEvent());

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
                                String valFormat = val.toString().replaceAll(ENTER,ESPACIO).replaceAll(SALTOLINEA,ESPACIO);
                                //si contiene uno de estos caracteres especiales escapar
                                if (valFormat.contains(SEPARADOR) || valFormat.contains(COMILLA) || valFormat.contains(SALTOLINEA)) {
                                    valores += SEPARADOR + QUOTE + valFormat.trim() + QUOTE;
                                } else {
                                    if (valores.isEmpty()) valores += valFormat.trim();
                                    else valores += SEPARADOR + valFormat.trim();
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
        Connection con = ConnectionUtil.getConnection();
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
            sqlStrBuilder.append("select ").append(columnas).append(" from ").append(exportParameters.getTableName()).append(" where 1=1 ");

            if (exportParameters.thereAreCodes()) sqlStrBuilder.append(" and record_id between ? and ? ");
            if (!exportParameters.getEvent().equalsIgnoreCase("all")) sqlStrBuilder.append(" and redcap_event_name = ?");

            pStatement = con.prepareStatement(sqlStrBuilder.toString());
            if (exportParameters.thereAreCodes()){
                pStatement.setString(1, exportParameters.getCodigoInicio());
                pStatement.setString(2, exportParameters.getCodigoFin());
            }
            if (!exportParameters.getEvent().equalsIgnoreCase("all")) pStatement.setString(exportParameters.thereAreCodes()?3:1,exportParameters.getEvent());

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
                            String valFormat = val.toString().replaceAll(ENTER,ESPACIO).replaceAll(SALTOLINEA,ESPACIO);
                            //si contiene uno de estos caracteres especiales escapar
                            if (valFormat.contains(SEPARADOR) || valFormat.contains(COMILLA) || valFormat.contains(SALTOLINEA)) {
                                valores += SEPARADOR + QUOTE + valFormat.trim() + QUOTE;
                            } else {
                                if (valores.isEmpty()) valores += valFormat.trim();
                                else valores += SEPARADOR + valFormat.trim();
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
        Connection con = ConnectionUtil.getConnection();
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
            sqlStrBuilder.append("select ").append(columnas).append(" from ").append(exportParameters.getTableName()).append(" where 1=1 ");

            if (exportParameters.thereAreCodes()) sqlStrBuilder.append(" and record_id between ? and ? ");
            if (!exportParameters.getEvent().equalsIgnoreCase("all")) sqlStrBuilder.append(" and redcap_event_name = ?");

            pStatement = con.prepareStatement(sqlStrBuilder.toString());
            if (exportParameters.thereAreCodes()){
                pStatement.setString(1, exportParameters.getCodigoInicio());
                pStatement.setString(2, exportParameters.getCodigoFin());
            }
            if (!exportParameters.getEvent().equalsIgnoreCase("all")) pStatement.setString(exportParameters.thereAreCodes()?3:1,exportParameters.getEvent());

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
                                String valFormat = val.toString().replaceAll(ENTER,ESPACIO).replaceAll(SALTOLINEA,ESPACIO);
                                //si contiene uno de estos caracteres especiales escapar
                                if (valFormat.contains(SEPARADOR) || valFormat.contains(COMILLA) || valFormat.contains(SALTOLINEA)) {
                                    valores += SEPARADOR + QUOTE + valFormat.trim() + QUOTE;
                                } else {
                                    if (valores.isEmpty()) valores += valFormat.trim();
                                    else valores += SEPARADOR + valFormat.trim();
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
        Connection con = ConnectionUtil.getConnection();
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
            sqlStrBuilder.append("select ").append(columnas).append(" from ").append(exportParameters.getTableName()).append(" where 1=1 ");

            if (exportParameters.thereAreCodes()) sqlStrBuilder.append(" and record_id between ? and ? ");
            if (!exportParameters.getEvent().equalsIgnoreCase("all")) sqlStrBuilder.append(" and redcap_event_name = ?");

            pStatement = con.prepareStatement(sqlStrBuilder.toString());
            if (exportParameters.thereAreCodes()){
                pStatement.setString(1, exportParameters.getCodigoInicio());
                pStatement.setString(2, exportParameters.getCodigoFin());
            }
            if (!exportParameters.getEvent().equalsIgnoreCase("all")) pStatement.setString(exportParameters.thereAreCodes()?3:1,exportParameters.getEvent());

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
                                String valFormat = val.toString().replaceAll(ENTER,ESPACIO).replaceAll(SALTOLINEA,ESPACIO);
                                //si contiene uno de estos caracteres especiales escapar
                                if (valFormat.contains(SEPARADOR) || valFormat.contains(COMILLA) || valFormat.contains(SALTOLINEA)) {
                                    valores += SEPARADOR + QUOTE + valFormat.trim() + QUOTE;
                                } else {
                                    if (valores.isEmpty()) valores += valFormat.trim();
                                    else valores += SEPARADOR + valFormat.trim();
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
        Connection con = ConnectionUtil.getConnection();
        PreparedStatement pStatement = null;
        ResultSet res = null;
        String columnas = "";
        String valores = "";
        List<String> registros = new ArrayList<String>();

        try {
            //recuperar los nombres de las columnas
            List<String> columns = getTableMetaData(exportParameters.getTableName());
            columnas = parseColumns(columns);

            //pasar a recuperar los datos. Setear parámetro si los hay
            StringBuilder sqlStrBuilder = new StringBuilder();
            sqlStrBuilder.append("select ").append(columnas).append(" from ").append(exportParameters.getTableName()).append(" where redcap_event_name = ? ");
            if (exportParameters.thereAreCodes()) sqlStrBuilder.append(" and record_id between ? and ? ");

            pStatement = con.prepareStatement(sqlStrBuilder.toString());

            pStatement.setString(1,exportParameters.getEvent());
            if (exportParameters.thereAreCodes()){
                pStatement.setString(2, exportParameters.getCodigoInicio());
                pStatement.setString(3, exportParameters.getCodigoFin());
            }


            res = pStatement.executeQuery();

            //Valores de campos múltiples
            String[] bscMatOtherType = "1,4".split(",");

            //Columnas que necesita redcap y no estan en la tabla
            columnas = columnas.replaceFirst("bsc_mat_other_type","bsc_mat_other_type___1,bsc_mat_other_type___4");
            columnas += SEPARADOR + "zp02_biospecimen_collection_complete";

            while(res.next()){
                for(String col : columns){
                    Object val = res.getObject(col);
                    if (val!=null){
                        if (col.equalsIgnoreCase("bsc_mat_other_type")){
                            valores += setValuesMultipleField(val.toString(), bscMatOtherType);
                            //campos tipo hora HH:mm
                        }else if (col.equalsIgnoreCase("bsc_mat_bld_time") || col.equalsIgnoreCase("bsc_mat_slva_time") || col.equalsIgnoreCase("bsc_mat_vag_time") || col.equalsIgnoreCase("bsc_mat_vst_urn_time") || col.equalsIgnoreCase("bsc_mat_amf_time")
                                || col.equalsIgnoreCase("bsc_mat_cord_time") || col.equalsIgnoreCase("bsc_mat_placen_time") || col.equalsIgnoreCase("bsc_mat_breastm_time") || col.equalsIgnoreCase("bsc_mat_fetalt_time") || col.equalsIgnoreCase("bsc_matd_breastm_time")){
                            if (valores.isEmpty()) valores += val.toString().substring(0,5);
                            else valores += SEPARADOR + val.toString().substring(0,5);

                        } else if (col.equalsIgnoreCase("bsc_mat_hom_urn_col") || col.equalsIgnoreCase("bsc_mat_hom_urn_rsn") || col.equalsIgnoreCase("bsc_mat_hom_urn_specify") || col.equalsIgnoreCase("bsc_mat_hom_urn_num") ||
                                col.equalsIgnoreCase("bsc_mat_hom_urn_id1") || col.equalsIgnoreCase("bsc_mat_hom_urn_dat1") || col.equalsIgnoreCase("bsc_mat_hom_urn_time1") || col.equalsIgnoreCase("bsc_mat_hom_urn_com1") ||
                                col.equalsIgnoreCase("bsc_mat_hom_urn_id2") || col.equalsIgnoreCase("bsc_mat_hom_urn_dat2") || col.equalsIgnoreCase("bsc_mat_hom_urn_time2") || col.equalsIgnoreCase("bsc_mat_hom_urn_com2") ||
                                col.equalsIgnoreCase("bsc_mat_hom_urn_id3") || col.equalsIgnoreCase("bsc_mat_hom_urn_dat3") || col.equalsIgnoreCase("bsc_mat_hom_urn_time3") || col.equalsIgnoreCase("bsc_mat_hom_urn_com3") ||
                                col.equalsIgnoreCase("bsc_mat_hom_urn_id4") || col.equalsIgnoreCase("bsc_mat_hom_urn_dat4") || col.equalsIgnoreCase("bsc_mat_hom_urn_time4") || col.equalsIgnoreCase("bsc_mat_hom_urn_com4")) {
                            //para las muestras de horina en casa tomar los valores registrados en la visita quincenal previa (solo para los eventos mensuales)
                            if (exportParameters.getEvent().contains("week_post_entry_arm_1")) {
                                if (valores.isEmpty()) valores += col;
                                else valores += SEPARADOR + col;
                            } else {
                                if (col.equalsIgnoreCase("bsc_mat_hom_urn_time1") || col.equalsIgnoreCase("bsc_mat_hom_urn_time2") || col.equalsIgnoreCase("bsc_mat_hom_urn_time3") || col.equalsIgnoreCase("bsc_mat_hom_urn_time4")) {
                                    if (valores.isEmpty()) valores += val.toString().substring(0, 5);
                                    else valores += SEPARADOR + val.toString().substring(0, 5);
                                } else if (col.equalsIgnoreCase("bsc_mat_hom_urn_dat1") || col.equalsIgnoreCase("bsc_mat_hom_urn_dat2") || col.equalsIgnoreCase("bsc_mat_hom_urn_dat3") || col.equalsIgnoreCase("bsc_mat_hom_urn_dat4")) {
                                    if (valores.isEmpty())
                                        valores += DateToString(res.getDate(col), "dd/MM/yyyyy");
                                    else
                                        valores += SEPARADOR + DateToString(res.getDate(col), "dd/MM/yyyyy");
                                } else {
                                    //si contiene uno de estos caracteres especiales escapar
                                    if (val.toString().contains(SEPARADOR) || val.toString().contains(COMILLA) || val.toString().contains(SALTOLINEA)) {
                                        valores += SEPARADOR + QUOTE + val.toString() + QUOTE;
                                    } else {
                                        if (valores.isEmpty()) valores += val.toString();
                                        else valores += SEPARADOR + val.toString();
                                    }
                                }
                            }
                            //defecto
                        } else {
                            if (val instanceof String) {
                                String valFormat = val.toString().replaceAll(ENTER,ESPACIO).replaceAll(SALTOLINEA,ESPACIO);
                                //si contiene uno de estos caracteres especiales escapar
                                if (valFormat.contains(SEPARADOR) || valFormat.contains(COMILLA) || valFormat.contains(SALTOLINEA)) {
                                    valores += SEPARADOR + QUOTE + valFormat.trim() + QUOTE;
                                } else {
                                    if (valores.isEmpty()) valores += valFormat.trim();
                                    else valores += SEPARADOR + valFormat.trim();
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
                        } else if (exportParameters.getEvent().contains("week_post_entry_arm_1") && (col.equalsIgnoreCase("bsc_mat_hom_urn_col") || col.equalsIgnoreCase("bsc_mat_hom_urn_rsn") || col.equalsIgnoreCase("bsc_mat_hom_urn_specify") || col.equalsIgnoreCase("bsc_mat_hom_urn_num") ||
                                col.equalsIgnoreCase("bsc_mat_hom_urn_id1") || col.equalsIgnoreCase("bsc_mat_hom_urn_dat1") || col.equalsIgnoreCase("bsc_mat_hom_urn_time1") || col.equalsIgnoreCase("bsc_mat_hom_urn_com1") ||
                                col.equalsIgnoreCase("bsc_mat_hom_urn_id2") || col.equalsIgnoreCase("bsc_mat_hom_urn_dat2") || col.equalsIgnoreCase("bsc_mat_hom_urn_time2") || col.equalsIgnoreCase("bsc_mat_hom_urn_com2") ||
                                col.equalsIgnoreCase("bsc_mat_hom_urn_id3") || col.equalsIgnoreCase("bsc_mat_hom_urn_dat3") || col.equalsIgnoreCase("bsc_mat_hom_urn_time3") || col.equalsIgnoreCase("bsc_mat_hom_urn_com3") ||
                                col.equalsIgnoreCase("bsc_mat_hom_urn_id4") || col.equalsIgnoreCase("bsc_mat_hom_urn_dat4") || col.equalsIgnoreCase("bsc_mat_hom_urn_time4") || col.equalsIgnoreCase("bsc_mat_hom_urn_com4"))) {
                            //para las muestras de horina en casa tomar los valores registrados en la visita quincenal previa (solo para los eventos mensuales)
                            if (valores.isEmpty()) valores += col;
                            else valores += SEPARADOR + col;

                        }else {
                            valores += SEPARADOR;
                        }
                    }
                }
                //valor para zp02_biospecimen_collection_complete
                valores += SEPARADOR + "1";
                //sb.append(valores);
                registros.add(valores);
                valores = "";
                //sb.append(SALTOLINEA);
            }

            /*completar campos para tomas de muestras de orina en la casa que se toman 15 dias +- antes de los eventos mensuales
            (solo para los eventos mensuales desde semana 4 hasta semana 44) */
            if (exportParameters.getEvent().contains("week_post_entry_arm_1")) {
                int semana = Integer.valueOf(exportParameters.getEvent().substring(0, exportParameters.getEvent().indexOf("_")));
                String evento15dias = String.valueOf(semana - 2) + "_week_post_entry_arm_1";
                String sqlOrinaCasa = "select record_id,bsc_mat_hom_urn_col,bsc_mat_hom_urn_rsn,bsc_mat_hom_urn_specify,bsc_mat_hom_urn_num,bsc_mat_hom_urn_id1,bsc_mat_hom_urn_dat1,bsc_mat_hom_urn_time1,bsc_mat_hom_urn_com1," +
                        "bsc_mat_hom_urn_id2,bsc_mat_hom_urn_dat2,bsc_mat_hom_urn_time2,bsc_mat_hom_urn_com2,bsc_mat_hom_urn_id3,bsc_mat_hom_urn_dat3,bsc_mat_hom_urn_time3,bsc_mat_hom_urn_com3," +
                        "bsc_mat_hom_urn_id4,bsc_mat_hom_urn_dat4,bsc_mat_hom_urn_time4,bsc_mat_hom_urn_com4 from " + Constants.TABLE_ZP02 + " where redcap_event_name = ?";
                if (exportParameters.thereAreCodes()) sqlOrinaCasa += " and record_id between ? and ? ";
                pStatement = con.prepareStatement(sqlOrinaCasa);
                pStatement.setString(1, evento15dias);
                if (exportParameters.thereAreCodes()) {
                    pStatement.setString(2, exportParameters.getCodigoInicio());
                    pStatement.setString(3, exportParameters.getCodigoFin());
                }
                res = pStatement.executeQuery();
                while (res.next()) {
                    int indice = 0;
                    String registrotmp = "";
                    for (String registro : registros) {
                        String codparticipante = registro.substring(0, registro.indexOf(SEPARADOR));
                        if (codparticipante.equalsIgnoreCase(res.getString("record_id"))) {
                            registrotmp = registro;
                            registrotmp = registrotmp.replaceAll("bsc_mat_hom_urn_col", (res.getObject("bsc_mat_hom_urn_col") != null ? res.getString("bsc_mat_hom_urn_col") : ""));
                            registrotmp = registrotmp.replaceAll("bsc_mat_hom_urn_rsn", (res.getObject("bsc_mat_hom_urn_rsn") != null ? res.getString("bsc_mat_hom_urn_rsn") : ""));
                            registrotmp = registrotmp.replaceAll("bsc_mat_hom_urn_specify", (res.getObject("bsc_mat_hom_urn_specify") != null ? res.getString("bsc_mat_hom_urn_specify") : ""));
                            registrotmp = registrotmp.replaceAll("bsc_mat_hom_urn_num", (res.getObject("bsc_mat_hom_urn_num") != null ? res.getString("bsc_mat_hom_urn_num") : ""));
                            registrotmp = registrotmp.replaceAll("bsc_mat_hom_urn_id1", (res.getObject("bsc_mat_hom_urn_id1") != null ? res.getString("bsc_mat_hom_urn_id1") : ""));
                            registrotmp = registrotmp.replaceAll("bsc_mat_hom_urn_dat1", (res.getObject("bsc_mat_hom_urn_dat1") != null ? DateToString(res.getDate("bsc_mat_hom_urn_dat1"), "dd/MM/yyyyy") : ""));
                            registrotmp = registrotmp.replaceAll("bsc_mat_hom_urn_time1", (res.getObject("bsc_mat_hom_urn_time1") != null ? res.getString("bsc_mat_hom_urn_time1").substring(0, 5) : ""));
                            registrotmp = registrotmp.replaceAll("bsc_mat_hom_urn_com1", (res.getObject("bsc_mat_hom_urn_com1") != null ? res.getString("bsc_mat_hom_urn_com1") : ""));
                            registrotmp = registrotmp.replaceAll("bsc_mat_hom_urn_id2", (res.getObject("bsc_mat_hom_urn_id2") != null ? res.getString("bsc_mat_hom_urn_id2") : ""));
                            registrotmp = registrotmp.replaceAll("bsc_mat_hom_urn_dat2", (res.getObject("bsc_mat_hom_urn_dat2") != null ? DateToString(res.getDate("bsc_mat_hom_urn_dat2"), "dd/MM/yyyyy") : ""));
                            registrotmp = registrotmp.replaceAll("bsc_mat_hom_urn_time2", (res.getObject("bsc_mat_hom_urn_time2") != null ? res.getString("bsc_mat_hom_urn_time2").substring(0, 5) : ""));
                            registrotmp = registrotmp.replaceAll("bsc_mat_hom_urn_com2", (res.getObject("bsc_mat_hom_urn_com2") != null ? res.getString("bsc_mat_hom_urn_com2") : ""));
                            registrotmp = registrotmp.replaceAll("bsc_mat_hom_urn_id3", (res.getObject("bsc_mat_hom_urn_id3") != null ? res.getString("bsc_mat_hom_urn_id3") : ""));
                            registrotmp = registrotmp.replaceAll("bsc_mat_hom_urn_dat3", (res.getObject("bsc_mat_hom_urn_dat3") != null ? DateToString(res.getDate("bsc_mat_hom_urn_dat3"), "dd/MM/yyyyy") : ""));
                            registrotmp = registrotmp.replaceAll("bsc_mat_hom_urn_time3", (res.getObject("bsc_mat_hom_urn_time3") != null ? res.getString("bsc_mat_hom_urn_time3").substring(0, 5) : ""));
                            registrotmp = registrotmp.replaceAll("bsc_mat_hom_urn_com3", (res.getObject("bsc_mat_hom_urn_com3") != null ? res.getString("bsc_mat_hom_urn_com3") : ""));
                            registrotmp = registrotmp.replaceAll("bsc_mat_hom_urn_id4", (res.getObject("bsc_mat_hom_urn_id4") != null ? res.getString("bsc_mat_hom_urn_id4") : ""));
                            registrotmp = registrotmp.replaceAll("bsc_mat_hom_urn_dat4", (res.getObject("bsc_mat_hom_urn_dat4") != null ? DateToString(res.getDate("bsc_mat_hom_urn_dat4"), "dd/MM/yyyyy") : ""));
                            registrotmp = registrotmp.replaceAll("bsc_mat_hom_urn_time4", (res.getObject("bsc_mat_hom_urn_time4") != null ? res.getString("bsc_mat_hom_urn_time4").substring(0, 5) : ""));
                            registrotmp = registrotmp.replaceAll("bsc_mat_hom_urn_com4", (res.getObject("bsc_mat_hom_urn_com4") != null ? res.getString("bsc_mat_hom_urn_com4") : ""));
                            break;
                        }
                        indice++;
                    }
                    if (!registrotmp.isEmpty()) {
                        registros.set(indice, registrotmp);
                    }
                }
            }/*FIN completar campos para tomas de muestras de orina en la casa*/

            if (exportParameters.isAddHeader()) {
                sb.append(columnas);
            }
            for (String registro : registros){
                sb.append(SALTOLINEA);
                //por si queda algun registro que no tiene toma de muestra 15 dias, se limpia
                registro = registro.replaceAll("bsc_mat_hom_urn_col", "");
                registro = registro.replaceAll("bsc_mat_hom_urn_rsn", "");
                registro = registro.replaceAll("bsc_mat_hom_urn_specify", "");
                registro = registro.replaceAll("bsc_mat_hom_urn_num", "");
                registro = registro.replaceAll("bsc_mat_hom_urn_id1", "");
                registro = registro.replaceAll("bsc_mat_hom_urn_dat1", "");
                registro = registro.replaceAll("bsc_mat_hom_urn_time1", "");
                registro = registro.replaceAll("bsc_mat_hom_urn_com1", "");
                registro = registro.replaceAll("bsc_mat_hom_urn_id2", "");
                registro = registro.replaceAll("bsc_mat_hom_urn_dat2", "");
                registro = registro.replaceAll("bsc_mat_hom_urn_time2", "");
                registro = registro.replaceAll("bsc_mat_hom_urn_com2", "");
                registro = registro.replaceAll("bsc_mat_hom_urn_id3", "");
                registro = registro.replaceAll("bsc_mat_hom_urn_dat3", "");
                registro = registro.replaceAll("bsc_mat_hom_urn_time3", "");
                registro = registro.replaceAll("bsc_mat_hom_urn_com3", "");
                registro = registro.replaceAll("bsc_mat_hom_urn_id4", "");
                registro = registro.replaceAll("bsc_mat_hom_urn_dat4", "");
                registro = registro.replaceAll("bsc_mat_hom_urn_time4", "");
                registro = registro.replaceAll("bsc_mat_hom_urn_com4", "");
                sb.append(registro);
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
        Connection con = ConnectionUtil.getConnection();
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
            sqlStrBuilder.append("select ").append(columnas).append(" from ").append(exportParameters.getTableName()).append(" where 1=1 ");

            if (exportParameters.thereAreCodes()) sqlStrBuilder.append(" and record_id between ? and ? ");
            if (!exportParameters.getEvent().equalsIgnoreCase("all")) sqlStrBuilder.append(" and redcap_event_name = ?");

            pStatement = con.prepareStatement(sqlStrBuilder.toString());
            if (exportParameters.thereAreCodes()){
                pStatement.setString(1, exportParameters.getCodigoInicio());
                pStatement.setString(2, exportParameters.getCodigoFin());
            }
            if (!exportParameters.getEvent().equalsIgnoreCase("all")) pStatement.setString(exportParameters.thereAreCodes()?3:1,exportParameters.getEvent());

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
            columnas = columnas.replaceAll("mon_charac_discharge","mon_charac_discharge___1,mon_charac_discharge___2,mon_charac_discharge___3,mon_charac_discharge___4,mon_charac_discharge___5,mon_charac_discharge___6");
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
                        if (col.equalsIgnoreCase("mon_charac_discharge")){
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
                                String valFormat = val.toString().replaceAll(ENTER,ESPACIO).replaceAll(SALTOLINEA,ESPACIO);
                                //si contiene uno de estos caracteres especiales escapar
                                if (valFormat.contains(SEPARADOR) || valFormat.contains(COMILLA) || valFormat.contains(SALTOLINEA)) {
                                    valores += SEPARADOR + QUOTE + valFormat.trim() + QUOTE;
                                } else {
                                    if (valores.isEmpty()) valores += valFormat.trim();
                                    else valores += SEPARADOR + valFormat.trim();
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
                        if (col.equalsIgnoreCase("mon_charac_discharge")){
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
        Connection con = ConnectionUtil.getConnection();
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
            sqlStrBuilder.append("select ").append(columnas).append(" from ").append(exportParameters.getTableName()).append(" where 1=1 ");

            if (exportParameters.thereAreCodes()) sqlStrBuilder.append(" and record_id between ? and ? ");
            if (!exportParameters.getEvent().equalsIgnoreCase("all")) sqlStrBuilder.append(" and redcap_event_name = ?");

            pStatement = con.prepareStatement(sqlStrBuilder.toString());
            if (exportParameters.thereAreCodes()){
                pStatement.setString(1, exportParameters.getCodigoInicio());
                pStatement.setString(2, exportParameters.getCodigoFin());
            }
            if (!exportParameters.getEvent().equalsIgnoreCase("all")) pStatement.setString(exportParameters.thereAreCodes()?3:1,exportParameters.getEvent());

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
                                String valFormat = val.toString().replaceAll(ENTER,ESPACIO).replaceAll(SALTOLINEA,ESPACIO);
                                //si contiene uno de estos caracteres especiales escapar
                                if (valFormat.contains(SEPARADOR) || valFormat.contains(COMILLA) || valFormat.contains(SALTOLINEA)) {
                                    valores += SEPARADOR + QUOTE + valFormat.trim() + QUOTE;
                                } else {
                                    if (valores.isEmpty()) valores += valFormat.trim();
                                    else valores += SEPARADOR + valFormat.trim();
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
        Connection con = ConnectionUtil.getConnection();
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
            sqlStrBuilder.append("select ").append(columnas).append(" from ").append(exportParameters.getTableName()).append(" where 1=1 ");

            if (exportParameters.thereAreCodes()) sqlStrBuilder.append(" and record_id between ? and ? ");
            if (!exportParameters.getEvent().equalsIgnoreCase("all")) sqlStrBuilder.append(" and redcap_event_name = ?");

            pStatement = con.prepareStatement(sqlStrBuilder.toString());
            if (exportParameters.thereAreCodes()){
                pStatement.setString(1, exportParameters.getCodigoInicio());
                pStatement.setString(2, exportParameters.getCodigoFin());
            }
            if (!exportParameters.getEvent().equalsIgnoreCase("all")) pStatement.setString(exportParameters.thereAreCodes()?3:1,exportParameters.getEvent());

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
                            String valFormat = val.toString().replaceAll(ENTER,ESPACIO).replaceAll(SALTOLINEA,ESPACIO);
                            //si contiene uno de estos caracteres especiales escapar
                            if (valFormat.contains(SEPARADOR) || valFormat.contains(COMILLA) || valFormat.contains(SALTOLINEA)) {
                                valores += SEPARADOR + QUOTE + valFormat.trim() + QUOTE;
                            } else {
                                if (valores.isEmpty()) valores += valFormat.trim();
                                else valores += SEPARADOR + valFormat.trim();
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
        Connection con = ConnectionUtil.getConnection();
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
            sqlStrBuilder.append("select ").append(columnas).append(" from ").append(exportParameters.getTableName()).append(" where 1=1 ");

            if (exportParameters.thereAreCodes()) sqlStrBuilder.append(" and record_id between ? and ? ");
            if (!exportParameters.getEvent().equalsIgnoreCase("all")) sqlStrBuilder.append(" and redcap_event_name = ?");

            pStatement = con.prepareStatement(sqlStrBuilder.toString());
            if (exportParameters.thereAreCodes()){
                pStatement.setString(1, exportParameters.getCodigoInicio());
                pStatement.setString(2, exportParameters.getCodigoFin());
            }
            if (!exportParameters.getEvent().equalsIgnoreCase("all")) pStatement.setString(exportParameters.thereAreCodes()?3:1,exportParameters.getEvent());

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
                                String valFormat = val.toString().replaceAll(ENTER,ESPACIO).replaceAll(SALTOLINEA,ESPACIO);
                                //si contiene uno de estos caracteres especiales escapar
                                if (valFormat.contains(SEPARADOR) || valFormat.contains(COMILLA) || valFormat.contains(SALTOLINEA)) {
                                    valores += SEPARADOR + QUOTE + valFormat.trim() + QUOTE;
                                } else {
                                    if (valores.isEmpty()) valores += valFormat.trim();
                                    else valores += SEPARADOR + valFormat.trim();
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
        Connection con = ConnectionUtil.getConnection();
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
            sqlStrBuilder.append("select ").append(columnas).append(" from ").append(exportParameters.getTableName()).append(" where 1=1 ");

            if (exportParameters.thereAreCodes()) sqlStrBuilder.append(" and record_id between ? and ? ");
            if (!exportParameters.getEvent().equalsIgnoreCase("all")) sqlStrBuilder.append(" and redcap_event_name = ?");

            pStatement = con.prepareStatement(sqlStrBuilder.toString());
            if (exportParameters.thereAreCodes()){
                pStatement.setString(1, exportParameters.getCodigoInicio());
                pStatement.setString(2, exportParameters.getCodigoFin());
            }
            if (!exportParameters.getEvent().equalsIgnoreCase("all")) pStatement.setString(exportParameters.thereAreCodes()?3:1,exportParameters.getEvent());

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
                                String valFormat = val.toString().replaceAll(ENTER,ESPACIO).replaceAll(SALTOLINEA,ESPACIO);
                                //si contiene uno de estos caracteres especiales escapar
                                if (valFormat.contains(SEPARADOR) || valFormat.contains(COMILLA) || valFormat.contains(SALTOLINEA)) {
                                    valores += SEPARADOR + QUOTE + valFormat.trim() + QUOTE;
                                } else {
                                    if (valores.isEmpty()) valores += valFormat.trim();
                                    else valores += SEPARADOR + valFormat.trim();
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
        Connection con = ConnectionUtil.getConnection();
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
            sqlStrBuilder.append("select ").append(columnas).append(" from ").append(exportParameters.getTableName()).append(" where 1=1 ");

            if (exportParameters.thereAreCodes()) sqlStrBuilder.append(" and record_id between ? and ? ");
            if (!exportParameters.getEvent().equalsIgnoreCase("all")) sqlStrBuilder.append(" and redcap_event_name = ?");

            pStatement = con.prepareStatement(sqlStrBuilder.toString());
            if (exportParameters.thereAreCodes()){
                pStatement.setString(1, exportParameters.getCodigoInicio());
                pStatement.setString(2, exportParameters.getCodigoFin());
            }
            if (!exportParameters.getEvent().equalsIgnoreCase("all")) pStatement.setString(exportParameters.thereAreCodes()?3:1,exportParameters.getEvent());

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
                                String valFormat = val.toString().replaceAll(ENTER,ESPACIO).replaceAll(SALTOLINEA,ESPACIO);
                                //si contiene uno de estos caracteres especiales escapar
                                if (valFormat.contains(SEPARADOR) || valFormat.contains(COMILLA) || valFormat.contains(SALTOLINEA)) {
                                    valores += SEPARADOR + QUOTE + valFormat.trim() + QUOTE;
                                } else {
                                    if (valores.isEmpty()) valores += valFormat.trim();
                                    else valores += SEPARADOR + valFormat.trim();
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
        Connection con = ConnectionUtil.getConnection();
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
            sqlStrBuilder.append("select ").append(columnas).append(" from ").append(exportParameters.getTableName()).append(" where 1=1 ");

            if (exportParameters.thereAreCodes()) sqlStrBuilder.append(" and record_id between ? and ? ");
            if (!exportParameters.getEvent().equalsIgnoreCase("all")) sqlStrBuilder.append(" and redcap_event_name = ?");

            pStatement = con.prepareStatement(sqlStrBuilder.toString());
            if (exportParameters.thereAreCodes()){
                pStatement.setString(1, exportParameters.getCodigoInicio());
                pStatement.setString(2, exportParameters.getCodigoFin());
            }
            if (!exportParameters.getEvent().equalsIgnoreCase("all")) pStatement.setString(exportParameters.thereAreCodes()?3:1,exportParameters.getEvent());

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
                            String valFormat = val.toString().replaceAll(ENTER,ESPACIO).replaceAll(SALTOLINEA,ESPACIO);
                            //si contiene uno de estos caracteres especiales escapar
                            if (valFormat.contains(SEPARADOR) || valFormat.contains(COMILLA) || valFormat.contains(SALTOLINEA)) {
                                valores += SEPARADOR + QUOTE + valFormat.trim() + QUOTE;
                            } else {
                                if (valores.isEmpty()) valores += valFormat.trim();
                                else valores += SEPARADOR + valFormat.trim();
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

    public StringBuffer getZp07ExportData(ExportParameters exportParameters) throws Exception{

        StringBuffer sb = new StringBuffer();
        Connection con = ConnectionUtil.getConnection();
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
            sqlStrBuilder.append("select ").append(columnas).append(" from ").append(exportParameters.getTableName()).append(" where 1=1 ");

            if (exportParameters.thereAreCodes()) sqlStrBuilder.append(" and record_id between ? and ? ");
            if (!exportParameters.getEvent().equalsIgnoreCase("all")) sqlStrBuilder.append(" and redcap_event_name = ?");

            pStatement = con.prepareStatement(sqlStrBuilder.toString());
            if (exportParameters.thereAreCodes()){
                pStatement.setString(1, exportParameters.getCodigoInicio());
                pStatement.setString(2, exportParameters.getCodigoFin());
            }
            if (!exportParameters.getEvent().equalsIgnoreCase("all")) pStatement.setString(exportParameters.thereAreCodes()?3:1,exportParameters.getEvent());

            res = pStatement.executeQuery();

            //Valores de campos múltiples
            String[] whichEye = "1,2".split(",");
            String[] otherIssue = "1,2,3,4,5,6,7,8,9".split(",");
            String[] whichEar = "1,2".split(",");
            String[] breastReason = "1,2,3,4,5,6".split(",");
            String[] neurodeveType = "1,2,3,4,5,6".split(",");
            String[] exhibited = "1,2,3,4,5,6,7,8,9,10".split(",");

            //columnas que necesita redcap y no estan en la tabla
            columnas = columnas.replaceAll("infant_which_eye","infant_which_eye___1,infant_which_eye___2");
            columnas = columnas.replaceAll("infant_other_issue","infant_other_issue___1,infant_other_issue___2,infant_other_issue___3,infant_other_issue___4,infant_other_issue___5,infant_other_issue___6,infant_other_issue___7,infant_other_issue___8,infant_other_issue___9");
            columnas = columnas.replaceAll("infant_which_ear","infant_which_ear___1,infant_which_ear___2");
            columnas = columnas.replaceAll("infant_breast_reason","infant_breast_reason___1,infant_breast_reason___2,infant_breast_reason___3,infant_breast_reason___4,infant_breast_reason___5,infant_breast_reason___6");
            columnas = columnas.replaceAll("infant_neurodeve_type","infant_neurodeve_type___1,infant_neurodeve_type___2,infant_neurodeve_type___3,infant_neurodeve_type___4,infant_neurodeve_type___5,infant_neurodeve_type___6");
            columnas = columnas.replaceAll("infant_exhibited","infant_exhibited___1,infant_exhibited___2,infant_exhibited___3,infant_exhibited___4,infant_exhibited___5,infant_exhibited___6,infant_exhibited___7,infant_exhibited___8,infant_exhibited___9,infant_exhibited___10");
            columnas = columnas.replaceAll("infant_wtpercen_na","infant_wtpercen_na___99");
            columnas = columnas.replaceAll("infant_lenpercen_na","infant_lenpercen_na___99");
            columnas = columnas.replaceAll("infant_heapercen_na","infant_heapercen_na___99");
            columnas = columnas.replaceAll("infant_apgar_na","infant_apgar_na___99");


            //columnas que necesita redcap y no estan en la tabla
            columnas += SEPARADOR + "zp07_infant_assessment_complete";

            sb.append(columnas);
            sb.append(SALTOLINEA);

            while(res.next()){
                for(String col : columns){
                    Object val = res.getObject(col);
                    if (val!=null){
                        if (col.equalsIgnoreCase("infant_which_eye")) {
                            valores += setValuesMultipleField(val.toString(), whichEye);

                        }else if (col.equalsIgnoreCase("infant_other_issue")) {
                            valores += setValuesMultipleField(val.toString(), otherIssue);

                        }else if (col.equalsIgnoreCase("infant_which_ear")) {
                            valores += setValuesMultipleField(val.toString(), whichEar);

                        }else if (col.equalsIgnoreCase("infant_breast_reason")) {
                            valores += setValuesMultipleField(val.toString(), breastReason);

                        }else if (col.equalsIgnoreCase("infant_neurodeve_type")) {
                            valores += setValuesMultipleField(val.toString(), neurodeveType);

                        }else if (col.equalsIgnoreCase("infant_exhibited")) {
                            valores += setValuesMultipleField(val.toString(), exhibited);

                        }else {
                            if (val instanceof String) {
                                String valFormat = val.toString().replaceAll(ENTER, ESPACIO).replaceAll(SALTOLINEA, ESPACIO);
                                //si contiene uno de estos caracteres especiales escapar
                                if (valFormat.contains(SEPARADOR) || valFormat.contains(COMILLA) || valFormat.contains(SALTOLINEA)) {
                                    valores += SEPARADOR + QUOTE + valFormat.trim() + QUOTE;
                                } else {
                                    if (valores.isEmpty()) valores += valFormat.trim();
                                    else valores += SEPARADOR + valFormat.trim();
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
                        if (col.equalsIgnoreCase("infant_which_eye")){
                            for(int i = 0 ; i< whichEye.length; i++){
                                valores += SEPARADOR;
                            }
                        }else if (col.equalsIgnoreCase("infant_other_issue")){
                            for(int i = 0 ; i< otherIssue.length; i++){
                                valores += SEPARADOR;
                            }
                        }else if (col.equalsIgnoreCase("infant_which_ear")){
                            for(int i = 0 ; i< whichEar.length; i++){
                                valores += SEPARADOR;
                            }
                        }else if (col.equalsIgnoreCase("infant_breast_reason")){
                            for(int i = 0 ; i< breastReason.length; i++){
                                valores += SEPARADOR;
                            }
                        }else if (col.equalsIgnoreCase("infant_neurodeve_type")){
                            for(int i = 0 ; i< neurodeveType.length; i++){
                                valores += SEPARADOR;
                            }
                        }else if (col.equalsIgnoreCase("infant_exhibited")){
                            for(int i = 0 ; i< exhibited.length; i++){
                                valores += SEPARADOR;
                            }
                        }else {
                            valores += SEPARADOR;
                        }

                    }
                }
                //valor para zp07_infant_assessment_complete
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

    public StringBuffer getZp02dExportData(ExportParameters exportParameters) throws Exception{

        StringBuffer sb = new StringBuffer();
        Connection con = ConnectionUtil.getConnection();
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
            sqlStrBuilder.append("select ").append(columnas).append(" from ").append(exportParameters.getTableName()).append(" where 1=1 ");

            if (exportParameters.thereAreCodes()) sqlStrBuilder.append(" and record_id between ? and ? ");
            if (!exportParameters.getEvent().equalsIgnoreCase("all")) sqlStrBuilder.append(" and redcap_event_name = ?");

            pStatement = con.prepareStatement(sqlStrBuilder.toString());
            if (exportParameters.thereAreCodes()){
                pStatement.setString(1, exportParameters.getCodigoInicio());
                pStatement.setString(2, exportParameters.getCodigoFin());
            }
            if (!exportParameters.getEvent().equalsIgnoreCase("all")) pStatement.setString(exportParameters.thereAreCodes()?3:1,exportParameters.getEvent());

            res = pStatement.executeQuery();

            //columnas que necesita redcap y no estan en la tabla
            columnas += SEPARADOR + "zp02d_infant_biospecimen_collection_complete";

            sb.append(columnas);
            sb.append(SALTOLINEA);

            while(res.next()){
                for(String col : columns){
                    Object val = res.getObject(col);
                    if (val!=null){
                        if (col.equalsIgnoreCase("infant_mat_bld_time") || col.equalsIgnoreCase("infant_mat_slva_time") || col.equalsIgnoreCase("infant_mat_vst_urn_time")){ //campo tipo hora HH:mm
                            if (valores.isEmpty()) valores += val.toString().substring(0,5);
                            else valores += SEPARADOR + val.toString().substring(0,5);
                        }else {
                            if (val instanceof String) {
                                String valFormat = val.toString().replaceAll(ENTER, ESPACIO).replaceAll(SALTOLINEA, ESPACIO);
                                //si contiene uno de estos caracteres especiales escapar
                                if (valFormat.contains(SEPARADOR) || valFormat.contains(COMILLA) || valFormat.contains(SALTOLINEA)) {
                                    valores += SEPARADOR + QUOTE + valFormat.trim() + QUOTE;
                                } else {
                                    if (valores.isEmpty()) valores += valFormat.trim();
                                    else valores += SEPARADOR + valFormat.trim();
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
                //valor para zp02d_infant_biospecimen_collection_complete
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

    private List<String[]> getAllTableMetaData(String[] tableNames) throws Exception{
        Connection con = ConnectionUtil.getConnection();
        List<String[]> columns = new ArrayList<String[]>();
        try {
            DatabaseMetaData meta = con.getMetaData();
            for(String tableName : tableNames) {
                ResultSet res = meta.getColumns(null, null, tableName, null);
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
                            !res.getString("COLUMN_NAME").equalsIgnoreCase("prescreen_id") &&
                            !res.getString("COLUMN_NAME").equalsIgnoreCase("record_id") &&
                            !res.getString("COLUMN_NAME").equalsIgnoreCase("redcap_event_name") &&
                            !res.getString("COLUMN_NAME").contains("_addt_") //_addt_ se usa para campos adicionales que no son de redacp
                            ) {

                        String[] columna = {res.getString("TABLE_NAME"),res.getString("COLUMN_NAME")};
                        columns.add(columna);

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

    public StringBuffer getAllExportData(ExportParameters exportParameters) throws Exception{

        StringBuffer sb = new StringBuffer();
        Connection con = ConnectionUtil.getConnection();
        PreparedStatement pStatement = null;
        ResultSet res = null;
        String columnas = "record_id,redcap_event_name";

        try {
            //recuperar los nombres de las columnas de todas las tablas
            String[] tableNames = exportParameters.getTableName().split(",");
            List<String[]> allColumns = getAllTableMetaData(tableNames);
            List<String> participantes = getSubjects(exportParameters);
            List<String> registros = new ArrayList<String>();
            int primerRegistro = 0;

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
            String[] characterDisch = "1,2,3,4,5,6".split(",");
            String[] bscMatOtherType = "1,4".split(",");
            String[] houseAmenities = "1,2,3,4,5,6,7,8".split(",");
            String[] transAccess = "1,2,3,4,5,6".split(",");
            String[] animalTyp = "1,2,3,4,5".split(",");
            String[] mosqRepTyp = "1,2,3,4,5".split(",");
            String[] fyesSpecify1 = "1,2,3,4,5".split(",");
            String[] sSpecify1 = "1,2,3,4,5".split(",");
            String[] whichEye = "1,2".split(",");
            String[] otherIssue = "1,2,3,4,5,6,7,8,9".split(",");
            String[] whichEar = "1,2".split(",");
            String[] breastReason = "1,2,3,4,5,6".split(",");
            String[] neurodeveType = "1,2,3,4,5,6".split(",");
            String[] exhibited = "1,2,3,4,5,6,7,8,9,10".split(",");

            //for (String redCapEvent : redCapEvents){
            for (String participante : participantes) {
                String valores = participante+SEPARADOR+exportParameters.getEvent();
                boolean existeDato = false;
                for (String tableName : tableNames) {
                    String columnasT = getTableColumns(allColumns, tableName);
                    boolean encontroRegistros = false;
                    //pasar a recuperar los datos. Setear parámetro si los hay
                    pStatement = con.prepareStatement("select " + columnasT + " from " + tableName + " where redcap_event_name = ?" + " and record_id = ? ");
                    pStatement.setString(1, exportParameters.getEvent());
                    pStatement.setString(2, participante);

                    res = pStatement.executeQuery();


                    String[] arrayColumnasT = columnasT.split(",");
                    while (res.next()) {
                        existeDato = true;
                        encontroRegistros = true;
                        for (String[] col : allColumns) {
                            if (existeColumna(col[1], arrayColumnasT)) {
                                Object val = null;
                                try {
                                    val = res.getObject(col[1]);
                                } catch (Exception ex) {
                                    ex.printStackTrace();
                                }
                                if (val != null) {
                                    //campos que necesitan un trato especial
                                    //ZP01 E
                                    if (col[1].equalsIgnoreCase("sea_diseases")) {
                                        valores += setValuesMultipleField(val.toString(), diseases);

                                    } else if (col[1].equalsIgnoreCase("sea_rash_first")) {
                                        valores += setValuesMultipleField(val.toString(), rashFirst);

                                    } else if (col[1].equalsIgnoreCase("sea_spread_part")) {
                                        valores += setValuesMultipleField(val.toString(), spreadPart);

                                    } else if (col[1].equalsIgnoreCase("sea_same_symptom")) {
                                        valores += setValuesMultipleField(val.toString(), sameSymptom);

                                    } else if (col[1].equalsIgnoreCase("sea_specify_symptom")) {
                                        valores += setValuesMultipleField(val.toString(), specifySymptom);

                                    } else if (col[1].equalsIgnoreCase("sea_tingling_arm") || col[1].equalsIgnoreCase("sea_numb_arm") || col[1].equalsIgnoreCase("sea_para_arm")) {
                                        valores += setValuesMultipleField(val.toString(), arm);

                                    } else if (col[1].equalsIgnoreCase("sea_tingling_leg") || col[1].equalsIgnoreCase("sea_numb_leg") || col[1].equalsIgnoreCase("sea_para_leg")) {
                                        valores += setValuesMultipleField(val.toString(), leg);

                                    } else if (col[1].equalsIgnoreCase("sea_tingling_hand") || col[1].equalsIgnoreCase("sea_numb_hand") || col[1].equalsIgnoreCase("sea_para_hand")) {
                                        valores += setValuesMultipleField(val.toString(), hand);

                                    } else if (col[1].equalsIgnoreCase("sea_tingling_foot") || col[1].equalsIgnoreCase("sea_numb_foot") || col[1].equalsIgnoreCase("sea_para_foot")) {
                                        valores += setValuesMultipleField(val.toString(), foot);

                                    } else if (col[1].equalsIgnoreCase("sea_tingling_face") || col[1].equalsIgnoreCase("sea_numb_face") || col[1].equalsIgnoreCase("sea_para_face")) {
                                        valores += setValuesMultipleField(val.toString(), face);

                                        //ZP01 FK
                                    } else if (col[1].equalsIgnoreCase("sea_character_disch")) {
                                        valores += setValuesMultipleField(val.toString(), characterDisch);

                                    } else if (col[1].equalsIgnoreCase("sea_oneweek_time") || col[1].equalsIgnoreCase("sea_next_time")) { //campos tipo hora HH:mm
                                        if (valores.isEmpty()) valores += val.toString().substring(0, 5);
                                        else valores += SEPARADOR + val.toString().substring(0, 5);

                                        //ZP02
                                    } else if (col[1].equalsIgnoreCase("bsc_mat_other_type")) {
                                        valores += setValuesMultipleField(val.toString(), bscMatOtherType);
                                        //campos tipo hora HH:mm
                                    } else if (col[1].equalsIgnoreCase("bsc_mat_bld_time") || col[1].equalsIgnoreCase("bsc_mat_slva_time") || col[1].equalsIgnoreCase("bsc_mat_vag_time") || col[1].equalsIgnoreCase("bsc_mat_vst_urn_time") || col[1].equalsIgnoreCase("bsc_mat_amf_time")
                                            || col[1].equalsIgnoreCase("bsc_mat_cord_time") || col[1].equalsIgnoreCase("bsc_mat_placen_time") || col[1].equalsIgnoreCase("bsc_mat_breastm_time") || col[1].equalsIgnoreCase("bsc_mat_fetalt_time") || col[1].equalsIgnoreCase("bsc_matd_breastm_time")) {
                                        if (valores.isEmpty()) valores += val.toString().substring(0, 5);
                                        else valores += SEPARADOR + val.toString().substring(0, 5);


                                    } else if (col[1].equalsIgnoreCase("bsc_mat_hom_urn_col") || col[1].equalsIgnoreCase("bsc_mat_hom_urn_rsn") || col[1].equalsIgnoreCase("bsc_mat_hom_urn_specify") || col[1].equalsIgnoreCase("bsc_mat_hom_urn_num") ||
                                            col[1].equalsIgnoreCase("bsc_mat_hom_urn_id1") || col[1].equalsIgnoreCase("bsc_mat_hom_urn_dat1") || col[1].equalsIgnoreCase("bsc_mat_hom_urn_time1") || col[1].equalsIgnoreCase("bsc_mat_hom_urn_com1") ||
                                            col[1].equalsIgnoreCase("bsc_mat_hom_urn_id2") || col[1].equalsIgnoreCase("bsc_mat_hom_urn_dat2") || col[1].equalsIgnoreCase("bsc_mat_hom_urn_time2") || col[1].equalsIgnoreCase("bsc_mat_hom_urn_com2") ||
                                            col[1].equalsIgnoreCase("bsc_mat_hom_urn_id3") || col[1].equalsIgnoreCase("bsc_mat_hom_urn_dat3") || col[1].equalsIgnoreCase("bsc_mat_hom_urn_time3") || col[1].equalsIgnoreCase("bsc_mat_hom_urn_com3") ||
                                            col[1].equalsIgnoreCase("bsc_mat_hom_urn_id4") || col[1].equalsIgnoreCase("bsc_mat_hom_urn_dat4") || col[1].equalsIgnoreCase("bsc_mat_hom_urn_time4") || col[1].equalsIgnoreCase("bsc_mat_hom_urn_com4")) {
                                        //para las muestras de horina en casa tomar los valores registrados en la visita quincenal previa (solo para los eventos mensuales)
                                        if (exportParameters.getEvent().contains("week_post_entry_arm_1")) {
                                            if (valores.isEmpty()) valores += col[1];
                                            else valores += SEPARADOR + col[1];
                                        } else {
                                            if (col[1].equalsIgnoreCase("bsc_mat_hom_urn_time1") || col[1].equalsIgnoreCase("bsc_mat_hom_urn_time2") || col[1].equalsIgnoreCase("bsc_mat_hom_urn_time3") || col[1].equalsIgnoreCase("bsc_mat_hom_urn_time4")) {
                                                if (valores.isEmpty()) valores += val.toString().substring(0, 5);
                                                else valores += SEPARADOR + val.toString().substring(0, 5);
                                            } else if (col[1].equalsIgnoreCase("bsc_mat_hom_urn_dat1") || col[1].equalsIgnoreCase("bsc_mat_hom_urn_dat2") || col[1].equalsIgnoreCase("bsc_mat_hom_urn_dat3") || col[1].equalsIgnoreCase("bsc_mat_hom_urn_dat4")) {
                                                if (valores.isEmpty())
                                                    valores += DateToString(res.getDate(col[1]), "dd/MM/yyyyy");
                                                else
                                                    valores += SEPARADOR + DateToString(res.getDate(col[1]), "dd/MM/yyyyy");
                                            } else {
                                                //si contiene uno de estos caracteres especiales escapar
                                                if (val.toString().contains(SEPARADOR) || val.toString().contains(COMILLA) || val.toString().contains(SALTOLINEA)) {
                                                    valores += SEPARADOR + QUOTE + val.toString() + QUOTE;
                                                } else {
                                                    if (valores.isEmpty()) valores += val.toString();
                                                    else valores += SEPARADOR + val.toString();
                                                }
                                            }
                                        }
                                        //ZP03
                                    } else if (col[1].equalsIgnoreCase("mon_charac_discharge")) {
                                        valores += setValuesMultipleField(val.toString(), characterDisch);

                                    } else if (col[1].equalsIgnoreCase("mon_rash_first")) {
                                        valores += setValuesMultipleField(val.toString(), rashFirst);

                                    } else if (col[1].equalsIgnoreCase("mon_spread_part")) {
                                        valores += setValuesMultipleField(val.toString(), spreadPart);

                                    } else if (col[1].equalsIgnoreCase("mon_specify_symptom")) {
                                        valores += setValuesMultipleField(val.toString(), specifySymptom);

                                    } else if (col[1].equalsIgnoreCase("mon_tingling_arm") || col[1].equalsIgnoreCase("mon_numb_arm") || col[1].equalsIgnoreCase("mon_para_arm")) {
                                        valores += setValuesMultipleField(val.toString(), arm);

                                    } else if (col[1].equalsIgnoreCase("mon_tingling_leg") || col[1].equalsIgnoreCase("mon_numb_leg") || col[1].equalsIgnoreCase("mon_para_leg")) {
                                        valores += setValuesMultipleField(val.toString(), leg);

                                    } else if (col[1].equalsIgnoreCase("mon_tingling_hand") || col[1].equalsIgnoreCase("mon_numb_hand") || col[1].equalsIgnoreCase("mon_para_hand")) {
                                        valores += setValuesMultipleField(val.toString(), hand);

                                    } else if (col[1].equalsIgnoreCase("mon_tingling_foot") || col[1].equalsIgnoreCase("mon_numb_foot") || col[1].equalsIgnoreCase("mon_para_foot")) {
                                        valores += setValuesMultipleField(val.toString(), foot);

                                    } else if (col[1].equalsIgnoreCase("mon_tingling_face") || col[1].equalsIgnoreCase("mon_numb_face") || col[1].equalsIgnoreCase("mon_para_face")) {
                                        valores += setValuesMultipleField(val.toString(), face);

                                    } else if (col[1].equalsIgnoreCase("mon_oneweek_time") || col[1].equalsIgnoreCase("mon_next_time")) { //campos tipo hora HH:mm
                                        if (valores.isEmpty()) valores += val.toString().substring(0, 5);
                                        else valores += SEPARADOR + val.toString().substring(0, 5);

                                        //ZP04 AD
                                    } else if (col[1].equalsIgnoreCase("tri_house_amenities")) {
                                        valores += setValuesMultipleField(val.toString(), houseAmenities);

                                    } else if (col[1].equalsIgnoreCase("tri_trans_access")) {
                                        valores += setValuesMultipleField(val.toString(), transAccess);

                                    } else if (col[1].equalsIgnoreCase("tri_animal_typ")) {
                                        valores += setValuesMultipleField(val.toString(), animalTyp);

                                        //ZP04 FH
                                    } else if (col[1].equalsIgnoreCase("tri_mosq_rep_typ")) {
                                        valores += setValuesMultipleField(val.toString(), mosqRepTyp);

                                    } else if (col[1].equalsIgnoreCase("tri_next_visit_time")) { //campo tipo hora HH:mm
                                        if (valores.isEmpty()) valores += val.toString().substring(0, 5);
                                        else valores += SEPARADOR + val.toString().substring(0, 5);

                                        //ZP05
                                    } else if (col[1].equalsIgnoreCase("ult_fyes_specify1")) {
                                        valores += setValuesMultipleField(val.toString(), fyesSpecify1);

                                    } else if (col[1].equalsIgnoreCase("ult_sspecify1")) {
                                        valores += setValuesMultipleField(val.toString(), sSpecify1);

                                        //ZP06
                                    } else if (col[1].equalsIgnoreCase("deli_rash_first")) {
                                        valores += setValuesMultipleField(val.toString(), rashFirst);

                                    } else if (col[1].equalsIgnoreCase("deli_spread_part")) {
                                        valores += setValuesMultipleField(val.toString(), spreadPart);

                                    } else if (col[1].equalsIgnoreCase("deli_specify_symptom")) {
                                        valores += setValuesMultipleField(val.toString(), specifySymptom);

                                    } else if (col[1].equalsIgnoreCase("deli_tingling_arm") || col[1].equalsIgnoreCase("deli_numb_arm") || col[1].equalsIgnoreCase("deli_para_arm")) {
                                        valores += setValuesMultipleField(val.toString(), arm);

                                    } else if (col[1].equalsIgnoreCase("deli_tingling_leg") || col[1].equalsIgnoreCase("deli_numb_leg") || col[1].equalsIgnoreCase("deli_para_leg")) {
                                        valores += setValuesMultipleField(val.toString(), leg);

                                    } else if (col[1].equalsIgnoreCase("deli_tingling_hand") || col[1].equalsIgnoreCase("deli_numb_hand") || col[1].equalsIgnoreCase("deli_para_hand")) {
                                        valores += setValuesMultipleField(val.toString(), hand);

                                    } else if (col[1].equalsIgnoreCase("deli_tingling_foot") || col[1].equalsIgnoreCase("deli_numb_foot") || col[1].equalsIgnoreCase("deli_para_foot")) {
                                        valores += setValuesMultipleField(val.toString(), foot);

                                    } else if (col[1].equalsIgnoreCase("deli_tingling_face") || col[1].equalsIgnoreCase("deli_numb_face") || col[1].equalsIgnoreCase("deli_para_face")) {
                                        valores += setValuesMultipleField(val.toString(), face);
                                        //ZP07
                                    } else if (col[1].equalsIgnoreCase("infant_which_eye")) {
                                        valores += setValuesMultipleField(val.toString(), whichEye);

                                    }else if (col[1].equalsIgnoreCase("infant_other_issue")) {
                                        valores += setValuesMultipleField(val.toString(), otherIssue);

                                    }else if (col[1].equalsIgnoreCase("infant_which_ear")) {
                                        valores += setValuesMultipleField(val.toString(), whichEar);

                                    }else if (col[1].equalsIgnoreCase("infant_breast_reason")) {
                                        valores += setValuesMultipleField(val.toString(), breastReason);

                                    }else if (col[1].equalsIgnoreCase("infant_neurodeve_type")) {
                                        valores += setValuesMultipleField(val.toString(), neurodeveType);

                                    }else if (col[1].equalsIgnoreCase("infant_exhibited")) {
                                        valores += setValuesMultipleField(val.toString(), exhibited);
                                        //ZP02d (Infante)
                                    } else if (col[1].equalsIgnoreCase("infant_mat_bld_time") || col[1].equalsIgnoreCase("infant_mat_slva_time") || col[1].equalsIgnoreCase("infant_mat_vst_urn_time")){ //campo tipo hora HH:mm
                                        if (valores.isEmpty()) valores += val.toString().substring(0,5);
                                        else valores += SEPARADOR + val.toString().substring(0,5);

                                    } else { //defecto
                                        if (val instanceof String) {
                                            String valFormat = val.toString().replaceAll(ENTER,ESPACIO).replaceAll(SALTOLINEA,ESPACIO);
                                            //si contiene uno de estos caracteres especiales escapar
                                            if (valFormat.contains(SEPARADOR) || valFormat.contains(COMILLA) || valFormat.contains(SALTOLINEA)) {
                                                valores += SEPARADOR + QUOTE + valFormat.trim() + QUOTE;
                                            } else {
                                                if (valores.isEmpty()) valores += valFormat.trim();
                                                else valores += SEPARADOR + valFormat.trim();
                                            }
                                        } else if (val instanceof Integer) {
                                            if (valores.isEmpty())
                                                valores += String.valueOf(res.getInt(col[1]));
                                            else valores += SEPARADOR + String.valueOf(res.getInt(col[1]));

                                        } else if (val instanceof java.util.Date) {
                                            if (valores.isEmpty())
                                                valores += DateToString(res.getDate(col[1]), "dd/MM/yyyyy");
                                            else
                                                valores += SEPARADOR + DateToString(res.getDate(col[1]), "dd/MM/yyyyy");

                                        } else if (val instanceof Float) {
                                            if (valores.isEmpty())
                                                valores += String.valueOf(res.getFloat(col[1]));
                                            else valores += SEPARADOR + String.valueOf(res.getFloat(col[1]));
                                        }
                                    }
                                } else {
                                    //campos que necesitan un trato especial
                                    //ZP01 E
                                    if (col[1].equalsIgnoreCase("sea_diseases")) {
                                        for (int i = 0; i < diseases.length; i++) {
                                            valores += SEPARADOR;
                                        }
                                    } else if (col[1].equalsIgnoreCase("sea_rash_first")) {
                                        for (int i = 0; i < rashFirst.length; i++) {
                                            valores += SEPARADOR;
                                        }
                                    } else if (col[1].equalsIgnoreCase("sea_spread_part")) {
                                        for (int i = 0; i < spreadPart.length; i++) {
                                            valores += SEPARADOR;
                                        }
                                    } else if (col[1].equalsIgnoreCase("sea_same_symptom")) {
                                        for (int i = 0; i < sameSymptom.length; i++) {
                                            valores += SEPARADOR;
                                        }
                                    } else if (col[1].equalsIgnoreCase("sea_specify_symptom")) {
                                        for (int i = 0; i < specifySymptom.length; i++) {
                                            valores += SEPARADOR;
                                        }
                                    } else if (col[1].equalsIgnoreCase("sea_tingling_arm") || col[1].equalsIgnoreCase("sea_numb_arm") || col[1].equalsIgnoreCase("sea_para_arm") ||
                                            col[1].equalsIgnoreCase("sea_tingling_leg") || col[1].equalsIgnoreCase("sea_numb_leg") || col[1].equalsIgnoreCase("sea_para_leg") ||
                                            col[1].equalsIgnoreCase("sea_tingling_hand") || col[1].equalsIgnoreCase("sea_numb_hand") || col[1].equalsIgnoreCase("sea_para_hand") ||
                                            col[1].equalsIgnoreCase("sea_tingling_foot") || col[1].equalsIgnoreCase("sea_numb_foot") || col[1].equalsIgnoreCase("sea_para_foot") ||
                                            col[1].equalsIgnoreCase("sea_tingling_face") || col[1].equalsIgnoreCase("sea_numb_face") || col[1].equalsIgnoreCase("sea_para_face")) {
                                        valores += SEPARADOR + SEPARADOR;
                                        //Zp01 FK
                                    } else if (col[1].equalsIgnoreCase("sea_character_disch")) {
                                        for (int i = 0; i < characterDisch.length; i++) {
                                            valores += SEPARADOR;
                                        }

                                        //ZP02
                                    } else if (col[1].equalsIgnoreCase("bsc_mat_other_type")) {
                                        for (int i = 0; i < bscMatOtherType.length; i++) {
                                            valores += SEPARADOR;
                                        }

                                    } else if (exportParameters.getEvent().contains("week_post_entry_arm_1") && (col[1].equalsIgnoreCase("bsc_mat_hom_urn_col") || col[1].equalsIgnoreCase("bsc_mat_hom_urn_rsn") || col[1].equalsIgnoreCase("bsc_mat_hom_urn_specify") || col[1].equalsIgnoreCase("bsc_mat_hom_urn_num") ||
                                            col[1].equalsIgnoreCase("bsc_mat_hom_urn_id1") || col[1].equalsIgnoreCase("bsc_mat_hom_urn_dat1") || col[1].equalsIgnoreCase("bsc_mat_hom_urn_time1") || col[1].equalsIgnoreCase("bsc_mat_hom_urn_com1") ||
                                            col[1].equalsIgnoreCase("bsc_mat_hom_urn_id2") || col[1].equalsIgnoreCase("bsc_mat_hom_urn_dat2") || col[1].equalsIgnoreCase("bsc_mat_hom_urn_time2") || col[1].equalsIgnoreCase("bsc_mat_hom_urn_com2") ||
                                            col[1].equalsIgnoreCase("bsc_mat_hom_urn_id3") || col[1].equalsIgnoreCase("bsc_mat_hom_urn_dat3") || col[1].equalsIgnoreCase("bsc_mat_hom_urn_time3") || col[1].equalsIgnoreCase("bsc_mat_hom_urn_com3") ||
                                            col[1].equalsIgnoreCase("bsc_mat_hom_urn_id4") || col[1].equalsIgnoreCase("bsc_mat_hom_urn_dat4") || col[1].equalsIgnoreCase("bsc_mat_hom_urn_time4") || col[1].equalsIgnoreCase("bsc_mat_hom_urn_com4"))) {
                                        //para las muestras de horina en casa tomar los valores registrados en la visita quincenal previa (solo para los eventos mensuales)
                                        if (valores.isEmpty()) valores += col[1];
                                        else valores += SEPARADOR + col[1];

                                        //ZP03
                                    } else if (col[1].equalsIgnoreCase("mon_charac_discharge")) {
                                        for (int i = 0; i < characterDisch.length; i++) {
                                            valores += SEPARADOR;
                                        }
                                    } else if (col[1].equalsIgnoreCase("mon_rash_first")) {
                                        for (int i = 0; i < rashFirst.length; i++) {
                                            valores += SEPARADOR;
                                        }
                                    } else if (col[1].equalsIgnoreCase("mon_spread_part")) {
                                        for (int i = 0; i < spreadPart.length; i++) {
                                            valores += SEPARADOR;
                                        }
                                    } else if (col[1].equalsIgnoreCase("mon_specify_symptom")) {
                                        for (int i = 0; i < specifySymptom.length; i++) {
                                            valores += SEPARADOR;
                                        }
                                    } else if (col[1].equalsIgnoreCase("mon_tingling_arm") || col[1].equalsIgnoreCase("mon_numb_arm") || col[1].equalsIgnoreCase("mon_para_arm") ||
                                            col[1].equalsIgnoreCase("mon_tingling_leg") || col[1].equalsIgnoreCase("mon_numb_leg") || col[1].equalsIgnoreCase("mon_para_leg") ||
                                            col[1].equalsIgnoreCase("mon_tingling_hand") || col[1].equalsIgnoreCase("mon_numb_hand") || col[1].equalsIgnoreCase("mon_para_hand") ||
                                            col[1].equalsIgnoreCase("mon_tingling_foot") || col[1].equalsIgnoreCase("mon_numb_foot") || col[1].equalsIgnoreCase("mon_para_foot") ||
                                            col[1].equalsIgnoreCase("mon_tingling_face") || col[1].equalsIgnoreCase("mon_numb_face") || col[1].equalsIgnoreCase("mon_para_face")) {
                                        valores += SEPARADOR + SEPARADOR;

                                        //Zp04 AD
                                    } else if (col[1].equalsIgnoreCase("tri_house_amenities")) {
                                        for (int i = 0; i < houseAmenities.length; i++) {
                                            valores += SEPARADOR;
                                        }
                                    } else if (col[1].equalsIgnoreCase("tri_trans_access")) {
                                        for (int i = 0; i < transAccess.length; i++) {
                                            valores += SEPARADOR;
                                        }
                                    } else if (col[1].equalsIgnoreCase("tri_animal_typ")) {
                                        for (int i = 0; i < animalTyp.length; i++) {
                                            valores += SEPARADOR;
                                        }

                                        //ZP04 FH
                                    } else if (col[1].equalsIgnoreCase("tri_mosq_rep_typ")) {
                                        for (int i = 0; i < mosqRepTyp.length; i++) {
                                            valores += SEPARADOR;
                                        }

                                        //ZP05
                                    } else if (col[1].equalsIgnoreCase("ult_fyes_specify1")) {
                                        for (int i = 0; i < fyesSpecify1.length; i++) {
                                            valores += SEPARADOR;
                                        }
                                    } else if (col[1].equalsIgnoreCase("ult_sspecify1")) {
                                        for (int i = 0; i < sSpecify1.length; i++) {
                                            valores += SEPARADOR;
                                        }

                                        //ZP06
                                    } else if (col[1].equalsIgnoreCase("deli_rash_first")) {
                                        for (int i = 0; i < rashFirst.length; i++) {
                                            valores += SEPARADOR;
                                        }
                                    } else if (col[1].equalsIgnoreCase("deli_spread_part")) {
                                        for (int i = 0; i < spreadPart.length; i++) {
                                            valores += SEPARADOR;
                                        }
                                    } else if (col[1].equalsIgnoreCase("deli_specify_symptom")) {
                                        for (int i = 0; i < specifySymptom.length; i++) {
                                            valores += SEPARADOR;
                                        }
                                    } else if (col[1].equalsIgnoreCase("deli_tingling_arm") || col[1].equalsIgnoreCase("deli_numb_arm") || col[1].equalsIgnoreCase("deli_para_arm") ||
                                            col[1].equalsIgnoreCase("deli_tingling_leg") || col[1].equalsIgnoreCase("deli_numb_leg") || col[1].equalsIgnoreCase("deli_para_leg") ||
                                            col[1].equalsIgnoreCase("deli_tingling_hand") || col[1].equalsIgnoreCase("deli_numb_hand") || col[1].equalsIgnoreCase("deli_para_hand") ||
                                            col[1].equalsIgnoreCase("deli_tingling_foot") || col[1].equalsIgnoreCase("deli_numb_foot") || col[1].equalsIgnoreCase("deli_para_foot") ||
                                            col[1].equalsIgnoreCase("deli_tingling_face") || col[1].equalsIgnoreCase("deli_numb_face") || col[1].equalsIgnoreCase("deli_para_face")) {
                                        valores += SEPARADOR + SEPARADOR;

                                    }else if (col[1].equalsIgnoreCase("infant_which_eye")){
                                        for(int i = 0 ; i< whichEye.length; i++){
                                            valores += SEPARADOR;
                                        }
                                    }else if (col[1].equalsIgnoreCase("infant_other_issue")){
                                        for(int i = 0 ; i< otherIssue.length; i++){
                                            valores += SEPARADOR;
                                        }
                                    }else if (col[1].equalsIgnoreCase("infant_which_ear")){
                                        for(int i = 0 ; i< whichEar.length; i++){
                                            valores += SEPARADOR;
                                        }
                                    }else if (col[1].equalsIgnoreCase("infant_breast_reason")){
                                        for(int i = 0 ; i< breastReason.length; i++){
                                            valores += SEPARADOR;
                                        }
                                    }else if (col[1].equalsIgnoreCase("infant_neurodeve_type")){
                                        for(int i = 0 ; i< neurodeveType.length; i++){
                                            valores += SEPARADOR;
                                        }
                                    }else if (col[1].equalsIgnoreCase("infant_exhibited")){
                                        for(int i = 0 ; i< exhibited.length; i++){
                                            valores += SEPARADOR;
                                        }
                                        //defecto
                                    } else {
                                        valores += SEPARADOR;
                                    }
                                }// fin else valor=null
                            }//fin llamado existeColumna()
                        }//fin foreach allColumns
                    }// fiN WHILE.NEXT

                    //columnas que necesita redcap y no estan en la tabla
                    if (tableName.equalsIgnoreCase(Constants.TABLE_ZP00)) {
                        columnasT = columnasT.replaceFirst("scr_prestudyna", "scr_prestudyna___1");
                        columnasT += SEPARADOR + "zp00_screening_complete";

                    } else if (tableName.equalsIgnoreCase(Constants.TABLE_ZP01AD)) {
                        columnasT = columnasT.replaceFirst("sea_lmpunknown", "sea_lmpunknown___1");
                        columnasT = columnasT.replaceFirst("sea_leavena", "sea_leavena___1");
                        columnasT += SEPARADOR + "zp01_study_entry_section_a_to_d_complete";

                    } else if (tableName.equalsIgnoreCase(Constants.TABLE_ZP01E)) {
                        columnasT = columnasT.replaceAll("sea_diseases", "sea_diseases___1,sea_diseases___2,sea_diseases___3,sea_diseases___4,sea_diseases___5,sea_diseases___6,sea_diseases___7,sea_diseases___8,sea_diseases___9,sea_diseases___98");
                        columnasT = columnasT.replaceAll("sea_rash_first", "sea_rash_first___1,sea_rash_first___2,sea_rash_first___3,sea_rash_first___4,sea_rash_first___5,sea_rash_first___6,sea_rash_first___7,sea_rash_first___8,sea_rash_first___9");
                        columnasT = columnasT.replaceAll("sea_spread_part", "sea_spread_part___1,sea_spread_part___2,sea_spread_part___3,sea_spread_part___4,sea_spread_part___5,sea_spread_part___6,sea_spread_part___7,sea_spread_part___8,sea_spread_part___9");
                        columnasT = columnasT.replaceAll("sea_same_symptom", "sea_same_symptom___1,sea_same_symptom___2,sea_same_symptom___3");
                        columnasT = columnasT.replaceAll("sea_specify_symptom", "sea_specify_symptom___1,sea_specify_symptom___2,sea_specify_symptom___3,sea_specify_symptom___4,sea_specify_symptom___5,sea_specify_symptom___6,sea_specify_symptom___7,sea_specify_symptom___8,sea_specify_symptom___9,sea_specify_symptom___10,sea_specify_symptom___11,sea_specify_symptom___12,sea_specify_symptom___13");
                        columnasT = columnasT.replaceAll("sea_tingling_arm", "sea_tingling_arm___1,sea_tingling_arm___2");
                        columnasT = columnasT.replaceAll("sea_tingling_leg", "sea_tingling_leg___1,sea_tingling_leg___2");
                        columnasT = columnasT.replaceAll("sea_tingling_hand", "sea_tingling_hand___1,sea_tingling_hand___2");
                        columnasT = columnasT.replaceAll("sea_tingling_foot", "sea_tingling_foot___1,sea_tingling_foot___2");
                        columnasT = columnasT.replaceAll("sea_tingling_face", "sea_tingling_face___1,sea_tingling_face___2");
                        columnasT = columnasT.replaceAll("sea_numb_arm", "sea_numb_arm___1,sea_numb_arm___2");
                        columnasT = columnasT.replaceAll("sea_numb_leg", "sea_numb_leg___1,sea_numb_leg___2");
                        columnasT = columnasT.replaceAll("sea_numb_hand", "sea_numb_hand___1,sea_numb_hand___2");
                        columnasT = columnasT.replaceAll("sea_numb_foot", "sea_numb_foot___1,sea_numb_foot___2");
                        columnasT = columnasT.replaceAll("sea_numb_face", "sea_numb_face___1,sea_numb_face___2");
                        columnasT = columnasT.replaceAll("sea_para_arm", "sea_para_arm___1,sea_para_arm___2");
                        columnasT = columnasT.replaceAll("sea_para_leg", "sea_para_leg___1,sea_para_leg___2");
                        columnasT = columnasT.replaceAll("sea_para_hand", "sea_para_hand___1,sea_para_hand___2");
                        columnasT = columnasT.replaceAll("sea_para_foot", "sea_para_foot___1,sea_para_foot___2");
                        columnasT = columnasT.replaceAll("sea_para_face", "sea_para_face___1,sea_para_face___2");
                        columnasT = columnasT.replaceAll("sea_tempunknown", "sea_tempunknown___1");
                        columnasT += SEPARADOR + "zp01_study_entry_section_e_complete";

                    } else if (tableName.equalsIgnoreCase(Constants.TABLE_ZP01FK)) {
                        columnasT = columnasT.replaceAll("sea_character_disch", "sea_character_disch___1,sea_character_disch___2,sea_character_disch___3,sea_character_disch___4,sea_character_disch___5,sea_character_disch___6");
                        columnasT += SEPARADOR + "zp01_study_entry_section_f_to_k_complete";

                    } else if (tableName.equalsIgnoreCase(Constants.TABLE_ZP02)) {
                        columnasT = columnasT.replaceFirst("bsc_mat_other_type", "bsc_mat_other_type___1,bsc_mat_other_type___4");
                        columnasT += SEPARADOR + "zp02_biospecimen_collection_complete";

                    } else if (tableName.equalsIgnoreCase(Constants.TABLE_ZP03)) {
                        columnasT = columnasT.replaceAll("mon_charac_discharge", "mon_charac_discharge___1,mon_charac_discharge___2,mon_charac_discharge___3,mon_charac_discharge___4,mon_charac_discharge___5,mon_charac_discharge___6");
                        columnasT = columnasT.replaceAll("mon_rash_first", "mon_rash_first___1,mon_rash_first___2,mon_rash_first___3,mon_rash_first___4,mon_rash_first___5,mon_rash_first___6,mon_rash_first___7,mon_rash_first___8,mon_rash_first___9");
                        columnasT = columnasT.replaceAll("mon_spread_part", "mon_spread_part___1,mon_spread_part___2,mon_spread_part___3,mon_spread_part___4,mon_spread_part___5,mon_spread_part___6,mon_spread_part___7,mon_spread_part___8,mon_spread_part___9");
                        columnasT = columnasT.replaceAll("mon_specify_symptom", "mon_specify_symptom___1,mon_specify_symptom___2,mon_specify_symptom___3,mon_specify_symptom___4,mon_specify_symptom___5,mon_specify_symptom___6,mon_specify_symptom___7,mon_specify_symptom___8,mon_specify_symptom___9,mon_specify_symptom___10,mon_specify_symptom___11,mon_specify_symptom___12,mon_specify_symptom___13");
                        columnasT = columnasT.replaceAll("mon_tingling_arm", "mon_tingling_arm___1,mon_tingling_arm___2");
                        columnasT = columnasT.replaceAll("mon_tingling_leg", "mon_tingling_leg___1,mon_tingling_leg___2");
                        columnasT = columnasT.replaceAll("mon_tingling_hand", "mon_tingling_hand___1,mon_tingling_hand___2");
                        columnasT = columnasT.replaceAll("mon_tingling_foot", "mon_tingling_foot___1,mon_tingling_foot___2");
                        columnasT = columnasT.replaceAll("mon_tingling_face", "mon_tingling_face___1,mon_tingling_face___2");
                        columnasT = columnasT.replaceAll("mon_numb_arm", "mon_numb_arm___1,mon_numb_arm___2");
                        columnasT = columnasT.replaceAll("mon_numb_leg", "mon_numb_leg___1,mon_numb_leg___2");
                        columnasT = columnasT.replaceAll("mon_numb_hand", "mon_numb_hand___1,mon_numb_hand___2");
                        columnasT = columnasT.replaceAll("mon_numb_foot", "mon_numb_foot___1,mon_numb_foot___2");
                        columnasT = columnasT.replaceAll("mon_numb_face", "mon_numb_face___1,mon_numb_face___2");
                        columnasT = columnasT.replaceAll("mon_para_arm", "mon_para_arm___1,mon_para_arm___2");
                        columnasT = columnasT.replaceAll("mon_para_leg", "mon_para_leg___1,mon_para_leg___2");
                        columnasT = columnasT.replaceAll("mon_para_hand", "mon_para_hand___1,mon_para_hand___2");
                        columnasT = columnasT.replaceAll("mon_para_foot", "mon_para_foot___1,mon_para_foot___2");
                        columnasT = columnasT.replaceAll("mon_para_face", "mon_para_face___1,mon_para_face___2");
                        columnasT = columnasT.replaceAll("mon_tempunknown", "mon_tempunknown___1");
                        columnasT += SEPARADOR + "zp03_monthly_visit_complete";

                    } else if (tableName.equalsIgnoreCase(Constants.TABLE_ZP04AD)) {
                        columnasT = columnasT.replaceAll("tri_prim_job_title_ref", "tri_prim_job_title_ref___1");
                        columnasT = columnasT.replaceAll("tri_prev_job_title_ref", "tri_prev_job_title_ref___1");
                        columnasT = columnasT.replaceAll("tri_husb_job_title_ref", "tri_husb_job_title_ref___1");
                        columnasT = columnasT.replaceAll("tri_resid_ref", "tri_resid_ref___1");
                        columnasT = columnasT.replaceAll("tri_house_amenities", "tri_house_amenities___1,tri_house_amenities___2,tri_house_amenities___3,tri_house_amenities___4,tri_house_amenities___5,tri_house_amenities___6,tri_house_amenities___7,tri_house_amenities___8");
                        columnasT = columnasT.replaceAll("tri_trans_access", "tri_trans_access___1,tri_trans_access___2,tri_trans_access___3,tri_trans_access___4,tri_trans_access___5,tri_trans_access___6");
                        columnasT = columnasT.replaceAll("tri_animal_typ", "tri_animal_typ___1,tri_animal_typ___2,tri_animal_typ___3,tri_animal_typ___4,tri_animal_typ___5");
                        columnasT += SEPARADOR + "zp04_trimester_visit_section_a_to_d_complete";

                    } else if (tableName.equalsIgnoreCase(Constants.TABLE_ZP04E)) {
                        columnasT += SEPARADOR + "zp04_trimester_visit_section_e_complete";

                    } else if (tableName.equalsIgnoreCase(Constants.TABLE_ZP04FH)) {
                        columnasT = columnasT.replaceAll("tri_mosq_rep_dk_spray", "tri_mosq_rep_dk_spray___1");
                        columnasT = columnasT.replaceAll("tri_mosq_rep_dk_lotion", "tri_mosq_rep_dk_lotion___1");
                        columnasT = columnasT.replaceAll("tri_mosq_rep_dk_spiral", "tri_mosq_rep_dk_spiral___1");
                        columnasT = columnasT.replaceAll("tri_mosq_rep_dk_plugin", "tri_mosq_rep_dk_plugin___1");
                        columnasT = columnasT.replaceAll("tri_mosq_rep_dk_other", "tri_mosq_rep_dk_other___1");
                        columnasT = columnasT.replaceAll("tri_mosq_rep_typ", "tri_mosq_rep_typ___1,tri_mosq_rep_typ___2,tri_mosq_rep_typ___3,tri_mosq_rep_typ___4,tri_mosq_rep_typ___5");
                        columnasT += SEPARADOR + "zp04_trimester_visit_section_f_to_h_complete";

                    } else if (tableName.equalsIgnoreCase(Constants.TABLE_ZP05)) {
                        columnasT = columnasT.replaceAll("ult_id_na", "ult_id_na___1");
                        columnasT = columnasT.replaceAll("ult_fcrl_na1", "ult_fcrl_na1___1");
                        columnasT = columnasT.replaceAll("ult_fcrl_na2", "ult_fcrl_na2___1");
                        columnasT = columnasT.replaceAll("ult_fcrl_na3", "ult_fcrl_na3___1");
                        columnasT = columnasT.replaceAll("ult_fyes_specify1", "ult_fyes_specify1___1,ult_fyes_specify1___2,ult_fyes_specify1___3,ult_fyes_specify1___4,ult_fyes_specify1___5");
                        columnasT = columnasT.replaceAll("ult_sspecify1", "ult_sspecify1___1,ult_sspecify1___2,ult_sspecify1___3,ult_sspecify1___4,ult_sspecify1___5");
                        columnasT += SEPARADOR + "zp05_ultrasound_exam_complete";

                    } else if (tableName.equalsIgnoreCase(Constants.TABLE_ZP06)) {
                        columnasT = columnasT.replaceAll("deli_rash_first", "deli_rash_first___1,deli_rash_first___2,deli_rash_first___3,deli_rash_first___4,deli_rash_first___5,deli_rash_first___6,deli_rash_first___7,deli_rash_first___8,deli_rash_first___9");
                        columnasT = columnasT.replaceAll("deli_spread_part", "deli_spread_part___1,deli_spread_part___2,deli_spread_part___3,deli_spread_part___4,deli_spread_part___5,deli_spread_part___6,deli_spread_part___7,deli_spread_part___8,deli_spread_part___9");
                        columnasT = columnasT.replaceAll("deli_specify_symptom", "deli_specify_symptom___1,deli_specify_symptom___2,deli_specify_symptom___3,deli_specify_symptom___4,deli_specify_symptom___5,deli_specify_symptom___6,deli_specify_symptom___7,deli_specify_symptom___8,deli_specify_symptom___9,deli_specify_symptom___10,deli_specify_symptom___11,deli_specify_symptom___12,deli_specify_symptom___13");
                        columnasT = columnasT.replaceAll("deli_tingling_arm", "deli_tingling_arm___1,deli_tingling_arm___2");
                        columnasT = columnasT.replaceAll("deli_tingling_leg", "deli_tingling_leg___1,deli_tingling_leg___2");
                        columnasT = columnasT.replaceAll("deli_tingling_hand", "deli_tingling_hand___1,deli_tingling_hand___2");
                        columnasT = columnasT.replaceAll("deli_tingling_foot", "deli_tingling_foot___1,deli_tingling_foot___2");
                        columnasT = columnasT.replaceAll("deli_tingling_face", "deli_tingling_face___1,deli_tingling_face___2");
                        columnasT = columnasT.replaceAll("deli_numb_arm", "deli_numb_arm___1,deli_numb_arm___2");
                        columnasT = columnasT.replaceAll("deli_numb_leg", "deli_numb_leg___1,deli_numb_leg___2");
                        columnasT = columnasT.replaceAll("deli_numb_hand", "deli_numb_hand___1,deli_numb_hand___2");
                        columnasT = columnasT.replaceAll("deli_numb_foot", "deli_numb_foot___1,deli_numb_foot___2");
                        columnasT = columnasT.replaceAll("deli_numb_face", "deli_numb_face___1,deli_numb_face___2");
                        columnasT = columnasT.replaceAll("deli_para_arm", "deli_para_arm___1,deli_para_arm___2");
                        columnasT = columnasT.replaceAll("deli_para_leg", "deli_para_leg___1,deli_para_leg___2");
                        columnasT = columnasT.replaceAll("deli_para_hand", "deli_para_hand___1,deli_para_hand___2");
                        columnasT = columnasT.replaceAll("deli_para_foot", "deli_para_foot___1,deli_para_foot___2");
                        columnasT = columnasT.replaceAll("deli_para_face", "deli_para_face___1,deli_para_face___2");
                        columnasT = columnasT.replaceAll("deli_tempunknown", "deli_tempunknown___1");
                        columnasT += SEPARADOR + "zp06_delivery_and_6week_visit_complete";

                    } else if (tableName.equalsIgnoreCase(Constants.TABLE_ZP08)) {
                        columnasT += SEPARADOR + "zp08_study_exit_complete";

                    } else if (tableName.equalsIgnoreCase(Constants.TABLE_ZP07)) {
                        columnasT = columnasT.replaceAll("infant_which_eye","infant_which_eye___1,infant_which_eye___2");
                        columnasT = columnasT.replaceAll("infant_other_issue","infant_other_issue___1,infant_other_issue___2,infant_other_issue___3,infant_other_issue___4,infant_other_issue___5,infant_other_issue___6,infant_other_issue___7,infant_other_issue___8,infant_other_issue___9");
                        columnasT = columnasT.replaceAll("infant_which_ear","infant_which_ear___1,infant_which_ear___2");
                        columnasT = columnasT.replaceAll("infant_breast_reason","infant_breast_reason___1,infant_breast_reason___2,infant_breast_reason___3,infant_breast_reason___4,infant_breast_reason___5,infant_breast_reason___6");
                        columnasT = columnasT.replaceAll("infant_neurodeve_type","infant_neurodeve_type___1,infant_neurodeve_type___2,infant_neurodeve_type___3,infant_neurodeve_type___4,infant_neurodeve_type___5,infant_neurodeve_type___6");
                        columnasT = columnasT.replaceAll("infant_exhibited","infant_exhibited___1,infant_exhibited___2,infant_exhibited___3,infant_exhibited___4,infant_exhibited___5,infant_exhibited___6,infant_exhibited___7,infant_exhibited___8,infant_exhibited___9,infant_exhibited___10");
                        columnasT = columnasT.replaceAll("infant_wtpercen_na","infant_wtpercen_na___99");
                        columnasT = columnasT.replaceAll("infant_lenpercen_na","infant_lenpercen_na___99");
                        columnasT = columnasT.replaceAll("infant_heapercen_na","infant_heapercen_na___99");
                        columnasT = columnasT.replaceAll("infant_apgar_na","infant_apgar_na___99");
                        columnasT += SEPARADOR + "zp07_infant_assessment_complete";
                    }else if (tableName.equalsIgnoreCase(Constants.TABLE_ZP02d)) {
                        columnasT += SEPARADOR + "zp02d_infant_biospecimen_collection_complete";

                    }

                    if (primerRegistro == 0) {
                        columnas += ((columnas.isEmpty() ? "" : SEPARADOR) + columnasT);
                    }

                    if(!encontroRegistros){
                        arrayColumnasT = columnasT.split(",");
                        for(int i = 0 ; i < arrayColumnasT.length; i++){
                            valores += SEPARADOR;
                        }
                    }else {
                        //valor para zp0XXX_complete
                        valores += SEPARADOR + "1";
                    }
                }
                if (existeDato) {
                    registros.add(valores);
                }
                primerRegistro++;
            }
            //}

            /*completar campos para tomas de muestras de orina en la casa que se toman 15 dias +- antes de los eventos mensuales
            (solo para los eventos mensuales desde semana 4 hasta semana 44) */
            if (exportParameters.getEvent().contains("week_post_entry_arm_1")) {
                int semana = Integer.valueOf(exportParameters.getEvent().substring(0, exportParameters.getEvent().indexOf("_")));
                String evento15dias = String.valueOf(semana - 2) + "_week_post_entry_arm_1";
                String sqlOrinaCasa = "select record_id,bsc_mat_hom_urn_col,bsc_mat_hom_urn_rsn,bsc_mat_hom_urn_specify,bsc_mat_hom_urn_num,bsc_mat_hom_urn_id1,bsc_mat_hom_urn_dat1,bsc_mat_hom_urn_time1,bsc_mat_hom_urn_com1," +
                        "bsc_mat_hom_urn_id2,bsc_mat_hom_urn_dat2,bsc_mat_hom_urn_time2,bsc_mat_hom_urn_com2,bsc_mat_hom_urn_id3,bsc_mat_hom_urn_dat3,bsc_mat_hom_urn_time3,bsc_mat_hom_urn_com3," +
                        "bsc_mat_hom_urn_id4,bsc_mat_hom_urn_dat4,bsc_mat_hom_urn_time4,bsc_mat_hom_urn_com4 from " + Constants.TABLE_ZP02 + " where redcap_event_name = ?";
                if (exportParameters.thereAreCodes()) sqlOrinaCasa += " and record_id between ? and ? ";
                pStatement = con.prepareStatement(sqlOrinaCasa);
                pStatement.setString(1, evento15dias);
                if (exportParameters.thereAreCodes()) {
                    pStatement.setString(2, exportParameters.getCodigoInicio());
                    pStatement.setString(3, exportParameters.getCodigoFin());
                }
                res = pStatement.executeQuery();
                while (res.next()) {
                    int indice = 0;
                    String registrotmp = "";
                    for (String registro : registros) {
                        String codparticipante = registro.substring(0, registro.indexOf(SEPARADOR));
                        if (codparticipante.equalsIgnoreCase(res.getString("record_id"))) {
                            registrotmp = registro;
                            registrotmp = registrotmp.replaceAll("bsc_mat_hom_urn_col", (res.getObject("bsc_mat_hom_urn_col") != null ? res.getString("bsc_mat_hom_urn_col") : ""));
                            registrotmp = registrotmp.replaceAll("bsc_mat_hom_urn_rsn", (res.getObject("bsc_mat_hom_urn_rsn") != null ? res.getString("bsc_mat_hom_urn_rsn") : ""));
                            registrotmp = registrotmp.replaceAll("bsc_mat_hom_urn_specify", (res.getObject("bsc_mat_hom_urn_specify") != null ? res.getString("bsc_mat_hom_urn_specify") : ""));
                            registrotmp = registrotmp.replaceAll("bsc_mat_hom_urn_num", (res.getObject("bsc_mat_hom_urn_num") != null ? res.getString("bsc_mat_hom_urn_num") : ""));
                            registrotmp = registrotmp.replaceAll("bsc_mat_hom_urn_id1", (res.getObject("bsc_mat_hom_urn_id1") != null ? res.getString("bsc_mat_hom_urn_id1") : ""));
                            registrotmp = registrotmp.replaceAll("bsc_mat_hom_urn_dat1", (res.getObject("bsc_mat_hom_urn_dat1") != null ? DateToString(res.getDate("bsc_mat_hom_urn_dat1"), "dd/MM/yyyyy") : ""));
                            registrotmp = registrotmp.replaceAll("bsc_mat_hom_urn_time1", (res.getObject("bsc_mat_hom_urn_time1") != null ? res.getString("bsc_mat_hom_urn_time1").substring(0, 5) : ""));
                            registrotmp = registrotmp.replaceAll("bsc_mat_hom_urn_com1", (res.getObject("bsc_mat_hom_urn_com1") != null ? res.getString("bsc_mat_hom_urn_com1") : ""));
                            registrotmp = registrotmp.replaceAll("bsc_mat_hom_urn_id2", (res.getObject("bsc_mat_hom_urn_id2") != null ? res.getString("bsc_mat_hom_urn_id2") : ""));
                            registrotmp = registrotmp.replaceAll("bsc_mat_hom_urn_dat2", (res.getObject("bsc_mat_hom_urn_dat2") != null ? DateToString(res.getDate("bsc_mat_hom_urn_dat2"), "dd/MM/yyyyy") : ""));
                            registrotmp = registrotmp.replaceAll("bsc_mat_hom_urn_time2", (res.getObject("bsc_mat_hom_urn_time2") != null ? res.getString("bsc_mat_hom_urn_time2").substring(0, 5) : ""));
                            registrotmp = registrotmp.replaceAll("bsc_mat_hom_urn_com2", (res.getObject("bsc_mat_hom_urn_com2") != null ? res.getString("bsc_mat_hom_urn_com2") : ""));
                            registrotmp = registrotmp.replaceAll("bsc_mat_hom_urn_id3", (res.getObject("bsc_mat_hom_urn_id3") != null ? res.getString("bsc_mat_hom_urn_id3") : ""));
                            registrotmp = registrotmp.replaceAll("bsc_mat_hom_urn_dat3", (res.getObject("bsc_mat_hom_urn_dat3") != null ? DateToString(res.getDate("bsc_mat_hom_urn_dat3"), "dd/MM/yyyyy") : ""));
                            registrotmp = registrotmp.replaceAll("bsc_mat_hom_urn_time3", (res.getObject("bsc_mat_hom_urn_time3") != null ? res.getString("bsc_mat_hom_urn_time3").substring(0, 5) : ""));
                            registrotmp = registrotmp.replaceAll("bsc_mat_hom_urn_com3", (res.getObject("bsc_mat_hom_urn_com3") != null ? res.getString("bsc_mat_hom_urn_com3") : ""));
                            registrotmp = registrotmp.replaceAll("bsc_mat_hom_urn_id4", (res.getObject("bsc_mat_hom_urn_id4") != null ? res.getString("bsc_mat_hom_urn_id4") : ""));
                            registrotmp = registrotmp.replaceAll("bsc_mat_hom_urn_dat4", (res.getObject("bsc_mat_hom_urn_dat4") != null ? DateToString(res.getDate("bsc_mat_hom_urn_dat4"), "dd/MM/yyyyy") : ""));
                            registrotmp = registrotmp.replaceAll("bsc_mat_hom_urn_time4", (res.getObject("bsc_mat_hom_urn_time4") != null ? res.getString("bsc_mat_hom_urn_time4").substring(0, 5) : ""));
                            registrotmp = registrotmp.replaceAll("bsc_mat_hom_urn_com4", (res.getObject("bsc_mat_hom_urn_com4") != null ? res.getString("bsc_mat_hom_urn_com4") : ""));
                            break;
                        }
                        indice++;
                    }
                    if (!registrotmp.isEmpty()) {
                        registros.set(indice, registrotmp);
                    }
                }
            }/*FIN completar campos para tomas de muestras de orina en la casa*/

            if (exportParameters.isAddHeader()) {
                sb.append(columnas);
            }
            for (String registro : registros){
                sb.append(SALTOLINEA);
                //por si queda algun registro que no tiene toma de muestra 15 dias, se limpia
                registro = registro.replaceAll("bsc_mat_hom_urn_col", "");
                registro = registro.replaceAll("bsc_mat_hom_urn_rsn", "");
                registro = registro.replaceAll("bsc_mat_hom_urn_specify", "");
                registro = registro.replaceAll("bsc_mat_hom_urn_num", "");
                registro = registro.replaceAll("bsc_mat_hom_urn_id1", "");
                registro = registro.replaceAll("bsc_mat_hom_urn_dat1", "");
                registro = registro.replaceAll("bsc_mat_hom_urn_time1", "");
                registro = registro.replaceAll("bsc_mat_hom_urn_com1", "");
                registro = registro.replaceAll("bsc_mat_hom_urn_id2", "");
                registro = registro.replaceAll("bsc_mat_hom_urn_dat2", "");
                registro = registro.replaceAll("bsc_mat_hom_urn_time2", "");
                registro = registro.replaceAll("bsc_mat_hom_urn_com2", "");
                registro = registro.replaceAll("bsc_mat_hom_urn_id3", "");
                registro = registro.replaceAll("bsc_mat_hom_urn_dat3", "");
                registro = registro.replaceAll("bsc_mat_hom_urn_time3", "");
                registro = registro.replaceAll("bsc_mat_hom_urn_com3", "");
                registro = registro.replaceAll("bsc_mat_hom_urn_id4", "");
                registro = registro.replaceAll("bsc_mat_hom_urn_dat4", "");
                registro = registro.replaceAll("bsc_mat_hom_urn_time4", "");
                registro = registro.replaceAll("bsc_mat_hom_urn_com4", "");
                sb.append(registro);
            }
            //sb.append(SALTOLINEA);
        } catch (Exception e) {
            e.printStackTrace();
        }finally {
            if (res !=null) res.close();
            if (pStatement !=null) pStatement.close();
            if (con !=null) con.close();
        }
        return sb;
    }

    private static boolean existeColumna(String buscar, String[] todas){
        for(String comparar : todas){
            if (comparar.equalsIgnoreCase(buscar)) {
                return true;
            }
        }
        return false;
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

    private static String getTableColumns(List<String[]> allColumns, String tableName){
        String columnas = "";
        for(String col[] : allColumns){
            if (col[0].equalsIgnoreCase(tableName)) {
                if (columnas.isEmpty()) //es primer columna
                    columnas += col[1];
                else
                    columnas += SEPARADOR + col[1];
            }
        }
        return columnas;
    }

    public List<String> getRedCapEvents(){
        List<String> redCapEvents = new ArrayList<String>();
        redCapEvents.add(Constants.SCREENING);
        redCapEvents.add(Constants.ENTRY);
        redCapEvents.add(Constants.WEEK4);
        redCapEvents.add(Constants.WEEK8);
        redCapEvents.add(Constants.WEEK12);
        redCapEvents.add(Constants.WEEK16);
        redCapEvents.add(Constants.WEEK20);
        redCapEvents.add(Constants.WEEK24);
        redCapEvents.add(Constants.WEEK28);
        redCapEvents.add(Constants.WEEK32);
        redCapEvents.add(Constants.WEEK36);
        redCapEvents.add(Constants.WEEK40);
        redCapEvents.add(Constants.WEEK44);
        redCapEvents.add(Constants.DELIVERY);
        redCapEvents.add(Constants.AFTERDELIVERY);
        redCapEvents.add(Constants.UNSHED1);
        redCapEvents.add(Constants.UNSHED2);
        redCapEvents.add(Constants.UNSHED3);
        redCapEvents.add(Constants.UNSHED4);
        redCapEvents.add(Constants.UNSHED5);
        redCapEvents.add(Constants.EXIT);
        redCapEvents.add(Constants.BIRTH);
        redCapEvents.add(Constants.MONTHS3);
        redCapEvents.add(Constants.MONTHS6);
        redCapEvents.add(Constants.MONTHS12);
        return redCapEvents;
    }
    
    public List<String> getCompleteRedCapEvents(){
        List<String> redCapEvents = new ArrayList<String>();
        redCapEvents.add(Constants.SCREENING);
        redCapEvents.add(Constants.ENTRY);
        redCapEvents.add(Constants.WEEK2);
        redCapEvents.add(Constants.WEEK4);
        redCapEvents.add(Constants.WEEK6);
        redCapEvents.add(Constants.WEEK8);
        redCapEvents.add(Constants.WEEK10);
        redCapEvents.add(Constants.WEEK12);
        redCapEvents.add(Constants.WEEK14);
        redCapEvents.add(Constants.WEEK16);
        redCapEvents.add(Constants.WEEK18);
        redCapEvents.add(Constants.WEEK20);
        redCapEvents.add(Constants.WEEK22);
        redCapEvents.add(Constants.WEEK24);
        redCapEvents.add(Constants.WEEK26);
        redCapEvents.add(Constants.WEEK28);
        redCapEvents.add(Constants.WEEK30);
        redCapEvents.add(Constants.WEEK32);
        redCapEvents.add(Constants.WEEK34);
        redCapEvents.add(Constants.WEEK36);
        redCapEvents.add(Constants.WEEK38);
        redCapEvents.add(Constants.WEEK40);
        redCapEvents.add(Constants.WEEK42);
        redCapEvents.add(Constants.WEEK44);
        redCapEvents.add(Constants.DELIVERY);
        redCapEvents.add(Constants.AFTERDELIVERY);
        redCapEvents.add(Constants.UNSHED1);
        redCapEvents.add(Constants.UNSHED2);
        redCapEvents.add(Constants.UNSHED3);
        redCapEvents.add(Constants.UNSHED4);
        redCapEvents.add(Constants.UNSHED5);
        redCapEvents.add(Constants.EXIT);
        redCapEvents.add(Constants.BIRTH);
        redCapEvents.add(Constants.MONTHS3);
        redCapEvents.add(Constants.MONTHS6);
        redCapEvents.add(Constants.MONTHS12);
        return redCapEvents;
    }

    private List<String> getSubjects(ExportParameters exportParameters) throws SQLException{
        StringBuilder sqlStrBuilder = new StringBuilder("select zp00.recordId from Zp00Screening zp00");
        if (exportParameters.thereAreCodes()) sqlStrBuilder.append(" where zp00.recordId between :inicio and :fin ");

        Session session = sessionFactory.getCurrentSession();
        Query query = session.createQuery(sqlStrBuilder.toString());
        if (exportParameters.thereAreCodes()){
            query.setString("inicio", exportParameters.getCodigoInicio());
            query.setString("fin", exportParameters.getCodigoFin());
        }
        List<String> subjects = query.list();
        sqlStrBuilder = new StringBuilder("select zpID.recordId from ZpInfantData zpID");
        if (exportParameters.thereAreCodes()) sqlStrBuilder.append(" where zpID.recordId between :inicio and :fin ");
        query = session.createQuery(sqlStrBuilder.toString());
        if (exportParameters.thereAreCodes()){
            query.setString("inicio", exportParameters.getCodigoInicio());
            query.setString("fin", exportParameters.getCodigoFin());
        }
        subjects.addAll(query.list());
        return subjects;
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
