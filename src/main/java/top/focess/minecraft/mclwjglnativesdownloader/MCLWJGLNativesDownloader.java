package top.focess.minecraft.mclwjglnativesdownloader;

import top.focess.minecraft.mclwjglnativesdownloader.platform.Architecture;
import top.focess.minecraft.mclwjglnativesdownloader.platform.Platform;
import top.focess.minecraft.mclwjglnativesdownloader.platform.PlatformResolver;
import top.focess.minecraft.mclwjglnativesdownloader.util.ZipUtil;
import top.focess.util.Pair;
import top.focess.util.json.JSON;
import top.focess.util.json.JSONList;
import top.focess.util.json.JSONObject;
import top.focess.util.option.Option;
import top.focess.util.option.OptionParserClassifier;
import top.focess.util.option.Options;
import top.focess.util.option.type.OptionType;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.util.*;

public class MCLWJGLNativesDownloader {

    private static final String PREFIX_URL = "https://repo1.maven.org/maven2/org/lwjgl/";

    private static final String LWJGL_SOURCE_URL = "https://github.com/LWJGL/lwjgl3/archive/refs/tags/";

    public static void main(String[] args) throws IOException, InterruptedException {
        Platform platform = Platform.parse(System.getProperty("os.name"));
        Architecture arch = Architecture.parse(System.getProperty("os.arch"));
        System.out.println("Platform: " + platform);
        System.out.println("Architecture: " + arch);
        PlatformResolver platformResolver = PlatformResolver.getPlatformResolver(platform, arch);
        Scanner scanner = new Scanner(System.in);
        if (arch != Architecture.ARM64) {
            System.err.println("Architecture of your computer is not ARM64. If this is want you want, you can ignore this error.");
            System.out.println("But if your computer is ARM64, please make sure you are using ARM64 Java.");
            System.err.println("Please enter 'ENTER' key to continue.");
            scanner.next();
        }
        Options options = Options.parse(args, new OptionParserClassifier("path", OptionType.DEFAULT_OPTION_TYPE));
        Option option = options.get("path");
        String path = System.getProperty("user.dir");
        if (option != null)
            path = option.get(OptionType.DEFAULT_OPTION_TYPE);
        File file = new File(path);
        String filename = file.getName() + ".json";
        File jsonFile = new File(file, filename);
        Set<Pair<String, String>> libs = new HashSet<>();
        File parent = new File(file, "build");
        if (jsonFile.exists()) {
            System.out.println("Found json file: " + jsonFile.getAbsolutePath());
            JSONObject json = JSON.parse(Files.readString(jsonFile.toPath()));
            JSONList libraries = json.getList("libraries");
            System.out.println("Start collecting libraries needed to download...");
            for (JSONObject library : libraries) {
                String name = library.get("name");
                String[] arguments = name.split(":");
                String group = arguments[0];
                String type = arguments[1];
                String version = arguments[2];
                if (group.equals("org.lwjgl")) {
                    JSONList rules = library.getList("rules");
                    boolean allowed = true;
                    for (JSONObject object : rules) {
                        JSON rule = (JSON) object;
                        if (rule.get("action").equals("disallow"))
                            if (rule.contains("os")) {
                                JSON os = rule.getJSON("os");
                                if (os.get("name").equals(platform.getNativesName()))
                                    allowed = false;
                            }
                    }
                    if (allowed)
                        libs.add(Pair.of(type, version));
                }
            }
            System.out.println("Collect finished. All libraries needed to download: " + libs.size());
            System.out.println("Start downloading...");
            List<Pair<String, String>> builtLibs = new ArrayList<>();
            for (Pair<String, String> lib : libs) {
                String type = lib.getFirst();
                String version = lib.getSecond();
                String url = PREFIX_URL + type + "/" + version + "/" + type + "-" + version + "-" + platform.getDownloadName(arch) + ".jar";
                System.out.println("Download " + url);
                try {
                    InputStream inputStream = new URL(url).openStream();
                } catch (FileNotFoundException e) {
                    builtLibs.add(lib);
                } catch (Exception e) {
                    e.printStackTrace();
                    System.exit(-1);
                }
            }
            System.out.println("Downloading finished. All libraries downloaded: " + (libs.size() - builtLibs.size()));
            System.out.println("Start building libraries: " + builtLibs.size());
            Set<String> versions = new HashSet<>();
            for (Pair<String, String> lib : builtLibs) {
                String version = lib.getSecond();
                if (versions.contains(version))
                    continue;
                versions.add(version);
                String url = LWJGL_SOURCE_URL + version + ".zip";
                System.out.println("Download built library: " + url);
                if (!new File(parent, "lwjgl3-" + version).exists()) {
                    InputStream inputStream = new URL(url).openStream();
                    try {
                        ZipUtil.unzip(inputStream, parent);
                    } catch (Exception e) {
                        e.printStackTrace();
                        System.exit(-1);
                    }
                }
                File lwjgl3 = new File(parent, "lwjgl3-" + version);
                System.out.println("Replace necessary files...");
                platformResolver.resolvePrebuild(lwjgl3);
                System.out.println("Download necessary files...");
                platformResolver.resolvePredownload(lwjgl3);
                System.out.println("Build library: " + version);
                Process process = new ProcessBuilder("ant","compile-templates").redirectOutput(ProcessBuilder.Redirect.INHERIT).redirectError(ProcessBuilder.Redirect.INHERIT).directory(lwjgl3).start();
                if (process.waitFor() != 0) {
                    System.err.println("Build failed.");
                    System.exit(-1);
                }
                System.out.println("Finish 25%");
                process = new ProcessBuilder("ant","compile-native").redirectOutput(ProcessBuilder.Redirect.INHERIT).redirectError(ProcessBuilder.Redirect.INHERIT).directory(lwjgl3).start();
                if (process.waitFor() != 0) {
                    System.err.println("Build failed.");
                    System.exit(-1);
                }
                System.out.println("Finish 50%");
                platformResolver.resolveDownloadGLFW(parent);
                System.out.println("Finish 60%");
                InputStream inputStream = new URL("https://github.com/jemalloc/jemalloc/archive/refs/heads/master.zip").openStream();
                File jmalloc = new File(parent, "jemalloc-master");
                ZipUtil.unzip(inputStream, parent);
                process = new ProcessBuilder("./configure").redirectOutput(ProcessBuilder.Redirect.INHERIT).redirectError(ProcessBuilder.Redirect.INHERIT).directory(jmalloc).start();
                if (process.waitFor() != 0) {
                    System.err.println("Build failed.");
                    System.exit(-1);
                }
                process = new ProcessBuilder("make").redirectOutput(ProcessBuilder.Redirect.INHERIT).redirectError(ProcessBuilder.Redirect.INHERIT).directory(jmalloc).start();
                if (process.waitFor() != 0) {
                    System.err.println("Build failed.");
                    System.exit(-1);
                }
                System.out.println("Finish 70%");
                inputStream = new URL("https://github.com/kcat/openal-soft/archive/refs/heads/master.zip").openStream();
                File openal = new File(lwjgl3, "openal-soft-master");
                ZipUtil.unzip(inputStream, parent);
                process = new ProcessBuilder("cmake", "..").redirectOutput(ProcessBuilder.Redirect.INHERIT).redirectError(ProcessBuilder.Redirect.INHERIT).directory(openal).start();
                if (process.waitFor() != 0) {
                    System.err.println("Build failed.");
                    System.exit(-1);
                }
                process = new ProcessBuilder("make").redirectOutput(ProcessBuilder.Redirect.INHERIT).redirectError(ProcessBuilder.Redirect.INHERIT).directory(openal).start();
                if (process.waitFor() != 0) {
                    System.err.println("Build failed.");
                    System.exit(-1);
                }
                System.out.println("Finish 80%");
                platformResolver.resolveBridge(parent);
                System.out.println("Finish 90%");
                platformResolver.resolveMove(parent);
                System.out.println("Finish 100%");
            }
        } else {
            System.out.println("Can't find json file: " + jsonFile.getAbsolutePath());
        }
    }
}
