package ni.org.ics.zikapositivas.service;

import ni.org.ics.zikapositivas.domain.Zp00Screening;

import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;

/**
 * Servicio para el objeto Zp00Screening
 * Created by FIRSTICT on 10/11/2016.
 * V1.0
 */
@Service("zp00ScreeningService")
@Transactional
public class Zp00ScreeningService {

    @Resource(name="sessionFactory")
    private SessionFactory sessionFactory;

    /**
     * Retorna todos los formularios Zp00Screening
     * @return una lista de Zp00Screening
     */
    @SuppressWarnings("unchecked")
    public List<Zp00Screening> getZp00Screening(){
        Session session = sessionFactory.getCurrentSession();
        Query query = session.createQuery("FROM Zp00Screening");
        return query.list();
    }
    
    /**
     * Retorna todos los formularios Zp00Screening
     * @return una lista de Zp00Screening
     */
    @SuppressWarnings("unchecked")
    public List<Zp00Screening> getZp00ScreeningByUser(String username){
        Session session = sessionFactory.getCurrentSession();
        Query query = session.createQuery("FROM Zp00Screening zp00 where zp00.pasive = '0' and zp00.preScreenId in (select recId from ZpPreScreening zpPre where zpPre.pasive = '0' and zpPre.cs in " +
        		"(Select uc.centro.cs from UserCenter uc where uc.user.username =:usuarioactual and uc.pasive = '0'))");
        query.setParameter("usuarioactual",username);
        return query.list();
    }

    /**
     * Retorna un formulario Zp00Screening
     * @param recordId del Zp00Screening a retornar
     * @return un Zp00Screening
     */
    public Zp00Screening getZp00Screening(String recordId){
        Session session = sessionFactory.getCurrentSession();
        Query query = session.createQuery("FROM Zp00Screening where recordId = :recordId");
        query.setParameter("recordId",recordId);
        return (Zp00Screening)query.uniqueResult();
    }

    /**
     * Guardar un formulario Zp00Screening
     * @param zp00Screening a guardar
     */
    public void saveZp00Screening(Zp00Screening zp00Screening){
        Session session = sessionFactory.getCurrentSession();
        session.saveOrUpdate(zp00Screening);
    }
}
