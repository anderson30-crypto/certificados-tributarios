package servicio;

import dao.RolPermisoDAO;
import modelo.Permiso;

import java.util.ArrayList;


public class RolPermisoServicio {


    private RolPermisoDAO rolPermisoDAO;



    public RolPermisoServicio(){

        rolPermisoDAO = new RolPermisoDAO();

    }



    // ASIGNAR PERMISO A UN ROL

    public boolean asignarPermiso(int idRol, int idPermiso){


        if(idRol <= 0 || idPermiso <= 0){


            System.out.println(
            "El rol y el permiso son obligatorios");


            return false;

        }


        return rolPermisoDAO.asignarPermisoRol(
                idRol,
                idPermiso
        );

    }




    // MOSTRAR PERMISOS DE UN ROL

    public void mostrarPermisosPorRol(int idRol){


        ArrayList<Permiso> permisos =
        rolPermisoDAO.listarPermisosPorRol(idRol);



        System.out.println(
        "PERMISOS DEL ROL: " + idRol);



        for(Permiso p : permisos){


            System.out.println(
            "ID: "
            + p.getIdPermiso()
            +" - "
            + p.getNombrePermiso()
            +" - "
            + p.getDescripcion()
            );

        }

    }


}