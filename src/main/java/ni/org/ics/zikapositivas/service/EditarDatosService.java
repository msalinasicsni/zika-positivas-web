package ni.org.ics.zikapositivas.service;

import com.mysql.cj.jdbc.MysqlDataSource;
import ni.org.ics.zikapositivas.domain.audit.AuditTrail;
import ni.org.ics.zikapositivas.utils.ConnectionUtil;
import ni.org.ics.zikapositivas.utils.DateUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.sql.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by FIRSTICT on 2/16/2017.
 * V1.0
 */
@Service("editarDatosService")
@Transactional
public class EditarDatosService {

    @Resource(name = "auditTrailService")
    private AuditTrailService auditTrailService;

    public List<String> getTableMetaData(String tableName) throws Exception{
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

    public int getColumnType(String tableName, String columnName, Connection con) throws Exception{
        int type = 0;
        try {
            DatabaseMetaData meta = con.getMetaData();
            ResultSet res = meta.getColumns(null, null, tableName, null);
            while (res.next()) {
                    if (res.getString("COLUMN_NAME").equalsIgnoreCase(columnName)) {
                        //el record_id siempre debe ser el primer campo
                        type = res.getInt("DATA_TYPE");
                    }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return type;
    }

    /***
     * Método que actualiza el valor de cualquier campo de cualquier tabla en la base de datos
     * @param tabla nombre de la tabla a modificar
     * @param evento indica el evento de redcap al que pertenece el registro a modificar (no es obligatorio)
     * @param id código del participante a modificar
     * @param propiedad nombre del campo a actualizar
     * @param valor nuevo valor para el campo
     * @param setNull si es true el nuevo valor del campo será NULL, en caso contrario se toma el valor del párametro @valor
     * @param actorId username del usuario que realiza la modificación
     * @return cantidad de registros modificados
     * @throws Exception
     */
    public int updateProperty(String tabla, String evento, String id, String propiedad, String valor, boolean setNull, String actorId) throws Exception {
        Connection connection = ConnectionUtil.getConnection();
        int registros = 0;
        int resultado = 0;
        StringBuilder sbUpdate = null;
        String nombreCampoId = "record_id", nombreCampoEvento = "redcap_event_name";

        if (tabla.equalsIgnoreCase("zp_reporte_us_recepcion") || tabla.equalsIgnoreCase("zp_reporte_us_salida")) {
            nombreCampoId = "codigo";
            nombreCampoEvento = "evento";
        }else if(tabla.equalsIgnoreCase("zp_cons_recepcion") || tabla.equalsIgnoreCase("zp_cons_salida")) {
            nombreCampoId = "codigo";
            nombreCampoEvento = "";
        }

        /*determinar los registros que se van a actualizar*/
        StringBuilder sbSelect = new StringBuilder("select ");
        sbSelect.append(propiedad);
        if (!nombreCampoEvento.isEmpty())
                sbSelect.append(", ").append (nombreCampoEvento);

        sbSelect.append (" from ")
                .append(tabla)
                .append(" where ");

        sbSelect.append(nombreCampoId).append(" = '").append(id).append("'");

        if (evento!=null && !nombreCampoEvento.isEmpty())
            sbSelect.append(" and ").append(nombreCampoEvento).append(" = '")
                    .append(evento).append("'");

        int type = getColumnType(tabla, propiedad, connection);

        Statement statement = connection.createStatement();
        ResultSet resultSet = statement.executeQuery(sbSelect.toString());
        while(resultSet.next()) {
            //por cada registro armar update
            sbUpdate = new StringBuilder("update ");
            sbUpdate.append(tabla)
                    .append(" set ")
                    .append(propiedad)
                    .append((setNull?" = null ":" = ? "))
                    .append(" where ");

                    sbUpdate.append(nombreCampoId).append(" = '")
                    .append(id).append("'");

            if (!nombreCampoEvento.isEmpty()) {
                sbUpdate.append(" and ").append(nombreCampoEvento).append(" = '")
                        .append(resultSet.getString(2)).append("'");
            }

            PreparedStatement pstm = connection.prepareStatement(sbUpdate.toString());
            //dependiendo del tipo de dato, puede requerir tratamiento especial
            if (!setNull) {
                if (type == Types.VARCHAR)
                    pstm.setString(1, valor);
                else if (type == Types.DATE) {
                    if (valor.trim().contains(" ")) {
                        pstm.setDate(1, new java.sql.Date(DateUtil.StringToDate(valor, "dd/MM/yyyy").getTime()));
                    }
                } else if (type == Types.TIMESTAMP) {
                    if (valor.trim().contains(" ")) {
                        pstm.setTimestamp(1, new Timestamp(DateUtil.StringToDate(valor, "dd/MM/yyyy HH:mm").getTime()));
                    } else {
                        pstm.setDate(1, new java.sql.Date(DateUtil.StringToDate(valor, "dd/MM/yyyy").getTime()));
                    }
                } else if (type == Types.INTEGER)
                    pstm.setInt(1, Integer.valueOf(valor));
                else if (type == Types.FLOAT || type == Types.REAL)
                    pstm.setFloat(1, Float.valueOf(valor));
            }

            resultado=pstm.executeUpdate();
            //si se actualizó el registro entonces registrar pistas de auditoria e incrementar contador de registros actualizados
            if (resultado>0){
                registros++;
                AuditTrail auditTrail = new AuditTrail();
                auditTrail.setEntityId(id+","+(nombreCampoEvento.isEmpty()?"":resultSet.getString(2)));
                auditTrail.setEntityName(tabla);
                auditTrail.setEntityClass("editardatos." + tabla);
                auditTrail.setEntityProperty(propiedad);
                auditTrail.setEntityPropertyNewValue((setNull?null:valor));
                auditTrail.setEntityPropertyOldValue((resultSet.getObject(1)!=null?resultSet.getObject(1).toString():null));
                auditTrail.setOperationType("UPDATE");
                auditTrail.setOperationDate(new Date());
                auditTrail.setUsername(actorId);
                auditTrailService.saveAuditTrail(auditTrail);
            }
        }
        return registros;
    }

}
