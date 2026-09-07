package prueba;

import servicio.RolPermisoServicio;


public class PruevaPermisos {


    public static void main(String[] args) {


        RolPermisoServicio servicio =
        new RolPermisoServicio();



        // ASIGNAR PERMISO AL ROL ADMINISTRADOR


        boolean asignado =
        servicio.asignarPermiso(1, 1);



        if(asignado){


            System.out.println(
            "Permiso asignado correctamente");


        }else{


            System.out.println(
            "No se pudo asignar el permiso");

        }



        // CONSULTAR PERMISOS DEL ROL


        servicio.mostrarPermisosPorRol(1);



    }

}