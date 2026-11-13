Block ESP - Fabric client mod for Minecraft Java 1.21.11

GET THE JAR WITHOUT INSTALLING ANYTHING (GitHub builds it for you)
  1. Make a free GitHub account and create a new empty repository.
  2. Upload everything in this folder to it (drag and drop in the browser works,
     make sure the hidden .github folder is included).
  3. Open the repository's Actions tab. The "Build mod jar" run starts by itself.
  4. When it turns green, open the run and download "blockesp-jar" at the bottom.
     Unzip it to get blockesp-1.0.0.jar.

USE IN MODRINTH APP
  Your instance must be Fabric 1.21.11 with Fabric API installed (add "Fabric API" from Modrinth).
  Open the instance > Content > Add content > Add from file > pick blockesp-1.0.0.jar.

IN GAME
  F6 opens/closes the menu (rebindable under Controls > Block ESP).
  Settings are saved in config/blockesp.json.

LOCAL BUILD (optional): install JDK 21 and Gradle, then run: gradle build
