/*
 * Copyright (c) 2018-2019 covers1624
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

package net.covers1624.wt.forge.remap;

import net.covers1624.wt.util.Utils;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.jar.Manifest;

/**
 * A simple Jar remapper.
 * Strips signing information, uses the provided Remapper.
 * Created by covers1624 on 10/01/19.
 */
public class JarRemapper {

    private final Remapper remapper;

    public JarRemapper(Remapper remapper) {
        this.remapper = remapper;
    }

    public void process(Path input, Path output) {
        if (Files.notExists(output)) {
            if (Files.notExists(output.getParent())) {
                Utils.sneaky(() -> Files.createDirectories(output.getParent()));
            }
        }
        try (FileSystem inFs = Utils.getJarFileSystem(input, true);//
             FileSystem outFs = Utils.getJarFileSystem(output, true)) {
            Path inRoot = inFs.getPath("/");
            Path outRoot = outFs.getPath("/");
            Files.walkFileTree(inRoot, new Visitor(inRoot, outRoot, remapper));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static class Visitor extends SimpleFileVisitor<Path> {

        private final Path inRoot;
        private final Path outRoot;
        private final Remapper remapper;

        private Visitor(Path inRoot, Path outRoot, Remapper remapper) {
            this.inRoot = inRoot;
            this.outRoot = outRoot;
            this.remapper = remapper;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
            Path outDir = outRoot.resolve(inRoot.relativize(dir).toString());
            if (Files.notExists(outDir)) {
                Files.createDirectories(outDir);
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path inFile, BasicFileAttributes attrs) throws IOException {
            Path outFile = outRoot.resolve(inRoot.relativize(inFile).toString());
            if (!outFile.endsWith(".SF") && !outFile.endsWith(".DSA") && !outFile.endsWith(".RSA")) {
                if (outFile.toString().endsWith("META-INF/MANIFEST.MF")) {
                    try (InputStream is = Files.newInputStream(inFile);//
                         OutputStream os = Files.newOutputStream(outFile, StandardOpenOption.CREATE)) {
                        Manifest manifest = new Manifest(is);
                        manifest.getEntries().clear();
                        manifest.write(os);
                        os.flush();
                    }
                } else if (outFile.toString().endsWith(".class")) {
                    try (InputStream is = Files.newInputStream(inFile);//
                         OutputStream os = Files.newOutputStream(outFile, StandardOpenOption.CREATE)) {
                        ClassReader reader = new ClassReader(is);
                        ClassWriter writer = new ClassWriter(0);
                        ClassRemapper remapper = new ClassRemapper(writer, this.remapper);
                        reader.accept(remapper, 0);
                        os.write(writer.toByteArray());
                        os.flush();
                    }
                } else {
                    Files.copy(inFile, outFile);
                }
            }

            return FileVisitResult.CONTINUE;
        }
    }

    //    public void process(File input, File output) {
    //        try (JarFile inputJar = new JarFile(input, false)) {
    //            List<Pair<JarEntry, byte[]>> toProcess = inputJar//
    //                    .stream()//
    //                    .filter(e -> !shouldStrip(e.getName()))//
    //                    .map(e -> Pair.of(e, Utils.doLoudly(() -> Utils.toBytes(inputJar.getInputStream(e)))))//
    //                    .collect(Collectors.toList());
    //            List<Pair<JarEntry, byte[]>> processed = toProcess.parallelStream()//
    //                    .map(e -> {
    //                        String name = e.getLeft().getName();
    //                        if (name.equals("META-INF/MANIFEST.MF")) {
    //                            Manifest mf = Utils.doLoudly(() -> new Manifest(Utils.toInputStream(e.getRight())));
    //                            mf.getEntries().clear();
    //                            ByteArrayOutputStream out = new ByteArrayOutputStream();
    //                            Utils.doLoudly(() -> mf.write(out));
    //                            return Pair.of(new JarEntry(name), out.toByteArray());
    //                        } else if (!e.getLeft().isDirectory() && name.endsWith(".class")) {
    //                            ClassReader reader = new ClassReader(e.getRight());
    //                            ClassWriter writer = new ClassWriter(0);
    //                            ClassRemapper remapper = new ClassRemapper(writer, this.remapper);
    //                            reader.accept(remapper, 0);
    //                            return Pair.of(new JarEntry(name), writer.toByteArray());
    //                        } else {
    //                            return Pair.of(new JarEntry(e.getLeft()), e.getRight());
    //                        }
    //                    })//
    //                    .collect(Collectors.toList());
    //            try (JarOutputStream jout = new JarOutputStream(new FileOutputStream(Utils.makeFile(output)))) {
    //                for (Pair<JarEntry, byte[]> pair : processed) {
    //                    jout.putNextEntry(pair.getLeft());
    //                    jout.write(pair.getRight());
    //                    jout.closeEntry();
    //                }
    //            }
    //        } catch (IOException e) {
    //            e.printStackTrace();
    //        }
    //    }
    //
    //    private static boolean shouldStrip(String name) {
    //        return name.endsWith(".SF") || name.endsWith(".DSA") || name.endsWith(".RSA");
    //    }
}
