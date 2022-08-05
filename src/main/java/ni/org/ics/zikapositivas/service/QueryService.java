package ni.org.ics.zikapositivas.service;

import ni.org.ics.zikapositivas.utils.ConnectionUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by FIRSTICT on 11/14/2016.
 * V1.0
 */
@Service("queryService")
@Transactional
public class QueryService {
    private static final String SEPARADOR = ",";
    private static final String SALTOLINEA = "\n";
    private static final String QUOTE = "\"";
    private static final String COMA = "\"";

    public StringBuffer getResultQuery(String query) throws Exception{
        StringBuffer sb = new StringBuffer();

        Connection con = ConnectionUtil.getConnection();
        Statement statement = null;
        ResultSet res = null;
        String columnas = "";
        String valores = "";
        try {
            int indiceSelect = query.toLowerCase().trim().indexOf(' ');
            int indiceFrom = query.toLowerCase().indexOf("from");
            columnas = query.substring(indiceSelect, indiceFrom);
            String[] columns = columnas.replaceAll(" ","").split(",");
            //pasar a recuperar los datos.

            statement = con.createStatement();

            res = statement.executeQuery(query);

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
                sb.append(valores);
                valores = "";
                sb.append(SALTOLINEA);
            }
        } catch (Exception e) {
            e.printStackTrace();
            sb = new StringBuffer();
            sb.append("ERROR");
            sb.append(SALTOLINEA);
            sb.append(e.getMessage());
        }finally {
            if (res !=null) res.close();
            if (statement !=null) statement.close();
            if (con !=null) con.close();
        }
        return sb;
    }

    public static String DateToString(Date dtFecha, String format)  {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(format);
        if(dtFecha!=null)
            return simpleDateFormat.format(dtFecha);
        else
            return null;
    }
}
