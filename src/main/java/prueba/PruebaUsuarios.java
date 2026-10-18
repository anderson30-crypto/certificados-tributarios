package prueba;

import modelo.Usuario;
import servicio.UsuarioServicio;


public class PruebaUsuarios {


    public static void main(String[] args) {


        UsuarioServicio servicio =
        new UsuarioServicio();



        // EDITAR USUARIO


        Usuario usuario = new Usuario();


        usuario.setIdUsuario(2);

        usuario.setNombreUsuario("Carlos Perez Modificado");

        usuario.setCorreo("carlosnuevo@empresa.com");

        usuario.setIdRol(1);



        boolean editado =
        servicio.actualizarUsuario(usuario);



        if(editado){

            System.out.println(
            "Usuario actualizado correctamente");

        }else{

            System.out.println(
            "No se pudo actualizar");

        }





        // DESACTIVAR USUARIO


        boolean desactivado =
        servicio.desactivarUsuario(2);



        if(desactivado){

            System.out.println(
            "Usuario desactivado correctamente");

        }else{

            System.out.println(
            "No se pudo desactivar");

        }


        System.out.println(
        "\nLISTADO FINAL");


        servicio.mostrarUsuarios();


    }

}