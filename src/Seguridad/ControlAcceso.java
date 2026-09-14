package Seguridad;

import dao.UsuarioDAO;
import dao.RolPermisoDAO;


public class ControlAcceso {


    private UsuarioDAO usuarioDAO;
    private RolPermisoDAO rolPermisoDAO;



    public ControlAcceso(){

        usuarioDAO = new UsuarioDAO();

        rolPermisoDAO = new RolPermisoDAO();

    }




    public boolean validarPermiso(
            int idUsuario,
            String permiso){



        boolean autorizado = false;



        try {

            int idRol =
                    usuarioDAO.obtenerRolUsuario(idUsuario);



            if(idRol == 0){

                return false;

            }

            autorizado =
                    rolPermisoDAO.tienePermiso(
                            idRol,
                            permiso
                    );



        }catch(Exception e){


            System.out.println(
                    "Error validando permiso: "
                    + e.getMessage()
            );


        }



        return autorizado;


    }



}