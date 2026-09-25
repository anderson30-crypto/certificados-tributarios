package prueba;

import servicio.BackupServicio;


public class PruebaBackup {


    public static void main(String[] args) {


        BackupServicio backup =
                new BackupServicio();


        backup.realizarBackup();


    }

}