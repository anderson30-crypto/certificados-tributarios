package servicio;

import dao.PermisoDAO;
import modelo.Permiso;
import java.util.ArrayList;


public class PermisoServicio {


    private PermisoDAO permisoDAO;


    public PermisoServicio(){

        permisoDAO = new PermisoDAO();

    }



    public boolean registrarPermiso(Permiso permiso){


        if(permiso.getNombrePermiso() == null ||
           permiso.getNombrePermiso().isEmpty()){


            System.out.println(
            "El nombre del permiso es obligatorio");


            return false;

        }


        return permisoDAO.crearPermiso(permiso);

    }




    public void listarPermisos(){


        ArrayList<Permiso> lista =
        permisoDAO.listarPermisos();



        for(Permiso p : lista){


            System.out.println(
            "ID: "
            +p.getIdPermiso()
            +" Permiso: "
            +p.getNombrePermiso()
            );


        }


    }


}