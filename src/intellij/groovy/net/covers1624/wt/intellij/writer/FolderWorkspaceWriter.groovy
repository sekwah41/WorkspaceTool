package net.covers1624.wt.intellij.writer

import net.covers1624.wt.api.WorkspaceToolContext
import net.covers1624.wt.api.dependency.LibraryDependency
import net.covers1624.wt.api.dependency.MavenDependency
import net.covers1624.wt.api.dependency.WorkspaceModuleDependency
import net.covers1624.wt.api.workspace.WorkspaceWriter
import net.covers1624.wt.intellij.api.script.Intellij
import net.covers1624.wt.intellij.api.script.IntellijRunConfig
import net.covers1624.wt.intellij.api.workspace.IJWorkspaceModule
import net.covers1624.wt.intellij.model.*
import net.covers1624.wt.intellij.util.ContentRootMerger
import org.apache.commons.lang3.StringUtils

/**
 * Created by covers1624 on 14/8/19.
 */
class FolderWorkspaceWriter implements WorkspaceWriter<Intellij> {

    private final WorkspaceToolContext context

    FolderWorkspaceWriter(WorkspaceToolContext context) {
        this.context = context
    }

    @Override
    void write(Intellij frameworkImpl) {
        def dotIdea = context.projectDir.resolve(".idea")
        def outFolder = context.projectDir.resolve("out")

        def misc = dotIdea.resolve("misc.xml")
        def miscNode = misc.exists ? misc.parseXml() : new Node(null, 'project', ['version', '4'])
        def prm = miscNode.children().find() { it instanceof Node && it.get("@name") == 'ProjectRootManager' }
        if (prm != null) {
            miscNode.remove(prm)
        }
        //TODO, script hooks to change VM target and JDK name.
        def prmNew = miscNode.appendNode('component', [name: 'ProjectRootManager', version: '2', languageLevel: 'JDK_1_8', 'project-jdk-name': '1.8', 'project-jdk-type': 'JavaSDK'])
        prmNew.appendNode('output', ['url': outFolder.fileURL])
        misc.write(miscNode)

        IJLibraryTable libTable = new IJLibraryTable()
        context.dependencyLibrary.dependencies.values().each {
            def dep = it.mavenDependency //Prep for LibraryDependency supporting all Dependencies.
            IJLibrary lib
            if (dep instanceof MavenDependency) {
                lib = new IJMavenLibrary()
                if (dep.classes != null) {
                    lib.classes << dep.classes
                }
                if (dep.javadoc != null) {
                    lib.javadoc << dep.javadoc
                }
                if (dep.sources != null) {
                    lib.sources << dep.sources
                }
            }
            lib.libraryName = it.libraryName
            libTable.libraries.put(it.libraryFileName, lib)
        }
        if (context.scalaSdk.scalac != null) {
            IJScalaLibrary scalaLib = new IJScalaLibrary()
            scalaLib.libraryName = context.scalaSdk.sdkName
            scalaLib.languageLevel = context.scalaSdk.name()
            scalaLib.attributes << [type: 'Scala']
            context.scalaSdk.classpath.each {
                if (it.classes != null) {
                    scalaLib.classpath << it.classes
                }
            }
            context.scalaSdk.libraries.each {
                if (it.classes != null) {
                    scalaLib.classes << it.classes
                }
                if (it.javadoc != null) {
                    scalaLib.javadoc << it.javadoc
                }
                if (it.sources != null) {
                    scalaLib.sources << it.sources
                }
            }

            libTable.libraries.put(context.scalaSdk.sdkName.replaceAll("[.-]", "_"), scalaLib)
        }
        libTable.write(dotIdea.resolve("libraries"))

        def ijModules = new IJModules()
        def modulesFolder = dotIdea.resolve("modules")


        List<IJModule> modules = []
        def mergeResult = ContentRootMerger.mergeContentRoots(context.workspaceModules)
        ((List<IJWorkspaceModule>) context.workspaceModules).each { module ->
            def ijModule = new IJModule()
            modules << ijModule
            ijModule.name = module.name
            ijModule.output = module.output
            ijModule.isTest = StringUtils.equals(module.sourceSetName, 'test')
            if (ijModule.isTest) {
                def prodMod = module.parent.children.find { it.name == 'main' }
                if (prodMod != null) {
                    ijModule.productionModule = prodMod.moduleName
                }
            }
            def modulePath = modulesFolder.resolve(ijModule.name + ".iml")
            ijModules.modules[modulePath] = null

            def contentRoots = mergeResult.row(module.name)
            contentRoots.each {
                def cr = it.key
                def paths = it.value
                ijModule.content << new IJModuleContent().with {
                    contentRoot = cr
                    paths.typeMap.each {
                        def attribs = [:]
                        if (it.key == 'resources') {
                            attribs['type'] = ijModule.isTest ? 'java-test-resource' : 'java-resource'
                        } else {
                            attribs['isTestSource'] = ijModule.isTest ? 'true' : 'false'
                        }
                        it.value.each {
                            sources[it] = attribs
                        }
                    }
                    it
                }
            }
            //Always add module path as a content root. (Group modules)
            if (contentRoots.isEmpty()) {
                ijModule.content << new IJModuleContent().with {
                    contentRoot = module.path
                    it
                }
            }
            ijModule.entries << new IJOrderEntry().with {
                it.attributes['type'] = 'inheritedJdk'
                it
            }
            ijModule.entries << new IJOrderEntry().with {
                it.attributes['type'] = 'sourceFolder'
                it.attributes['forTests'] = 'false'
                it
            }
            module.dependencies.each {
                def scope = it.key
                def deps = it.value
                deps.each { dep_ ->
                    ijModule.entries << new IJOrderEntry().with {
                        attributes['scope'] = scope.name()
                        if (dep_.export) {
                            attributes['exported'] = ''
                        }
                        if (dep_ instanceof WorkspaceModuleDependency) {
                            def dep = dep_ as WorkspaceModuleDependency
                            attributes['type'] = 'module'
                            attributes['module-name'] = dep.module.name

                        } else if (dep_ instanceof LibraryDependency) {
                            def dep = dep_ as LibraryDependency
                            attributes['type'] = 'library'
                            attributes['name'] = dep.libraryName
                            attributes['level'] = 'project'
                        } else {
                            throw new RuntimeException("Unhandled type: " + dep_.class)
                        }
                        it
                    }
                }
            }
            ijModule.write(modulePath)
        }
        ijModules.write(dotIdea)

        def runConfigsFolder = dotIdea.resolve("runConfigurations")
        List<IJRunConfig> runConfigs = []
        frameworkImpl.runConfigContainer.runConfigs.each {
            def cName = it.key
            def config = it.value as IntellijRunConfig
            runConfigs << new IJRunConfig().with {
                name = cName
                mainClass = config.mainClass
                classpathModule = config.classpathModule
                progArgs = config.progArgs
                vmArgs = config.vmArgs
                sysProps = config.sysProps
                envVars = config.envVars
                runDir = config.runDir
                it
            }
        }
        runConfigs.each { it.write(runConfigsFolder) }
    }
}