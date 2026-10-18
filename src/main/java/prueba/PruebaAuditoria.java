package prueba;

import java.util.List;

import modelo.Auditoria;
import servicio.AuditoriaServicio;


public class PruebaAuditoria {


    public static void main(String[] args) {


        AuditoriaServicio servicio = 
                new AuditoriaServicio();


        List<Auditoria> auditorias =
                servicio.consultarAuditorias();



        if(auditorias.isEmpty()){


            System.out.println(
                    "No existen registros de auditoria"
            );


        } else {


            for(Auditoria a : auditorias){


                System.out.println(
                        "ID LOG: "
                        + a.getIdLog()
                );


                System.out.println(
                        "Usuario: "
                        + a.getIdUsuario()
                );


                System.out.println(
                        "Accion: "
                        + a.getAccion()
                );


                System.out.println(
                        "Fecha: "
                        + a.getFechaRegistro()
                );


                System.out.println(
                        "Tabla afectada: "
                        + a.getTablaAfectada()
                );


                System.out.println(
                        "Registro ID: "
                        + a.getRegistroId()
                );


                System.out.println(
                        "Datos anteriores: "
                        + a.getDatosAnteriores()
                );


                System.out.println(
                        "Datos nuevos: "
                        + a.getDatosNuevos()
                );


                System.out.println(
                        "================================="
                );

            }

        }


    }

}