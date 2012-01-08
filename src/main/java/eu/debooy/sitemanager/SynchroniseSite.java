/**
 * Copyright 2012 Marco de Booij
 *
 * Licensed under the EUPL, Version 1.1 or - as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * you may not use this work except in compliance with the Licence. You may
 * obtain a copy of the Licence at:
 *
 * http://www.osor.eu/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */
package eu.debooy.sitemanager;

import eu.debooy.doosutils.Arguments;
import eu.debooy.doosutils.Banner;
import eu.debooy.doosutils.Datum;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.ParseException;
import java.util.Date;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;


/**
 * @author Marco de Booij
 */
public class SynchroniseSite {
  private static  Boolean   force       = false;
  private static  Boolean   ftpOverview = false;
  private static  Boolean   ftpSync     = false;
  private static  FTPClient ftp         = new FTPClient();
  private static  String    ftpHome     = "";
  private static  String    ftpHost     = "";
  private static  String    ftpPasswd   = "";
  private static  String    ftpType     = "";
  private static  String    ftpUser     = "";
  private static  String    localSite   = "";

  /**
   * Delete een directory leeg om die te kunnen verwijderen.
   * 
   * @param directory
   * @throws IOException 
   */
  private static void deleteFTPDirectory(String directory) throws IOException {
    ftp.changeWorkingDirectory(directory);
    FTPFile[] ftpContent  = ftp.listFiles();
    if (ftpContent != null) {
      for (int i = 0; i < ftpContent.length; i++) {
        if (ftpContent[i].isDirectory()) {
          deleteFTPDirectory(ftpContent[i].getName());
        }
        ftp.deleteFile(ftpContent[i].getName());
      }
    }

    ftp.changeWorkingDirectory("..");
    ftp.removeDirectory(directory);
  }

  public static void execute(String[] args) {
    Banner.printBanner("Synchronise Site");

    Arguments arguments = new Arguments(args);
    arguments.setParameters(new String[] {"force", "ftpHome", "ftpHost",
                                          "ftpOverview", "ftpPasswd", "ftpSync",
                                          "ftpType", "ftpUser","localSite"});
    arguments.setVerplicht(new String[] {"ftpHome", "ftpHost",  "ftpPasswd",
                                         "ftpUser"});
    if (!arguments.isValid()) {
      help();
      return;
    }

    if (arguments.hasArgument("force")) {
      force       =
        Boolean.parseBoolean(arguments.getArgument("force"));
    }
    if (arguments.hasArgument("ftpOverview")) {
      ftpOverview =
        Boolean.parseBoolean(arguments.getArgument("ftpOverview"));
    }
    if (arguments.hasArgument("ftpSync")) {
      ftpSync     =
        Boolean.parseBoolean(arguments.getArgument("ftpSync"));
    }
    ftpHome       = arguments.getArgument("ftpHome");
    ftpHost       = arguments.getArgument("ftpHost");
    ftpPasswd     = arguments.getArgument("ftpPasswd");
    if (arguments.hasArgument("ftpType")) {
      ftpType     = arguments.getArgument("ftpType");
    }
    ftpUser       = arguments.getArgument("ftpUser");
    if (arguments.hasArgument("localSite")) {
      localSite   = arguments.getArgument("localSite");
    }
    if (ftpSync) {
      if (!arguments.hasArgument("localSite")) {
        System.out.println("- localSite verplicht bij ftpSync=true.");
        return;
      }
    }

    try {
      ftp.connect(ftpHost);
      if ("pas".equalsIgnoreCase(ftpType)) {
        ftp.enterLocalPassiveMode();
      }
      ftp.login(ftpUser, ftpPasswd);
      ftp.setFileType(FTPClient.BINARY_FILE_TYPE);

      if (ftpOverview) {
        showFTPDirectoryContent(ftpHome);
        System.out.println();
      }

      if (ftpSync) {
        ftp.changeWorkingDirectory(ftpHome);
        synchroniseDirectory(localSite);
      }

      ftp.logout();
      ftp.disconnect();
    } catch (Exception e) {
      System.err.println(e.getLocalizedMessage());
    }
    System.out.println("Klaar.");
  }

  /**
   * Geeft de 'help' pagina.
   */
  protected static void help() {
    System.out.println("java -jar SiteManager.jar SynchroniseSite [OPTIE...]");
    System.out.println();
    System.out.println("  --force          [true|FALSE] Altijd kopiëren naar de website.");
    System.out.println("  --ftpHome                     De HOME directory van de website.");
    System.out.println("  --ftpHost                     De URL van de host van de website.");
    System.out.println("  --ftpOverview    [true|FALSE] Bestandslijst van de website.");
    System.out.println("  --ftpPasswd                   Password van de user voor het onderhoud van de website.");
    System.out.println("  --ftpSync        [true|FALSE] De website synchroniseren?");
    System.out.println("  --ftpType        [ACT|pas]    ACTive or PASsive FTP.");
    System.out.println("  --ftpUser                     De user voor het onderhoud van de website.");
    System.out.println("  --localSite                   De directory van de 'lokale' website.");
    System.out.println();
    System.out.println("Alle parameters behalve localSite zijn verplicht.");
    System.out.println("De parameter localSite is enkel verplicht bij ftpSync TRUE.");
    System.out.println();
  }

  /**
   * Geeft de inhoud van een directory.
   * 
   * @param directory
   * @return
   */
  private static void showFTPDirectoryContent(String directory) {
    try {
      ftp.changeWorkingDirectory(directory);
      String  pwd = ftp.printWorkingDirectory();
      FTPFile[] content = ftp.listFiles();
      if (content != null) {
        for (int i = 0; i < content.length; i++) {
          System.out.println(pwd + "/" + content[i].getName());
          if (content[i].isDirectory()) {
            showFTPDirectoryContent(content[i].getName());
          }
        }
      }
      ftp.changeWorkingDirectory("..");
    } catch (IOException e) {
      System.out.println(e.getLocalizedMessage());
    }
  }
  /**
   * Synchronise the WebSite with the local copy.
   * 
   * @param directory
   * @throws IOException
   */
  private static void synchroniseDirectory(String directory)
      throws IOException {
    String  ftpDir  =
      directory.substring(directory.lastIndexOf(File.separator)+1);
    if (localSite.equals(directory)) {
      ftpDir  = "";
    }
    ftp.changeWorkingDirectory(ftpDir);
    System.out.println("FTP Directory: " + ftp.printWorkingDirectory());
    FTPFile[] ftpContent  = ftp.listFiles();
    String[]  locContent  = new File(directory).list();
    TreeMap<String, FTPFile> 
              ftpFiles    = new TreeMap<String, FTPFile>();
    TreeMap<String, String>
              locFiles    = new TreeMap<String, String>();

    for (int i = 0; i < ftpContent.length; i++) {
      ftpFiles.put(ftpContent[i].getName(), ftpContent[i]);
    }
    for (int i = 0; i < locContent.length; i++) {
      locFiles.put(locContent[i], locContent[i]);
    }

    Iterator<Entry<String, FTPFile>> ftpIter = ftpFiles.entrySet().iterator();
    Iterator<Entry<String, String>>  locIter = locFiles.entrySet().iterator();
    String            ftpFile = "{";
    FTPFile           ftpItem = null;
    String            locFile = "{";
    File              locItem = null;
    if (ftpIter.hasNext()) {
      ftpFile = ftpIter.next().getKey();
      ftpItem = ftpFiles.get(ftpFile);
    }
    if (locIter.hasNext()) {
      locFile = locIter.next().getKey();
      locItem = new File(directory + File.separator + locFile);
    }
    while (!"{".equals(ftpFile)
        || !"{".equals(locFile)) {
      // Bestaat aan beide kanten.
      if (ftpFile.equals(locFile)) {
        if (locItem.isDirectory()
            && ftpItem.isDirectory()) {
          synchroniseDirectory(directory + File.separator + locFile);
        } else {
          if (locItem.isFile()
              && ftpItem.isFile()) {
            if (locItem.lastModified() > ftpItem.getTimestamp()
                                                .getTimeInMillis()
                || force) {
              String  datum = "";
              try {
                datum = Datum.fromDate(new Date(locItem.lastModified()),
                                       "dd/MM/yyyy HH:mm:ss.SSS") + " - "
                        + Datum.fromDate(new Date(ftpItem.getTimestamp()
                                                         .getTimeInMillis()),
                                       "dd/MM/yyyy HH:mm:ss.SSS");
              } catch (ParseException e) {
                System.out.println(e.getLocalizedMessage());
              }
              System.out.println("Vervang  : " + ftp.printWorkingDirectory()
                                 + "/" + locFile + " [" +  datum + "]");
              ftp.storeFile(locFile, new FileInputStream(directory
                                                         + File.separator
                                                         + locFile));
            }
          } else {
            System.out.println("Vervang  : " + ftp.printWorkingDirectory()
                               + "/" + locFile);
            if (locItem.isFile()) {
              deleteFTPDirectory(ftpFile);
              ftp.storeFile(locFile, new FileInputStream(directory
                                                         + File.separator
                                                         + locFile));
            } else {
              ftp.deleteFile(ftpFile);
              ftp.makeDirectory(locFile);
              synchroniseDirectory(directory + File.separator + locFile);
            }
          }
        }
        if (ftpIter.hasNext()) {
          ftpFile = ftpIter.next().getKey();
          ftpItem = ftpFiles.get(ftpFile);
        } else {
          ftpFile = "{";
          ftpItem = null;
        }
        if (locIter.hasNext()) {
          locFile = locIter.next().getKey();
          locItem = new File(directory + File.separator + locFile);
        } else {
          locFile = "{";
          locItem = null;
        }
      } else {
        // Enkel op WebSite.
        if (ftpFile.compareTo(locFile) < 0) {
          System.out.println("Verwijder: " + ftp.printWorkingDirectory()
                             + "/" + ftpFile);
          if (ftpItem.isFile()) {
            ftp.deleteFile(ftpFile);
          } else {
            deleteFTPDirectory(ftpFile);
          }
          if (ftpIter.hasNext()) {
            ftpFile = ftpIter.next().getKey();
            ftpItem = ftpFiles.get(ftpFile);
          } else {
            ftpFile = "{";
            ftpItem = null;
          }
        } else {
          // Nog niet op WebSite.
          if (ftpFile.compareTo(locFile) > 0) {
            System.out.println("Kopieer  : " + ftp.printWorkingDirectory()
                               + "/" + locFile);
            if (locItem.isFile()) {
              ftp.storeFile(locFile, new FileInputStream(directory
                                                         + File.separator
                                                         + locFile));
            } else {
              ftp.makeDirectory(locFile);
              synchroniseDirectory(directory + File.separator + locFile);
            }
            if (locIter.hasNext()) {
              locFile = locIter.next().getKey();
              locItem = new File(directory + File.separator + locFile);
            } else {
              locFile = "{";
              locItem = null;
            }
          }
        }
      }
    }
    ftp.changeWorkingDirectory("..");
  }
}
