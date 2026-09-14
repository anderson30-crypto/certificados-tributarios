package servicio;


import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;



public class BackupServicio {


    public boolean realizarBackup(){


        try {


            // Crear estructura de carpetas por fecha

            String año =
                    new SimpleDateFormat("yyyy")
                    .format(new Date());


            String mes =
                    new SimpleDateFormat("MM")
                    .format(new Date());


            String dia =
                    new SimpleDateFormat("dd")
                    .format(new Date());



            String carpeta =
                    "Backup/"
                    + año
                    + "/"
                    + mes
                    + "/"
                    + dia;



            File directorio =
                    new File(carpeta);



            if(!directorio.exists()){


                directorio.mkdirs();

            }




            // Nombre único del archivo

            String fecha =
                    new SimpleDateFormat(
                            "HH_mm_ss"
                    )
                    .format(new Date());



            String archivo =

                    carpeta
                    + "/certificados_tributarios_"
                    + fecha
                    + ".sql";






            // Ruta mysqldump

            String rutaMysql =

                    "C:\\Program Files\\MySQL\\MySQL Server 8.0\\bin\\mysqldump.exe";






            // Leer configuración

            Properties propiedades =
                    new Properties();



            InputStream entrada =
                    new FileInputStream(
                            "src/conexion/config.properties"
                    );



            propiedades.load(entrada);



            String usuario =
                    propiedades.getProperty("usuario");


            String password =
                    propiedades.getProperty("password");






            // Crear proceso de backup

            ProcessBuilder builder =

                    new ProcessBuilder(

                            rutaMysql,

                            "-u",

                            usuario,

                            "-p" + password,

                            "certificados_tributarios",

                            "-r",

                            archivo

                    );




            Process proceso =

                    builder.start();



            int resultado =

                    proceso.waitFor();






            if(resultado == 0){



                File archivoBackup =

                        new File(archivo);




                if(archivoBackup.exists()){



                    double tamaño =

                            archivoBackup.length()
                            / 1024.0;




                    DecimalFormat formato =

                            new DecimalFormat("#.##");




                    System.out.println(
                            "Backup creado correctamente:"
                    );



                    System.out.println(
                            archivo
                    );



                    System.out.println(
                            "Tamaño del backup: "
                            + formato.format(tamaño)
                            + " KB"
                    );



                    return true;


                }else{


                    System.out.println(
                            "El backup terminó pero el archivo no existe"
                    );


                    return false;

                }



            }else{


                System.out.println(
                        "Error creando backup"
                );


                return false;

            }




        }catch(Exception e){



            System.out.println(
                    "Error backup: "
                    + e.getMessage()
            );



            return false;

        }


    }


}