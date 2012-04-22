/**
 * Copyright 2008 Marco de Booij
 *
 * Licensed under the EUPL, Version 1.1 or - as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * you may not use this work except in compliance with the Licence. You may
 * obtain a copy of the Licence at:
 *
 * http://ec.europa.eu/idabc/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */
package eu.debooy.sitemanager;

import eu.debooy.doosutils.Banner;


/**
 * @author Marco de Booij
 */
public class SiteManager {
  private SiteManager() {}

  /**
   * @param args
   */
  public static void main(String[] args) {
    if (args.length == 0) {
      Banner.printBanner("Site Manager");
      help();
      return;
    }
    String    commando      = args[0];

    String[]  commandoArgs  = new String[args.length-1];
    System.arraycopy(args, 1, commandoArgs, 0, args.length-1);

    if ("generaterss".equalsIgnoreCase(commando)) {
      GenerateRss.execute(commandoArgs);
      return;
    }
    if ("generatesite".equalsIgnoreCase(commando)) {
      GenerateSite.execute(commandoArgs);
      return;
    }
    if ("synchronisesite".equalsIgnoreCase(commando)) {
      SynchroniseSite.execute(commandoArgs);
      return;
    }

    Banner.printBanner("Site Manager");
    help();
  }

  /**
   * Geeft de 'help' pagina.
   */
  private static void help() {
    System.out.println("java -jar SiteManager.jar [OPTIE...]");
    System.out.println();
    System.out.println("  GenerateRSS       Genereer een RSS feed.");
    System.out.println("  GenerateSite      Genereer de Website in een lokale directory.");
    System.out.println("  SynchroniseSite   Synchroniseert de Website met de lokale directory.");
    System.out.println();
    System.out.println("  --force          [true|FALSE] Altijd kopiëren naar de website.");
    System.out.println("  --ftpHome                     De HOME directory van de website.");
    System.out.println("  --ftpHost                     De URL van de host van de website.");
    System.out.println("  --ftpOverview    [true|FALSE] Bestandslijst van de website.");
    System.out.println("  --ftpPasswd                   Password van de user voor het onderhoud van de website.");
    System.out.println("  --ftpSync        [true|FALSE] De website synchroniseren?");
    System.out.println("  --ftpType        [ACT|pas]    ACTive or PASsive FTP.");
    System.out.println("  --ftpUser                     De user voor het onderhoud van de website.");
    System.out.println("  --localOverview  [true|FALSE] Bestandslijst van de 'lokale' website?");
    System.out.println("  --localSite                   De directory van de 'lokale' website.");
    System.out.println("  --siteURL                     De 'home' URL van de website (zonder http://).");
    System.out.println("  --sourceGenerate [true|FALSE] De 'lokale' website genereren?");
    System.out.println("  --sourceIncludes              De directory van de 'includes'.");
    System.out.println("  --sourcePages                 De directory van de 'source' paginas.");
    System.out.println();
    GenerateRss.help();
    System.out.println();
    GenerateSite.help();
    System.out.println();
    SynchroniseSite.help();
    System.out.println();
  }
}
