import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.*
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.bundling.Jar

class ConfigurePublishing implements Plugin<Project> {
	
	void apply(Project project) {

		project.extensions.create("configurePublishing", ConfigurePublishingExtension)

		project.publishing {
			publications {
				maven(MavenPublication) {
					// Make available in closure below
					def publication = it
                    from project.components.java
					
                    project.afterEvaluate {
						// Depend on jar built by bnd
						def jarFile = project.components.java.artifacts.first().file
						def pomTask = project.tasks.generatePomFileForMavenPublication
						def jarTask = project.tasks.jar
						pomTask.dependsOn(jarTask)

						// Continue "configuring" after jar jas been built
						pomTask.doFirst {
							def jarFiles = project.zipTree(jarFile)
							def pomFiles = jarFiles.matching {
								include "META-INF/maven/**/pom.xml" }.files
							if (pomFiles) {
								def top = new XmlParser().parse(pomFiles.first())
								pom.withXml { mergeDependencyInfo(it.asNode(), top) }
							}

						}
					
                        artifactId = project.archivesBaseName
                        artifact(project.tasks.sourcesJar) {
                            classifier = 'sources'
                        }
                        artifact(project.tasks.javadocJar) {
                            classifier = 'javadoc'
                        }
                        pom.packaging = "jar"
					
                        // Until https://github.com/gradle/gradle/issues/1232 is fixed:
                        pom.withXml {
                            // Generate map of resolved versions
                            Map resolvedVersionMap = [:]
                            Set<ResolvedArtifact> resolvedArtifacts = project.configurations.compile.getResolvedConfiguration().getResolvedArtifacts()
                            resolvedArtifacts.each {
                                ModuleVersionIdentifier mvi = it.getModuleVersion().getId();
                                resolvedVersionMap.put("${mvi.getGroup()}:${mvi.getName()}", mvi.getVersion())
                            }
                            Set<ResolvedArtifact> testResolved = project.configurations.testCompile.getResolvedConfiguration().getResolvedArtifacts()
                            testResolved.each {
                                ModuleVersionIdentifier mvi = it.getModuleVersion().getId();
                                resolvedVersionMap.put("${mvi.getGroup()}:${mvi.getName()}", mvi.getVersion())
                            }

                            // Update dependencies with resolved versions
                            if (asNode().dependencies) {
                                asNode().dependencies.first().each {
                                    def groupId = it.get("groupId").first().value().first()
                                    def artifactId = it.get("artifactId").first().value().first()
                                    def version = it.get("version").first().value()[0];
                                    // Leave Maven version ranges alone.
                                    if (!version.startsWith('(') && !version.startsWith('[')) {
                                        it.get("version").first().value = resolvedVersionMap.get("${groupId}:${artifactId}")
                                    }
                                }
                            }
                        }

                        def projectName = project.name
                        def projectDescription = project.description
                        if (projectDescription == null || projectDescription == "") {
                            projectDescription = "(No description)"
                        }
                        pom.withXml {
                            asNode().with {
                                appendNode('name', projectName)
                                appendNode('description', projectDescription)
                            }
                        }
                        pom.withXml(project.configurePublishing.withPomXml)
                    }
                }
            }
		}
        
        if (project.hasProperty("signing.keyId")) {
            project.signing.sign(project.publishing.publications.maven)
        }

	}

	void mergeDependencyInfo(Node target, Node source) {
		if (!source.dependencies) {
			return;
		}
		if (!target.dependencies) {
			target.appendNode('dependencies')
		}
		source.dependencies.first().each {
			target.dependencies*.append(it)
		}
	}
	
}
