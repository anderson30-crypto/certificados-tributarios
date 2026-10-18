package servicio;

import dao.AuditoriaDAO;
import modelo.Auditoria;

import java.util.List;


public class AuditoriaServicio {


    AuditoriaDAO dao = new AuditoriaDAO();



    public void guardar(
            int usuario,
            String accion,
            String tabla,
            int idRegistro,
            String anterior,
            String nuevo){


        Auditoria a = new Auditoria(
                usuario,
                accion,
                tabla,
                idRegistro,
                anterior,
                nuevo
        );


        dao.registrar(a);

    }

    public List<Auditoria> consultarAuditorias(){


        return dao.listarTodas();


    }


}