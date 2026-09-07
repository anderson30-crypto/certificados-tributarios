package servicio;

import dao.UsuarioDAO;
import modelo.Usuario;


public class UsuarioServicio {


    private UsuarioDAO usuarioDAO;


    public UsuarioServicio(){

        usuarioDAO = new UsuarioDAO();

    }



    public boolean registrarUsuario(Usuario usuario){


        if(usuario.getNombreUsuario() == null ||
           usuario.getNombreUsuario().isEmpty()){

            System.out.println(
            "El nombre es obligatorio");

            return false;
        }


        return usuarioDAO.crearUsuario(usuario);

    }



    public void mostrarUsuarios(){


        for(Usuario u : usuarioDAO.listarUsuarios()){


            System.out.println(
            "ID: "
            +u.getIdUsuario()
            +" Nombre: "
            +u.getNombreUsuario()
            +" Estado: "
            +u.isEstado()
            );

        }

    }

    public boolean actualizarUsuario(Usuario usuario){


        if(usuario.getIdUsuario() <= 0){

            System.out.println(
            "Usuario no valido");

            return false;

        }


        return usuarioDAO.editarUsuario(usuario);

    }




    public boolean desactivarUsuario(int idUsuario){


        if(idUsuario <= 0){

            System.out.println(
            "Usuario no valido");

            return false;

        }


        return usuarioDAO.desactivarUsuario(idUsuario);

    }
}
