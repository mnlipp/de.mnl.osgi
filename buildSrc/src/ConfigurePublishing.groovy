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
					from project.components.java

					// Make available in closure below
					def publication = it
					
					project.afterEvaluate {
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
							Set<ResolvedArtifact> resolvedArtifacts = project.configurations.compileClasspath.getResolvedConfiguration().getResolvedArtifacts()
							resolvedArtifacts.each {
								ModuleVersionIdentifier mvi = it.getModuleVersion().getId();
								resolvedVersionMap.put("${mvi.getGroup()}:${mvi.getName()}", mvi.getVersion())
							}
							Set<ResolvedArtifact> testResolved = project.configurations.testCompileClasspath.getResolvedConfiguration().getResolvedArtifacts()
							testResolved.each {
								ModuleVersionIdentifier mvi = it.getModuleVersion().getId();
								resolvedVersionMap.put("${mvi.getGroup()}:${mvi.getName()}", mvi.getVersion())
							}

							// Update dependencies with resolved versions
							if (asNode().dependencies) {
								asNode().dependencies.first().each {
									def groupId = it.get("groupId").first().text()
									def artifactId = it.get("artifactId").first().text()
									def version = it.get("version").first().text();
									// Leave Maven version ranges alone.
									if (!version.startsWith('(') && !version.startsWith('[')) {
										it.get("version").first().setValue(resolvedVersionMap.get("${groupId}:${artifactId}"))
									}
								}
							}
						}

						// Add readily available information
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

						// Add dependency information						
						pom.withXml {
							addDependencyInformation(project, asNode())
						}
						
						// Add user (configuration) supplied information
						pom.withXml(project.configurePublishing.withPomXml)
					}
				}
			}
		}

		if (project.hasProperty("signing.keyId")) {
			project.signing.sign(project.publishing.publications.maven)
		}

	}

	void addDependencyInformation(project, pomRoot) {
		// Already known dependencies
		def knownDependencies = collectKnownDependencies(pomRoot)
		// Extract maven coordinates from all jars in the compile classpath...
		project.configurations.compileClasspath.each {
			def jarFile = it
			def jarFiles = null
			try {
				jarFiles = project.zipTree(jarFile)
			} catch(e) {
			}
			if (!jarFiles) {
				return
			}
			def pomPropsFiles = jarFiles.matching {
				include "META-INF/maven/**/pom.properties"
			}.files
			if (pomPropsFiles.empty) {
				return
			}
			def pomProps = new Properties();
			new FileInputStream(pomPropsFiles.first()).withCloseable {
				input -> pomProps.load(input)
			}
			// Found coordinates in jar.
			def newDepKey = "${pomProps.groupId}:${pomProps.artifactId}:${pomProps.version}"
			if (!knownDependencies.contains(newDepKey)) {
				mergeDependency(pomRoot, pomProps)
				knownDependencies.add(newDepKey)
			}
		}
	}
	
	/**
	 * Collect already known dependencies from POM.
	 */
	Set collectKnownDependencies(pomRoot) {
		Set result = new HashSet()
		if (!pomRoot.dependencies || pomRoot.dependencies.empty) {
			return result
		}
		def dependencies = pomRoot.dependencies.first()
		dependencies.children().each {
			result.add("${it.groupId.text()}:${it.artifactId.text()}:${it.version.text()}")
		}
		return result
	}
	
	void mergeDependency(pomRoot, props) {
		if (!pomRoot.dependencies) {
			pomRoot.appendNode('dependencies')
		}
		def dependencies = pomRoot.dependencies.first()
		def dependency = dependencies.appendNode('dependency')
		dependency.appendNode('groupId').setValue(props.groupId)
		dependency.appendNode('artifactId').setValue(props.artifactId)
		dependency.appendNode('version').setValue(props.version)
		dependency.appendNode('scope').setValue('compile')
	}

}
