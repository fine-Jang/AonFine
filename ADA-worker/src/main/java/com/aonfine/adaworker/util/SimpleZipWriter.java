package com.aonfine.adaworker.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class SimpleZipWriter {
    private SimpleZipWriter() { }

    public static void zipDirectory(File sourceDir, File destZip) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(destZip));
             Stream<Path> walk = Files.walk(sourceDir.toPath())) {
            byte[] buf = new byte[64 * 1024];
            for (Path path : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
                String relative = sourceDir.toPath().relativize(path).toString().replace('\\', '/');
                zos.putNextEntry(new ZipEntry(relative));
                try (FileInputStream fis = new FileInputStream(path.toFile())) {
                    int read;
                    while ((read = fis.read(buf)) != -1) zos.write(buf, 0, read);
                }
                zos.closeEntry();
            }
        }
    }
}
