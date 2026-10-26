package dev.casperrs.cookieCore;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Properties;

/**
 * Adds the JDBC drivers to the plugin's classpath, resolved through Paper's Maven Central mirror (Maven Central
 * directly risks rate limits). Runs before the plugin class exists, so no plugin API here.
 */
public final class CookieCoreLoader implements PluginLoader {

    /** Filtered from the pom's driver version properties, so the versions cannot drift. */
    static final String LIBRARIES = "cookiecore-libraries.properties";

    @Override
    public void classloader(PluginClasspathBuilder classpathBuilder) {
        MavenLibraryResolver resolver = new MavenLibraryResolver();
        resolver.addRepository(new RemoteRepository.Builder(
                "central", "default", MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR).build());
        for (String coordinates : libraries()) {
            resolver.addDependency(new Dependency(new DefaultArtifact(coordinates), null));
        }
        classpathBuilder.addLibrary(resolver);
    }

    /** The {@code group:artifact:version} coordinates from {@link #LIBRARIES}, sorted by key. */
    static List<String> libraries() {
        Properties props = new Properties();
        try (InputStream in = CookieCoreLoader.class.getClassLoader().getResourceAsStream(LIBRARIES)) {
            if (in == null) {
                throw new IllegalStateException(LIBRARIES + " is missing from the plugin jar");
            }
            props.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + LIBRARIES, e);
        }
        return props.stringPropertyNames().stream().sorted().map(props::getProperty).toList();
    }
}
