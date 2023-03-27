import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.Set;
import java.util.TreeSet;

/**
 * This generates the application.info file.
 */
public class ApplicationInfoTest {
    private final String base = "com/sellerworx/info/commit";

    @Test
    public void emitInfo() {
        emitInformation();
    }

    private static final Logger logger = LoggerFactory.getLogger(ApplicationInfoTest.class);

    /**
     * Emits application information containing last commit log of each module.
     * git log -1
     */
    private void emitInformation() {
        File file = new File(System.getProperty("LOG_PATH", "."), "application.info");
        try {
            String info = getInfo();
            System.out.println(info);
            FileUtils.writeStringToFile(file, info, "UTF-8");
        } catch (Throwable e) {
            logger.warn("Could not generate application information.", e);
        }
    }

    private String getInfo() throws IOException {
        final ClassLoader classLoader = ApplicationInfoTest.class.getClassLoader();
        Enumeration<URL> urls = classLoader.getResources(base);
        Set<String> artifacts = new TreeSet<>();
        while (urls.hasMoreElements()) {
            try {
                URL url = urls.nextElement();
                String infoFile = url.toString();
                String artifact = infoFile.substring(infoFile.indexOf("com/sellerworx/") + 15);
                artifact = artifact.substring(0, artifact.indexOf('/'));
                artifacts.add(artifact);
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            }
        }
        StringBuilder stringBuilder = new StringBuilder();
        for (String artifact : artifacts) {
            stringBuilder.append(getInfo(artifact));
        }
        return stringBuilder.toString();
    }

    private String getInfo(String artifact) {
        InputStream stream = null;
        try {
            URL url = getClass().getClassLoader().getResource(base + "/" + artifact + ".txt");
            if (url != null) {
                stream = url.openStream();
                return IOUtils.toString(stream, "UTF-8");
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        } finally {
            IOUtils.closeQuietly(stream);
        }
        return "";
    }
}
