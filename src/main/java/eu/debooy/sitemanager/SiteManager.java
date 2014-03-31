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
import eu.debooy.doosutils.DoosUtils;

import java.util.Locale;
import java.util.ResourceBundle;


/**
 * @author Marco de Booij
 */
public final class SiteManager {
  private static  ResourceBundle  resourceBundle  =
      ResourceBundle.getBundle("ApplicatieResources", Locale.getDefault());

  private SiteManager() {}

  /**
   * @param args
   */
  public static void main(String[] args) {
    if (args.length == 0) {
      Banner.printBanner(resourceBundle.getString("banner.site.manager"));
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

    Banner.printBanner(resourceBundle.getString("banner.site.manager"));
    help();
  }

  /**
   * Geeft de 'help' pagina.
   */
  private static void help() {
    DoosUtils.naarScherm("  GenerateRSS       ",
                         resourceBundle.getString("help.genereer.rss"), 80);
    DoosUtils.naarScherm("  GenerateSite      ",
                         resourceBundle.getString("help.genereer.site"), 80);
    DoosUtils.naarScherm("  SynchroniseSite   ",
                         resourceBundle.getString("help.synchroniseer.site"),
                         80);
    DoosUtils.naarScherm("");
    GenerateRss.help();
    DoosUtils.naarScherm("");
    GenerateSite.help();
    DoosUtils.naarScherm("");
    SynchroniseSite.help();
  }
}
