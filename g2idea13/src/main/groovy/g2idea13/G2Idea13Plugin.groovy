package g2idea13

import org.gradle.BuildResult
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.XmlProvider
import org.gradle.plugins.ide.idea.IdeaPlugin


class G2Idea13Plugin implements Plugin<Project>{
  void apply(Project project){
    project.logger.debug "g2idea13 apply called from $project"

    project.plugins.withType(IdeaPlugin){ IdeaPlugin ideaPlugin ->
      project.logger.debug "idea plugin applied to project: $ideaPlugin"

      G2Idea13ProjectConvention projectConv = project.idea.extensions.create(
        "g2idea13", G2Idea13ProjectConvention)
      projectConv.ideaPlugin = ideaPlugin
      projectConv.project = project
      project.logger.debug "g2idea13 project set to : $project"
    }

    project.afterEvaluate{ Project delegateProject ->
      G2Idea13ProjectConvention projectConv =
        delegateProject.idea.extensions.g2idea13
      G2Idea13ProjectConvention rootProjectConv =
        delegateProject.rootProject.idea.extensions.g2idea13

      if( rootProjectConv.baseDirectory ){
        if( !delegateProject.parent && projectConv.baseProjectName ){
          projectConv.ideaPlugin.model.module.outputFile = new File(
            rootProjectConv.baseDirectoryFile,
            "${projectConv.baseProjectName}.iml" )
        }
        else {
          // subprojects should be named after their gradle project
          // (probably just dir name anyway)
          projectConv.ideaPlugin.model.module.outputFile = new File(
            rootProjectConv.baseDirectoryFile,
            "${projectConv.project.name}.iml" )

        }
      }

      if( !delegateProject.parent ){
        configureRootProjectSpecificStuff(projectConv)
      }

      projectConv.ideaPlugin.model.module?.iml?.withXml{ provider ->
        provider.node.component.content.sourceFolder.each{
          if( projectConv.resourcePatterns &&
            it.@url =~ projectConv.resourcePatterns.join("|")
          ){
            it.@type = "java-resource"
          }
          if( projectConv.testResourcePatterns &&
            it.@url =~ projectConv.testResourcePatterns.join("|")
          ){
            it.@type = "java-test-resource"
      } } }


      if( projectConv.finalMessage ){
        projectConv.project.gradle.buildFinished{ BuildResult buildResult ->
          if( !buildResult.failure ){
            println projectConv.finalMessage
      } } }
    }
  }

  private void configureRootProjectSpecificStuff(
    G2Idea13ProjectConvention projectConv)
  {
    String baseName = projectConv.baseProjectName ?: projectConv.project.name
    def baseDirFile = projectConv.baseDirectoryFile
    projectConv.ideaPlugin.model.project.outputFile = new File(
      baseDirFile, "${baseName}.ipr")
    projectConv.project.tasks.ideaWorkspace.outputFile = new File(
      baseDirFile, "${baseName}.iws")

    projectConv.ideaPlugin.model.project?.ipr?.withXml{ XmlProvider provider ->
      if( projectConv.compilerConfiguration ){
        provider.node.component.find{ it.@name == 'CompilerConfiguration' }.
          option.@value = projectConv.compilerConfiguration
      }

      if( projectConv.vcs ){
        provider.node.component.find{ it.@name == 'VcsDirectoryMappings' }.
          mapping.@vcs = projectConv.vcs
      }

      if( projectConv.vcsDirectory ){
        provider.node.component.find{ it.@name == 'VcsDirectoryMappings' }.
          mapping.@directory = projectConv.vcsDirectory
      }

      if( projectConv.disableSpelling ){
        disableSpelling(provider)
      }

      provider.node.component.find{ it.@name == 'Encoding' }.
        @native2AsciiForPropertiesFiles = projectConv.useNative2AsciiProperties
    }

    projectConv.ideaPlugin.model.workspace?.iws?.withXml{ provider ->
      if( projectConv.compilerHeapSize ){
        provider.node.appendNode(
          "component",
          [name: "CompilerWorkspaceConfiguration"]
        ).appendNode(
          "option",
          [name : "COMPILER_PROCESS_HEAP_SIZE",
           value: projectConv.getCompilerHeapSize()])
      }
    }
  }

  private void disableSpelling(XmlProvider provider){
    def inspectionProfiles = provider.node.component.find{
      it.@name == 'InspectionProjectProfileManager'
    }

    if( inspectionProfiles ){
      inspectionProfiles.profiles.profile.inspection.inspection_tool.find{
        it.@class == 'SpellCheckingInspection'
      }.enabled = 'false'
    }
    else {
      def builder = new NodeBuilder()
      def profiles = builder.profiles{
        profile(version: '1.0', is_locked: false){
          option(name: 'myName', value: 'Project Default')
          option(name: 'myLocal', value: false)
          inspection_tool(
            class: 'SpellCheckingInspection',
            enabled: false,
            level: 'TYPO',
            enabled_by_default: false) {
              option(name: 'processCode', value: false)
              option(name: 'processLiterals', value: false)
              option(name: 'processComments', value: false)
            }
        }
      }


      def component = provider.node.appendNode(
        "component", [name: "InspectionProjectProfileManager"])
      component.append(profiles)
      component.appendNode 'option',
        [name: 'PROJECT_PROFILE', value: 'Project Default']
      component.appendNode 'option', [name: 'USE_PROJECT_PROFILE', value: true]
      component.appendNode 'version', [value: "1.0"]
    }
  }


}


class G2Idea13ProjectConvention{
  Project project
  IdeaPlugin ideaPlugin

  String baseDirectory
  String baseProjectName
  List<String> resourcePatterns = [".*/src/main/resources"]
  List<String> testResourcePatterns = [".*/src/test/resources"]

  String disableSpelling = true

  String compilerConfiguration
  String vcs
  String vcsDirectory
  String compilerHeapSize

  boolean useNative2AsciiProperties = true

  String finalMessage


  File getBaseDirectoryFile(){
    return project.file(baseDirectory)
  }
}
